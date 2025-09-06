package com.hu.sightseek;

// Temporarily here
public class OnlineActivity extends Activity {
    private boolean isPublic;

    public OnlineActivity(int id, String name, TravelCategory category, String polyline, String starttime, String endtime, double elapsedtime, double distance) {
        super(id, name, category, polyline, starttime, endtime, elapsedtime, distance);
    }

    public OnlineActivity(int id, String name, int category, String polyline, String starttime, String endtime, double elapsedtime, double distance) {
        super(id, name, category, polyline, starttime, endtime, elapsedtime, distance);
    }

    public boolean isPublic() {
        return isPublic;
    }

    public void setPublic(boolean aPublic) {
        isPublic = aPublic;
    }
}