package com.hu.sightseek.utils;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public final class SightseekFirebaseUtils {
    private SightseekFirebaseUtils() {}

    public static void updateCellsInFirebase(FirebaseAuth mAuth, HashMap<String, Integer> cells, boolean isRemoval) {
        String uid = mAuth.getUid();
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
                    updateCellLeaderboard(db, uid);
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
                        updateCellLeaderboard(db, uid);
                    }
                });
            });
        }
    }

    public static void updateCellLeaderboard(FirebaseFirestore db, String uid) {
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
}
