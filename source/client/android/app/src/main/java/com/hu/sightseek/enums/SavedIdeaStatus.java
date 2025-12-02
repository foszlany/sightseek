package com.hu.sightseek.enums;

import androidx.annotation.NonNull;

public enum SavedIdeaStatus {
    SAVED(0),
    IGNORED(1),
    VISITED(2),
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
