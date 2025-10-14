package com.hu.sightseek.utils;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public final class SightseekFirebaseUtils {
    private SightseekFirebaseUtils() {}

    public static void updateCellsInFirebase(FirebaseAuth mAuth, HashMap<String, Integer> cells) {
        String uid = mAuth.getUid();
        Map<String, Object> updates = new HashMap<>();
        for(Map.Entry<String, Integer> entry : cells.entrySet()) {
            updates.put("visitedCells." + entry.getKey(), FieldValue.increment(entry.getValue()));
        }

        FirebaseFirestore.getInstance().collection("users")
                .document(uid)
                .update(updates);
    }

    public static void removeCellsFromFirebase(FirebaseAuth mAuth, HashMap<String, Integer> cells) {
        String uid = mAuth.getUid();
        FirebaseFirestore.getInstance().collection("users")
                .document(uid)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    HashMap<String, Long> firestoreMap = (HashMap<String, Long>) documentSnapshot.get("visitedCells");
                    HashMap<String, Object> newMap = new HashMap<>();

                    for(HashMap.Entry<String, Integer> entry : cells.entrySet()) {
                        String key = entry.getKey();
                        long subtractValue = entry.getValue();
                        Long currentValue = firestoreMap.get(key);

                        if(currentValue != null) {
                            long newValue = Math.max(0, currentValue - subtractValue);
                            newMap.put("visitedCells." + key, newValue);
                        }
                    }

                    FirebaseFirestore.getInstance().collection("users")
                            .document(uid)
                            .update(newMap);
                });
    }
}
