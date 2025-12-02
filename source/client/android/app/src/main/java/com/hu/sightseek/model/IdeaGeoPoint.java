package com.hu.sightseek.model;

import org.osmdroid.views.overlay.simplefastpoint.LabelledGeoPoint;

public class IdeaGeoPoint extends LabelledGeoPoint {
    private final long id;

    public IdeaGeoPoint(double aLatitude, double aLongitude, String aLabel, long id) {
        super(aLatitude, aLongitude, aLabel);

        this.id = id;
    }

    public long getId() {
        return id;
    }
}
