package com.hu.sightseek;

public class Activity extends LocalActivity {
    

    public Activity(int id, String name, TravelCategory category, String polyline, String starttime, String endtime, double elapsedtime, double distance) {
        super(id, name, category, polyline, starttime, endtime, elapsedtime, distance);
    }

    public Activity(int id, String name, int category, String polyline, String starttime, String endtime, double elapsedtime, double distance) {
        super(id, name, category, polyline, starttime, endtime, elapsedtime, distance);
    }
}