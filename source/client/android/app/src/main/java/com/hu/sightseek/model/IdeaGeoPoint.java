package com.hu.sightseek.model;

import org.osmdroid.views.overlay.simplefastpoint.LabelledGeoPoint;

/** A GeoPoint representing an Idea (Attraction). */
public class IdeaGeoPoint extends LabelledGeoPoint {
    /** ID of the Idea */
    private final long id;

    /**
     * Constructor
     * @param aLatitude Latitude
     * @param aLongitude Longitude
     * @param aLabel Name of the Idea
     * @param id ID of the Idea
     */
    public IdeaGeoPoint(double aLatitude, double aLongitude, String aLabel, long id) {
        super(aLatitude, aLongitude, aLabel);

        this.id = id;
    }

    public long getId() {
        return id;
    }
}
