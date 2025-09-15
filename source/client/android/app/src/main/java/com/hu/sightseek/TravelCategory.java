package com.hu.sightseek;

import androidx.annotation.NonNull;

// Temporarily here
public enum TravelCategory {
    LOCOMOTOR(0),
    MICROMOBILITY(1),
    OTHER(2),
    INVALID(3);

    private final int index;

    TravelCategory(int index) {
        this.index = index;
    }

    public int getIndex() {
        return index;
    }



    @NonNull
    public String toShortString() {
        switch(this) {
            case LOCOMOTOR: return "Locomotor";
            case MICROMOBILITY: return "Micromobility";
            case OTHER: return "Other";
            default: return "Invalid travel method";
        }
    }

    @NonNull
    @Override
    public String toString() {
        switch(this) {
            case LOCOMOTOR: return "Locomotor (walk, run, swim, hike...)";
            case MICROMOBILITY: return "Micromobility (bicycle, skateboard, scooter...)";
            case OTHER: return "Other (motorbike, car, ship...)";
            default: return "Invalid travel method";
        }
    }
}
