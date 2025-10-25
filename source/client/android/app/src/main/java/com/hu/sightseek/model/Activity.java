package com.hu.sightseek.model;

import com.hu.sightseek.enums.TravelCategory;

public class Activity {
    private int id;
    private String name;
    private TravelCategory category;
    private String polyline;
    private String starttime;
    private double elapsedtime;
    private double distance;
    private long stravaId;
    private String vectorizedData;

    public Activity(int id, String name, int category, String polyline, String starttime, double elapsedtime, double distance, long stravaId, String vectorizedData) {
        this.id = id;
        this.name = name;
        this.category = TravelCategory.values()[category];
        this.polyline = polyline;
        this.starttime = starttime;
        this.elapsedtime = elapsedtime;
        this.distance = distance;
        this.stravaId = stravaId;
        this.vectorizedData = vectorizedData;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public TravelCategory getCategory() {
        return category;
    }

    public void setCategory(TravelCategory category) {
        this.category = category;
    }

    public String getPolyline() {
        return polyline;
    }

    public void setPolyline(String polyline) {
        this.polyline = polyline;
    }

    public String getStarttime() {
        return starttime;
    }

    public void setStarttime(String starttime) {
        this.starttime = starttime;
    }

    public double getElapsedtime() {
        return elapsedtime;
    }

    public void setElapsedtime(double elapsedtime) {
        this.elapsedtime = elapsedtime;
    }

    public double getDistance() {
        return distance;
    }

    public void setDistance(double distance) {
        this.distance = distance;
    }

    public long getStravaId() {
        return stravaId;
    }

    public void setStravaId(long stravaId) {
        this.stravaId = stravaId;
    }

    public String getVectorizedData() { return vectorizedData; }

    public void setVectorizedData(String vectorizedData) { this.vectorizedData = vectorizedData; }

    @Override
    public String toString() {
        return "Activity{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", category=" + category +
                ", polyline='" + polyline + '\'' +
                ", starttime='" + starttime + '\'' +
                ", elapsedtime=" + elapsedtime +
                ", distance=" + distance +
                ", stravaId=" + stravaId +
                ", vectorizedData='" + vectorizedData + '\'' +
                '}';
    }
}