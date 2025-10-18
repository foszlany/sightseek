package com.hu.sightseek.model;

public class LeaderboardEntry {
    private String username;
    private double value;

    public LeaderboardEntry(String username, double value) {
        this.username = username;
        this.value = value;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public double getValue() {
        return value;
    }

    public void setValue(double value) {
        this.value = value;
    }
}