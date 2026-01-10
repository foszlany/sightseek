package com.hu.sightseek.model;

import androidx.annotation.NonNull;

import com.hu.sightseek.enums.SavedIdeaStatus;

/** Represents an Idea ("Attraction") */
public class Idea {
    /** ID */
    private final long id;
    /** Name */
    private final String name;
    /** Place (city or country) */
    private final String place;
    /** Latitude */
    private final double latitude;
    /** Longitude */
    private final double longitude;
    /** Status */
    private SavedIdeaStatus status;

    /** Constructor */
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