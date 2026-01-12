package com.hu.sightseek.model;

/** A LeaderboardEntry represents an entry for the leaderboard with a username and a score value. */
public class LeaderboardEntry {
    /** Username */
    private final String username;
    /** Score */
    private final double score;

    /**
     * Constructor
     * @param username Username
     * @param score Score
     */
    public LeaderboardEntry(String username, double score) {
        this.username = username;
        this.score = score;
    }

    public String getUsername() {
        return username;
    }

    public double getScore() {
        return score;
    }
}