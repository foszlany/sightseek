package com.hu.sightseek;

import com.hu.sightseek.enums.TravelCategory;
import com.hu.sightseek.model.Activity;

// Temporarily here
public class OnlineActivity extends Activity {
    private boolean isPublic;

    public OnlineActivity(int id, String name, TravelCategory category, String polyline, String starttime, double elapsedtime, double distance) {
        super(id, name, category.getIndex(), polyline, starttime, elapsedtime, distance, -1);
    }

    public OnlineActivity(int id, String name, int category, String polyline, String starttime, double elapsedtime, double distance) {
        super(id, name, category, polyline, starttime, elapsedtime, distance, -1);
    }

    public boolean isPublic() {
        return isPublic;
    }

    public void setPublic(boolean aPublic) {
        isPublic = aPublic;
    }
}