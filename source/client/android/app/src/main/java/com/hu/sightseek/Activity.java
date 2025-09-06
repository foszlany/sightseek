package com.hu.sightseek;

import androidx.annotation.NonNull;

// Temporarily here
public class Activity {
    private int id;
    private String name;
    private TravelCategory category;
    private String polyline;
    private String starttime;
    private String endtime;
    private double elapsedtime;
    private double distance;

    public Activity(int id, String name, TravelCategory category, String polyline, String starttime, String endtime, double elapsedtime, double distance) {
        this.id = id;
        this.name = name;
        this.category = category;
        this.polyline = polyline;
        this.starttime = starttime;
        this.endtime = endtime;
        this.elapsedtime = elapsedtime;
        this.distance = distance;
    }

    public Activity(int id, String name, int category, String polyline, String starttime, String endtime, double elapsedtime, double distance) {
        this.id = id;
        this.name = name;
        this.category = TravelCategory.values()[category];
        this.polyline = polyline;
        this.starttime = starttime;
        this.endtime = endtime;
        this.elapsedtime = elapsedtime;
        this.distance = distance;
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

    public String getEndtime() {
        return endtime;
    }

    public void setEndtime(String endtime) {
        this.endtime = endtime;
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

    @NonNull
    @Override
    public String toString() {
        return "Activity{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", category=" + category +
                ", polyline='" + polyline + '\'' +
                ", starttime='" + starttime + '\'' +
                ", endtime='" + endtime + '\'' +
                ", elapsedtime=" + elapsedtime +
                ", distance=" + distance +
                '}';
    }
}