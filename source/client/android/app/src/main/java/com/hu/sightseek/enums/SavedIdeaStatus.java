package com.hu.sightseek.enums;

import androidx.annotation.NonNull;

/** Defines statuses for ideas */
public enum SavedIdeaStatus {
    /** Saved, will show up for the user on the map */
    SAVED(0),
    /** Ignored, will only show up in the management tab. */
    IGNORED(1),
    /** Visited, will only show up in the management tab. */
    VISITED(2),
    /** For invalid or temporary usage */
    INVALID(3);

    private final int index;

    SavedIdeaStatus(int index) {
        this.index = index;
    }

    public int getIndex() {
        return index;
    }

    @NonNull
    @Override
    public String toString() {
        switch(this) {
            case SAVED: return "Saved";
            case IGNORED: return "Ignored";
            case VISITED: return "Visited";
            default: return "Invalid attraction status";
        }
    }
}
