package com.hu.sightseek.model;

import androidx.annotation.NonNull;

import com.hu.sightseek.enums.TravelCategory;

import java.util.Arrays;

/** The Activity class that represents a recorded or imported route along with its metadata. */
public class Activity {
    /** ID */
    private final int id;
    /** Name */
    private final String name;
    /** Travel category */
    private final TravelCategory category;
    /** Route as an encoded polyline */
    private final String polyline;
    /** Start time
     * </p>
     * Format: YYYY-MM-DDTHH:MM:SS*/
    private final String startTime;
    /** Elapsed time in seconds */
    private final double elapsedTime;
    /** Distance in meters */
    private final double distance;
    /** Strava ID. -1 if activity is not imported */
    private final long stravaId;
    /** Vectorized data as WKB (Well-known binary) format. */
    private byte[] vectorizedData;

    /** Constructor */
    public Activity(int id, String name, int category, String polyline, String startTime, double elapsedTime, double distance, long stravaId, byte[] vectorizedData) {
        this.id = id;
        this.name = name;
        this.category = TravelCategory.values()[category];
        this.polyline = polyline;
        this.startTime = startTime;
        this.elapsedTime = elapsedTime;
        this.distance = distance;
        this.stravaId = stravaId;
        this.vectorizedData = vectorizedData;
    }

    public int getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public TravelCategory getCategory() {
        return category;
    }

    public String getPolyline() {
        return polyline;
    }

    public String getStartTime() {
        return startTime;
    }

    public double getElapsedTime() {
        return elapsedTime;
    }

    public double getDistance() {
        return distance;
    }

    public long getStravaId() {
        return stravaId;
    }

    public byte[] getVectorizedData() {
        return vectorizedData;
    }

    public void setVectorizedData(byte[] vectorizedData) {
        this.vectorizedData = vectorizedData;
    }

    @NonNull
    @Override
    public String toString() {
        return "Activity{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", category=" + category +
                ", polyline='" + polyline + '\'' +
                ", starttime='" + startTime + '\'' +
                ", elapsedtime=" + elapsedTime +
                ", distance=" + distance +
                ", stravaId=" + stravaId +
                ", vectorizedData='" + Arrays.toString(vectorizedData) + '\'' +
                '}';
    }
}