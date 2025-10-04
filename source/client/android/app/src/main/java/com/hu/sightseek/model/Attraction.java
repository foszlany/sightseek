package com.hu.sightseek.model;

import androidx.annotation.NonNull;

import com.hu.sightseek.enums.SavedAttractionStatus;

public class Attraction {
    private long id;
    private String name;
    private String place;
    private SavedAttractionStatus status;

    public Attraction(long id, String name, String place, SavedAttractionStatus status) {
        this.id = id;
        this.name = name;
        this.place = place;
        this.status = status;
    }

    public Attraction() {}

    public long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getPlace() {
        return place;
    }

    public SavedAttractionStatus getStatus() {
        return status;
    }

    @NonNull
    @Override
    public String toString() {
        return "Attraction{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", place='" + place + '\'' +
                ", status=" + status +
                '}';
    }
}