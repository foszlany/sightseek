package com.hu.sightseek.model;

import androidx.annotation.NonNull;

import org.locationtech.jts.geom.Geometry;

public class RegionalEntry {
    private String continent;
    private String country;
    private String largeRegion;
    private String smallRegion;
    private Double distance;

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