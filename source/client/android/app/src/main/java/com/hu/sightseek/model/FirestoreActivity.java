package com.hu.sightseek.model;

import com.google.firebase.firestore.Blob;
import com.hu.sightseek.enums.TravelCategory;

/** The Activity class that represents a recorded or imported route along with its metadata specialized for Firestore (uses Blob instead of byte[] format for vectorizedData) */
public class FirestoreActivity extends Activity {
    /** Vectorized data as WKB (Well-known binary) format stored in Firestore's Blob format */
    private Blob vectorizedDataBlob;

    /**
     * Constructor
     * @param id ID
     * @param uid User ID, null if not present
     * @param name Name
     * @param category Category
     * @param polyline Encoded polyline
     * @param startTime Start time (Format: YYYY-MM-DDTHH:MM:SS)
     * @param elapsedTime Elapsed time in seconds
     * @param distance Distance in meters
     * @param stravaId Strava ID, -1 if activity is not imported
     * @param vectorizedData Vectorized data as WKB (Well-known binary) format
     */
    public FirestoreActivity(int id, String uid, String name, TravelCategory category, String polyline, String startTime, double elapsedTime, double distance, long stravaId, byte[] vectorizedData) {
        super(id, uid, name, category, polyline, startTime, elapsedTime, distance, stravaId);
        this.vectorizedDataBlob = vectorizedData != null ? Blob.fromBytes(vectorizedData) : null;
    }

    public Blob getVectorizedDataBlob() {
        return vectorizedDataBlob;
    }

    public void setVectorizedDataBlob(Blob vectorizedDataBlob) {
        this.vectorizedDataBlob = vectorizedDataBlob;
    }
}