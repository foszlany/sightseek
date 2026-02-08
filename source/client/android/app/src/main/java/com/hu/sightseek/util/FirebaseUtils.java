package com.hu.sightseek.util;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.WriteBatch;
import com.hu.sightseek.model.Activity;
import com.hu.sightseek.model.FirestoreActivity;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Utilities to update cells in Firebase */
public final class FirebaseUtils {
    /** Private constructor */
    private FirebaseUtils() {}

    /**
     * Uploads an activity for a user
     * @param activity Activity to upload
     */
    public static void uploadActivity(Activity activity) {
        if(activity == null) {
            return;
        }

        FirebaseFirestore db = FirebaseFirestore.getInstance();

        FirestoreActivity firestoreActivity = new FirestoreActivity(activity.getId(), activity.getUid(), activity.getName(), activity.getCategory(), activity.getPolyline(), activity.getStartTime(), activity.getElapsedTime(), activity.getDistance(), activity.getStravaId(), activity.getVectorizedData());

        db.collection("users")
                .document(activity.getUid())
                .collection("activities")
                .document(String.valueOf(firestoreActivity.getId()))
                .set(firestoreActivity);
    }

    /**
     * Uploads a list of activities for a user
     * @param activities List of Activities to upload
     */
    public static void uploadActivities(List<Activity> activities) {
        if(activities == null || activities.isEmpty()) {
            return;
        }

        FirebaseFirestore db = FirebaseFirestore.getInstance();
        WriteBatch batch = db.batch();

        for(Activity activity : activities) {
            FirestoreActivity firestoreActivity = new FirestoreActivity(activity.getId(), activity.getUid(), activity.getName(), activity.getCategory(), activity.getPolyline(), activity.getStartTime(), activity.getElapsedTime(), activity.getDistance(), activity.getStravaId(), activity.getVectorizedData());

            DocumentReference docRef = db.collection("users")
                    .document(activity.getUid())
                    .collection("activities")
                    .document(String.valueOf(activity.getId()));

            batch.set(docRef, firestoreActivity);
        }

        batch.commit();
    }


    /**
     * Updates celldata for a user
     * @param cells Map of cellindexes and their respective counts
     * @param isRemoval Whether to subtract cellvalues
     */
    public static void updateCells(Map<String, Integer> cells, boolean isRemoval) {
        FirebaseAuth auth = FirebaseAuth.getInstance();
        String uid = auth.getUid();
        if(uid == null || cells == null || cells.isEmpty()) {
            return;
        }

        FirebaseFirestore db = FirebaseFirestore.getInstance();
        DocumentReference userDocRef = db.collection("users").document(uid);

        if(!isRemoval) {
            HashMap<String, Object> updates = new HashMap<>();
            for(HashMap.Entry<String, Integer> entry : cells.entrySet()) {
                updates.put("visitedCells." + entry.getKey(), FieldValue.increment(entry.getValue()));
            }

            userDocRef.update(updates).addOnCompleteListener(task -> {
                if(task.isSuccessful()) {
                    updateCellLeaderboard(uid);
                }
            });

        }
        else {
            userDocRef.get().addOnSuccessListener(snapshot -> {
                if(!snapshot.exists()) {
                    return;
                }

                HashMap<String, Object> visitedCells = (HashMap<String, Object>) snapshot.get("visitedCells");
                if(visitedCells == null) {
                    return;
                }

                HashMap<String, Object> updates = new HashMap<>();
                for(HashMap.Entry<String, Integer> entry : cells.entrySet()) {
                    String key = entry.getKey();
                    Object value = visitedCells.get(key);

                    long current = (value instanceof Long) ? (Long) value : (value instanceof Integer) ? (Integer) value : 0;
                    long newValue = Math.max(0, current - entry.getValue());

                    if(newValue == 0) {
                        updates.put("visitedCells." + key, FieldValue.delete());
                    }
                    else {
                        updates.put("visitedCells." + key, newValue);
                    }
                }

                userDocRef.update(updates).addOnCompleteListener(task -> {
                    if(task.isSuccessful()) {
                        updateCellLeaderboard(uid);
                    }
                });
            });
        }
    }

    /**
     * Updates cell leaderboard position for a user
     * @param uid UID
     */
    private static void updateCellLeaderboard(String uid) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        DocumentReference userDocRef = db.collection("users").document(uid);

        userDocRef.get().addOnSuccessListener(snapshot -> {
            if(!snapshot.exists()) {
                return;
            }

            Map<String, Object> visitedCells = (Map<String, Object>) snapshot.get("visitedCells");
            String username = snapshot.getString("username");

            int totalVisited = 0;
            if(visitedCells != null) {
                totalVisited = visitedCells.size();
            }

            Map<String, Object> leaderboardEntry = new HashMap<>();
            leaderboardEntry.put("username", username != null ? username : "unknown");
            leaderboardEntry.put("cellsVisited", totalVisited);

            db.collection("leaderboard_cells").document(uid).set(leaderboardEntry);
        });
    }

    /**
     * Updates regional leaderboard for a user
     * @param distanceMap Map of region name and distance to add or remove
     * @param isRemoval Whether to subtract
     */
    public static void updateRegionalLeaderboard(Map<String, Double> distanceMap, boolean isRemoval) {
        if(distanceMap == null || distanceMap.isEmpty()) {
            return;
        }

        FirebaseFirestore db = FirebaseFirestore.getInstance();
        FirebaseAuth auth = FirebaseAuth.getInstance();
        String uid = auth.getUid();

        if(uid == null) {
            return;
        }

        DocumentReference userDocRef = db.collection("users").document(uid);

        userDocRef.get().addOnSuccessListener(snapshot -> {
            if(!snapshot.exists()) {
                return;
            }

            String username = snapshot.getString("username");

            for(Map.Entry<String, Double> entry : distanceMap.entrySet()) {
                String regionPath = entry.getKey();
                double deltaDistance = entry.getValue();

                DocumentReference userRegionDocument = db
                        .collection("leaderboard_regional")
                        .document(regionPath)
                        .collection("users")
                        .document(uid);

                userRegionDocument.get().addOnSuccessListener(docSnapshot -> {
                    double totalDistance;

                    Double oldDistance = docSnapshot.getDouble("distance");
                    if(oldDistance == null) {
                        oldDistance = 0.0;
                    }

                    if(isRemoval) {
                        totalDistance = Math.max(0, oldDistance - deltaDistance);
                    }
                    else {
                        totalDistance = oldDistance + deltaDistance;
                    }

                    Map<String, Object> leaderboardEntry = new HashMap<>();
                    leaderboardEntry.put("username", username);
                    leaderboardEntry.put("distance", totalDistance);

                    userRegionDocument.set(leaderboardEntry);
                });
            }
        });
    }
}