package com.hu.sightseek.model;

import androidx.annotation.NonNull;

import com.hu.sightseek.enums.SavedIdeaStatus;

public class Idea {
    private final long id;
    private final String name;
    private final String place;
    private final double latitude;
    private final double longitude;
    private SavedIdeaStatus status;

    public Idea(long id, String name, String place, double latitude, double longitude, SavedIdeaStatus status) {
        this.id = id;
        this.name = name;
        this.place = place;
        this.latitude = latitude;
        this.longitude = longitude;
        this.status = status;
    }

    public long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getPlace() {
        return place;
    }

    public SavedIdeaStatus getStatus() {
        return status;
    }

    public double getLatitude() {
        return latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public void setStatus(SavedIdeaStatus status) {
        this.status = status;
    }

    @NonNull
    @Override
    public String toString() {
        return "Idea{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", place='" + place + '\'' +
                ", status=" + status +
                '}';
    }
}