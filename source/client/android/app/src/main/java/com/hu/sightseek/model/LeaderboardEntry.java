package com.hu.sightseek.model;

public class LeaderboardEntry {
    private final String username;
    private final double value;

    public LeaderboardEntry(String username, double value) {
        this.username = username;
        this.value = value;
    }

    public String getUsername() {
        return username;
    }

    public double getValue() {
        return value;
    }
}