package com.hu.sightseek.utils;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;

public final class SightseekFirebaseUtils {
    private SightseekFirebaseUtils() {}

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
