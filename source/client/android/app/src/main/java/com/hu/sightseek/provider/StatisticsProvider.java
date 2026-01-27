package com.hu.sightseek.provider;

import android.content.Context;

import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.TaskCompletionSource;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Source;
import com.hu.sightseek.db.LocalDatabaseDAO;
import com.hu.sightseek.enums.TravelCategory;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Provides statistics based on stored user data. */
public final class StatisticsProvider {
    /** Key for the number of visited cells */
    public static final String STATISTICS_KEY_VISITED_CELLS = "visited_cells";
    /** Key for the total recorded distance */
    public static final String STATISTICS_KEY_TOTAL_DISTANCE = "total_distance";
    /** Key for the total recorded time */
    public static final String STATISTICS_KEY_TOTAL_TIME = "total_time";
    /** Key for the calculated average speed */
    public static final String STATISTICS_KEY_AVERAGE_SPEED = "average_speed";
    /** Key for the lower estimate of burned calories */
    public static final String STATISTICS_KEY_APPROX_CALORIES_LOW = "approx_calories_low";
    /** Key for the higher estimate of burned calories */
    public static final String STATISTICS_KEY_APPROX_CALORIES_HIGH = "approx_calories_high";
    /** Key for the total number of recorded points */
    public static final String STATISTICS_KEY_TOTAL_POINTS = "total_points";
    /** Key for the median latitude of all recorded points */
    public static final String STATISTICS_KEY_MEDIAN_LAT = "median_lat";
    /** Key for the median longitude of all recorded points. */
    public static final String STATISTICS_KEY_MEDIAN_LON = "median_lon";
    /** Key for the total recorded distance. */
    public static final String STATISTICS_KEY_LONGEST_DISTANCE = "longest_distance";
    /** Key for the longest single recorded activity time */
    public static final String STATISTICS_KEY_LONGEST_TIME = "longest_time";
    /** Key for the total number of recorded activities */
    public static final String STATISTICS_KEY_ACTIVITY_COUNT = "activity_count";
    /** Key for the number of imported (Strava) activities */
    public static final String STATISTICS_KEY_IMPORTED_COUNT = "imported_count";



    /** Private constructor */
    private StatisticsProvider() {}

    /**
     * Gets generic statistics based on your profile
     * @param ctx Context
     * @return Map with generic statistics
     */
    public static Task<HashMap<String, Serializable>> getDetailedGenericStatistics(Context ctx) {
        TaskCompletionSource<HashMap<String, Serializable>> source = new TaskCompletionSource<>();
        HashMap<String, Serializable> values = new HashMap<>();

        if(FirebaseAuth.getInstance().getCurrentUser() != null) {
            FirebaseFirestore fireStoreDb = FirebaseFirestore.getInstance();
            String uid = FirebaseAuth.getInstance().getCurrentUser().getUid();

            fireStoreDb.collection("users")
                    .document(uid)
                    .get(Source.SERVER)
                    .addOnCompleteListener(task -> {
                        double visited = 0.0;

                        if(task.isSuccessful() && task.getResult().exists()) {
                            Map<String, Object> data = task.getResult().getData();

                            if(data != null && data.containsKey("visitedCells")) {
                                Object visitedCellsObj = data.get("visitedCells");
                                if(visitedCellsObj instanceof Map) {
                                    Map<?, ?> visitedCellsMap = (Map<?, ?>) visitedCellsObj;
                                    visited = visitedCellsMap.size();
                                }
                            }
                        }
                        values.put(STATISTICS_KEY_VISITED_CELLS, visited);

                        fillLocalStats(ctx, values);
                        source.setResult(values);
                    });
        }
        else {
            values.put(STATISTICS_KEY_VISITED_CELLS, -1.0);
            fillLocalStats(ctx, values);
            source.setResult(values);
        }

        return source.getTask();
    }

    /**
     * Gets category specific statistics based on your profile
     * @param ctx Context
     * @param category Travel category
     * @return Map with the category specific statistics
     */
    public static HashMap<String, Serializable> getCategorySpecificStatistics(Context ctx, TravelCategory category) {
        LocalDatabaseDAO dao = new LocalDatabaseDAO(ctx);
        HashMap<String, Serializable> values = dao.getBaseStatistics(category);
        dao.close();

        Double valueHolder;

        // Speed
        valueHolder = (Double)values.get(STATISTICS_KEY_TOTAL_DISTANCE);
        double totalDistance = (valueHolder != null) ? (valueHolder) : 0.0;

        valueHolder = (Double)values.get(STATISTICS_KEY_TOTAL_TIME);
        double totalTime = (valueHolder != null) ? (valueHolder) : 0.0;

        values.put(STATISTICS_KEY_AVERAGE_SPEED, (totalTime != 0) ? ((totalDistance / totalTime) * 3.6) : 0);

        // Calories
        double approxCaloriesLow = 0;
        double approxCaloriesHigh = 0;
        switch(category) {
            case LOCOMOTOR:
                approxCaloriesLow = 30 * (totalDistance / 1000.0);
                approxCaloriesHigh = 130 * (totalDistance / 1000.0);
                break;

            case MICROMOBILITY:
                approxCaloriesLow = 15 * (totalDistance / 1000.0);
                approxCaloriesHigh = 60 * (totalDistance / 1000.0);
                break;

            case OTHER:
                approxCaloriesLow = 2 * (totalTime / 3600.0);
                approxCaloriesHigh = 10 * (totalTime / 3600.0);
                break;
        }
        values.put(STATISTICS_KEY_APPROX_CALORIES_LOW, approxCaloriesLow);
        values.put(STATISTICS_KEY_APPROX_CALORIES_HIGH, approxCaloriesHigh);

        return values;
    }

    /**
     * Adds "total points" and "median point" to a map of statistics
     * @param ctx Context
     * @param values Map to put values into
     */
    private static void fillLocalStats(Context ctx, HashMap<String, Serializable> values) {
        LocalDatabaseDAO dao = new LocalDatabaseDAO(ctx);
        List<LatLng> allPoints = dao.getAllPoints();
        dao.close();

        values.put(STATISTICS_KEY_TOTAL_POINTS, (double) allPoints.size());

        LatLng medianPoint = getMedianPoint(allPoints);
        values.put(STATISTICS_KEY_MEDIAN_LAT, medianPoint.latitude);
        values.put(STATISTICS_KEY_MEDIAN_LON, medianPoint.longitude);
    }

    /**
     * Finds the median point based on all recorded activities
     * @param allPoints List of all points
     * @return Median point
     */
    public static LatLng getMedianPoint(List<LatLng> allPoints) {
        int n = allPoints.size();
        double[] lats = new double[n];
        double[] lons = new double[n];

        for(int i = 0; i < n; i++) {
            LatLng p = allPoints.get(i);
            lats[i] = p.latitude;
            lons[i] = p.longitude;
        }

        Arrays.sort(lats);
        Arrays.sort(lons);

        return new LatLng(lats[n / 2], lons[n / 2]);
    }
}