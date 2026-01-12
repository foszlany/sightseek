package com.hu.sightseek.model;

import androidx.annotation.NonNull;

/** A RegionalEntry represents a distance travelled inside a region. */
public class RegionalEntry {
    /** Continent */
    private String continent;
    /** Country. */
    private String country;
    /** Large region. */
    private String largeRegion;
    /** Small region */
    private String smallRegion;
    /** Distance travelled inside a region */
    private Double distance;

    /** Default constructor */
    public RegionalEntry() {}

    public String getContinent() {
        return continent;
    }

    public String getCountry() {
        return country;
    }

    public String getLargeRegion() {
        return largeRegion;
    }

    public String getSmallRegion() {
        return smallRegion;
    }

    public Double getDistance() {
        return distance;
    }

    public void setContinent(String continent) {
        this.continent = continent;
    }

    public void setCountry(String country) {
        this.country = country;
    }

    public void setLargeRegion(String largeRegion) {
        this.largeRegion = largeRegion;
    }

    public void setSmallRegion(String smallRegion) {
        this.smallRegion = smallRegion;
    }

    public void setDistance(Double distance) {
        this.distance = distance;
    }

    @NonNull
    @Override
    public String toString() {
        return "RegionalEntry{" +
                "continent='" + continent + '\'' +
                ", country='" + country + '\'' +
                ", largeRegion='" + largeRegion + '\'' +
                ", smallRegion='" + smallRegion + '\'' +
                ", distance=" + distance +
                '}';
    }
}