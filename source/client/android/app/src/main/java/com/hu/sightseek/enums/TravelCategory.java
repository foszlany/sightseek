package com.hu.sightseek.enums;

import androidx.annotation.NonNull;

/** Defines travel categories */
public enum TravelCategory {
    /** Travel types where little to no assist is given to the person. Examples: walking, running, swimming */
    LOCOMOTOR(0),
    /** Travel types where some assist is given to the person, although effort is still required. Examples: cycling, skateboarding, skating. */
    MICROMOBILITY(1),
    /** Travel types that require very little effort from the person. Examples: driving a car, taking the bus, flying on a plane. */
    OTHER(2),
    /** For invalid or temporary usage */
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
