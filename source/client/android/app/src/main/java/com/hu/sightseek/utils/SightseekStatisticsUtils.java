package com.hu.sightseek.utils;

import android.content.Context;

import com.google.android.gms.maps.model.LatLng;
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

public final class SightseekStatisticsUtils {
    private SightseekStatisticsUtils() {}

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

    public static HashMap<String, Serializable> getDetailedGenericStatistics(Context ctx) {
        HashMap<String, Serializable> values = new HashMap<>();

        if(FirebaseAuth.getInstance().getCurrentUser() != null) {
            FirebaseFirestore fireStoreDb = FirebaseFirestore.getInstance();
            String uid = FirebaseAuth.getInstance().getCurrentUser().getUid();

            fireStoreDb.collection("users")
                    .document(uid)
                    .get(Source.SERVER)
                    .addOnSuccessListener(documentSnapshot -> {
                        if(documentSnapshot.exists()) {
                            Map<String, Object> data = documentSnapshot.getData();

                            if(data == null) {
                                values.put("visited_cells", 0.0);
                            }
                            else {
                                values.put("visited_cells", (double) data.size());
                            }
                        }
                        else {
                            values.put("visited_cells", 0.0);
                        }
                    })
                    .addOnFailureListener(e -> {
                        values.put("visited_cells", 0.0);
                    });
        }
        else {
            values.put("visited_cells", -1);
        }

        LocalDatabaseDAO dao = new LocalDatabaseDAO(ctx);
        ArrayList<LatLng> allPoints = dao.getAllPoints();
        dao.close();

        values.put("total_points", (double) allPoints.size());

        LatLng medianPoint = getMedianPoint(allPoints);
        values.put("median_lat", medianPoint.latitude);
        values.put("median_lon", medianPoint.longitude);

        return values;
    }

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