package com.hu.sightseek.model;

import com.google.firebase.firestore.Blob;
import com.hu.sightseek.enums.TravelCategory;

/** The Activity class that represents a recorded or imported route along with its metadata specialized for Firestore (uses Blob instead of byte[] format for vectorizedData) */
public class FirestoreActivity extends Activity {
    private Blob vectorizedDataBlob;

    public FirestoreActivity(int id, String uid, String name, TravelCategory category, String polyline, String startTime, double elapsedTime, double distance, long stravaId, Blob vectorizedDataBlob) {
        super(id, uid, name, category, polyline, startTime, elapsedTime, distance, stravaId);
        this.vectorizedDataBlob = vectorizedDataBlob;
    }

    public Blob getVectorizedDataBlob() {
        return vectorizedDataBlob;
    }

    public void setVectorizedDataBlob(Blob vectorizedDataBlob) {
        this.vectorizedDataBlob = vectorizedDataBlob;
    }
}