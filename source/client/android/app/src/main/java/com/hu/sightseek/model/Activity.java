package com.hu.sightseek.model;

import androidx.annotation.NonNull;

import com.hu.sightseek.enums.TravelCategory;

import java.util.Arrays;

public class Activity {
    private final int id;
    private final String name;
    private final TravelCategory category;
    private final String polyline;
    private final String startTime;
    private final double elapsedTime;
    private final double distance;
    private final long stravaId;
    private byte[] vectorizedData;

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

    public byte[] getVectorizedData() { return vectorizedData; }

    public void setVectorizedData(byte[] vectorizedData) { this.vectorizedData = vectorizedData; }

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