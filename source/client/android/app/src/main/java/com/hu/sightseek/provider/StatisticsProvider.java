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
import java.util.Map;

/** Provides statistics based on stored user data. */
public final class StatisticsProvider {
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
                        values.put("visited_cells", visited);

                        fillLocalStats(ctx, values);
                        source.setResult(values);
                    });
        }
        else {
            values.put("visited_cells", -1.0);
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
        valueHolder = (Double)values.get("total_distance");
        double totalDistance = (valueHolder != null) ? (valueHolder) : 0.0;

        valueHolder = (Double)values.get("total_time");
        double totalTime = (valueHolder != null) ? (valueHolder) : 0.0;

        values.put("average_speed", (totalTime != 0) ? ((totalDistance / totalTime) * 3.6) : 0);

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
        values.put("approx_calories_low", approxCaloriesLow);
        values.put("approx_calories_high", approxCaloriesHigh);

        return values;
    }

    /**
     * Adds "total points" and "median point" to a map of statistics
     * @param ctx Context
     * @param values Map to put values into
     */
    private static void fillLocalStats(Context ctx, HashMap<String, Serializable> values) {
        LocalDatabaseDAO dao = new LocalDatabaseDAO(ctx);
        ArrayList<LatLng> allPoints = dao.getAllPoints();
        dao.close();

        values.put("total_points", (double) allPoints.size());

        LatLng medianPoint = getMedianPoint(allPoints);
        values.put("median_lat", medianPoint.latitude);
        values.put("median_lon", medianPoint.longitude);
    }

    /**
     * Finds the median point based on all recorded activities
     * @param allPoints List of all points
     * @return Median point
     */
    public static LatLng getMedianPoint(ArrayList<LatLng> allPoints) {
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