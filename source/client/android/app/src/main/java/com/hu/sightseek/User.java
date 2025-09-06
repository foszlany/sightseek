package com.hu.sightseek;

import java.util.ArrayList;
import java.util.regex.Pattern;

// Temporarily here
public class User {
    private String username;
    private String email;
    private String password;
    private ArrayList<Integer> grid_ids;
    private ArrayList<Integer> activity_ids;

    public static final Pattern VALID_EMAIL_REGEX = Pattern.compile("^[\\w-.]+@([\\w-]+\\.)+[\\w-]{2,4}$", Pattern.CASE_INSENSITIVE);

    public User(String username, String email, String password) {
        this.username = username;
        this.email = email;
        this.password = password;
        this.grid_ids = new ArrayList<>();
        this.activity_ids = new ArrayList<>(activity_ids);
    }

    public User(String username, String email, String password, ArrayList<Integer> grid_ids, ArrayList<Integer> activity_ids) {
        this.username = username;
        this.email = email;
        this.password = password;
        this.grid_ids = grid_ids;
        this.activity_ids = activity_ids;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        if(!(username.length() >= 3 && username.length() <= 18)) {
            throw new IllegalArgumentException("Username must be 3-18 characters long.");
        }
        else {
            this.username = username;
        }
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        if(VALID_EMAIL_REGEX.matcher(email).find()) {
            this.email = email;
        }
        else {
            throw new IllegalArgumentException("Invalid email address.");
        }
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        if(!(password.length() >= 8 && password.length() <= 32)) {
            throw new IllegalArgumentException("Password must be 8-32 characters long.");
        }
        else {
            this.password = password;
        }
    }

    public ArrayList<Integer> getGrid_ids() {
        return grid_ids;
    }

    public void setGrid_ids(ArrayList<Integer> grid_ids) {
        this.grid_ids = grid_ids;
    }

    public ArrayList<Integer> getActivity_ids() {
        return activity_ids;
    }

    public void setActivity_ids(ArrayList<Integer> activity_ids) {
        this.activity_ids = activity_ids;
    }
}