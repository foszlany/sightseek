package com.hu.sightseek.db;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.google.android.gms.maps.model.LatLng;
import com.google.maps.android.PolyUtil;
import com.hu.sightseek.TravelCategory;
import com.hu.sightseek.model.Activity;

import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.overlay.Polyline;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class LocalActivityDatabaseDAO {
    private final LocalActivityDatabaseImpl dbHelper;

    public LocalActivityDatabaseDAO(Context context) {
        dbHelper = new LocalActivityDatabaseImpl(context);
    }

    public long addActivity(String name, int category, String polyline, String startTime, String endTime, double elapsedTime, double distance) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues values = new ContentValues();

        values.put(LocalActivityDatabaseImpl.ACTIVITIES_NAME, name);
        values.put(LocalActivityDatabaseImpl.ACTIVITIES_CATEGORY, category);
        values.put(LocalActivityDatabaseImpl.ACTIVITIES_POLYLINE, polyline);
        values.put(LocalActivityDatabaseImpl.ACTIVITIES_STARTTIME, startTime);
        values.put(LocalActivityDatabaseImpl.ACTIVITIES_ENDTIME, endTime);
        values.put(LocalActivityDatabaseImpl.ACTIVITIES_ELAPSEDTIME, elapsedTime);
        values.put(LocalActivityDatabaseImpl.ACTIVITIES_DISTANCE, distance);

        long id = db.insert(LocalActivityDatabaseImpl.ACTIVITIES_TABLE, null, values);
        db.close();

        return id;
    }

    public HashMap<String, Double> getStatistics() {
        SQLiteDatabase db = dbHelper.getReadableDatabase();

        String sql =
                "SELECT " +
                "IFNULL(SUM(" + LocalActivityDatabaseImpl.ACTIVITIES_DISTANCE + "), 0) AS total_distance, " +
                "IFNULL(SUM(" + LocalActivityDatabaseImpl.ACTIVITIES_ELAPSEDTIME + "), 0) AS total_time, " +
                "IFNULL(MAX(" + LocalActivityDatabaseImpl.ACTIVITIES_DISTANCE + "), 0) AS longest_distance, " +
                "IFNULL(MAX(" + LocalActivityDatabaseImpl.ACTIVITIES_ELAPSEDTIME + "), 0) AS longest_time " +
                "FROM " + LocalActivityDatabaseImpl.ACTIVITIES_TABLE;

        Cursor cursor = db.rawQuery(sql, null);

        HashMap<String, Double> res = new HashMap<>();
        if(cursor.moveToFirst()) {
            res.put("total_distance", cursor.getDouble(cursor.getColumnIndexOrThrow("total_distance")));
            res.put("total_time", cursor.getDouble(cursor.getColumnIndexOrThrow("total_time")));
            res.put("longest_distance", cursor.getDouble(cursor.getColumnIndexOrThrow("longest_distance")));
            res.put("longest_time", cursor.getDouble(cursor.getColumnIndexOrThrow("longest_time")));
        }

        cursor.close();
        db.close();

        return res;
    }

    public TravelCategory getMainTravelCategory() {
        SQLiteDatabase db = dbHelper.getReadableDatabase();

        String sql =
                "SELECT " + LocalActivityDatabaseImpl.ACTIVITIES_CATEGORY + " AS category, " +
                "COUNT(*) AS occurrences " +
                "FROM " + LocalActivityDatabaseImpl.ACTIVITIES_TABLE + " " +
                "GROUP BY " + LocalActivityDatabaseImpl.ACTIVITIES_CATEGORY + " " +
                "ORDER BY occurrences DESC " +
                "LIMIT 1";

        Cursor cursor = db.rawQuery(sql, null);

        if(cursor.moveToFirst()) {
            int categoryIndex = cursor.getInt(cursor.getColumnIndexOrThrow("category"));
            TravelCategory res = TravelCategory.values()[categoryIndex];

            cursor.close();
            db.close();

            return res;
        }
        else {
            cursor.close();
            db.close();

            return null;
        }
    }

    public Activity getActivity(int id) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();

        Cursor cursor = db.query(
                LocalActivityDatabaseImpl.ACTIVITIES_TABLE,
                null,
                "id=" + id,
                null,
                null,
                null,
                null
        );

        if(cursor.moveToFirst()) {
            String name = cursor.getString(cursor.getColumnIndexOrThrow(LocalActivityDatabaseImpl.ACTIVITIES_NAME));
            int categoryIndex = cursor.getInt(cursor.getColumnIndexOrThrow(LocalActivityDatabaseImpl.ACTIVITIES_CATEGORY));
            String polyline = cursor.getString(cursor.getColumnIndexOrThrow(LocalActivityDatabaseImpl.ACTIVITIES_POLYLINE));
            String starttime = cursor.getString(cursor.getColumnIndexOrThrow(LocalActivityDatabaseImpl.ACTIVITIES_STARTTIME));
            String endtime = cursor.getString(cursor.getColumnIndexOrThrow(LocalActivityDatabaseImpl.ACTIVITIES_ENDTIME));
            double elapsedtime = cursor.getDouble(cursor.getColumnIndexOrThrow(LocalActivityDatabaseImpl.ACTIVITIES_ELAPSEDTIME));
            double distance = cursor.getDouble(cursor.getColumnIndexOrThrow(LocalActivityDatabaseImpl.ACTIVITIES_DISTANCE));

            cursor.close();
            db.close();

            return new Activity(id, name, categoryIndex, polyline, starttime, endtime, elapsedtime, distance);
        }
        else {
            cursor.close();
            db.close();

            return null;
        }
    }

    public void deleteActivity(int id) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        db.delete(LocalActivityDatabaseImpl.ACTIVITIES_TABLE, "id = ?", new String[]{String.valueOf(id)});
        db.close();
    }

    public ArrayList<Activity> getAllActivities() {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        ArrayList<Activity> activities = new ArrayList<>();

        Cursor cursor = db.query(
                LocalActivityDatabaseImpl.ACTIVITIES_TABLE,
                null,
                null,
                null,
                null,
                null,
                LocalActivityDatabaseImpl.ACTIVITIES_ENDTIME + " DESC"
        );

        if(cursor.moveToFirst()) {
            do {
                int id = cursor.getInt(cursor.getColumnIndexOrThrow("id"));
                String name = cursor.getString(cursor.getColumnIndexOrThrow(LocalActivityDatabaseImpl.ACTIVITIES_NAME));
                int categoryIndex = cursor.getInt(cursor.getColumnIndexOrThrow(LocalActivityDatabaseImpl.ACTIVITIES_CATEGORY));
                String polyline = cursor.getString(cursor.getColumnIndexOrThrow(LocalActivityDatabaseImpl.ACTIVITIES_POLYLINE));
                String starttime = cursor.getString(cursor.getColumnIndexOrThrow(LocalActivityDatabaseImpl.ACTIVITIES_STARTTIME));
                String endtime = cursor.getString(cursor.getColumnIndexOrThrow(LocalActivityDatabaseImpl.ACTIVITIES_ENDTIME));
                double elapsedtime = cursor.getDouble(cursor.getColumnIndexOrThrow(LocalActivityDatabaseImpl.ACTIVITIES_ELAPSEDTIME));
                double distance = cursor.getDouble(cursor.getColumnIndexOrThrow(LocalActivityDatabaseImpl.ACTIVITIES_DISTANCE));

                activities.add(new Activity(id, name, categoryIndex, polyline, starttime, endtime, elapsedtime, distance));
            } while(cursor.moveToNext());
        }

        cursor.close();
        db.close();

        return activities;
    }

    public ArrayList<LatLng> getAllPoints() {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        ArrayList<LatLng> polylines = new ArrayList<>();

        Cursor cursor = db.query(
                LocalActivityDatabaseImpl.ACTIVITIES_TABLE,
                new String[]{ LocalActivityDatabaseImpl.ACTIVITIES_POLYLINE },
                null,
                null,
                null,
                null,
                null
        );

        if(cursor.moveToFirst()) {
            do {
                String polyline = cursor.getString(cursor.getColumnIndexOrThrow(LocalActivityDatabaseImpl.ACTIVITIES_POLYLINE));
                polylines.addAll(PolyUtil.decode(polyline));
            } while(cursor.moveToNext());
        }

        cursor.close();
        db.close();

        return polylines;
    }

    public ArrayList<Polyline> getAllPolylines(int tolerance) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        ArrayList<Polyline> polylines = new ArrayList<>();

        Cursor cursor = db.query(
                LocalActivityDatabaseImpl.ACTIVITIES_TABLE,
                new String[]{ LocalActivityDatabaseImpl.ACTIVITIES_POLYLINE },
                null,
                null,
                null,
                null,
                null
        );

        if(cursor.moveToFirst()) {
            do {
                String polylineString = cursor.getString(cursor.getColumnIndexOrThrow(LocalActivityDatabaseImpl.ACTIVITIES_POLYLINE));
                List<LatLng> latLngPoints;

                if(tolerance <= 0) {
                    latLngPoints = PolyUtil.decode(polylineString);
                }
                else {
                    latLngPoints = PolyUtil.simplify(PolyUtil.decode(polylineString), tolerance);
                }

                List<GeoPoint> geoPoints = new ArrayList<>(latLngPoints.size());
                for(LatLng p : latLngPoints) {
                    geoPoints.add(new GeoPoint(p.latitude, p.longitude));
                }

                Polyline polyline = new Polyline();
                polyline.setPoints(geoPoints);

                polylines.add(polyline);
            } while(cursor.moveToNext());
        }

        cursor.close();
        db.close();

        return polylines;
    }

    public void printAllActivities() {
        SQLiteDatabase db = dbHelper.getReadableDatabase();

        List<Activity> activities = getAllActivities();

        if(!activities.isEmpty()) {
            for(Activity a : activities) {
                System.out.println(a.toString());
            }
        }
        else {
            System.out.println("No rows found.");
        }

        db.close();
    }

    public void close() {
        dbHelper.close();
    }
}