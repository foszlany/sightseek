package com.hu.sightseek.db;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.google.android.gms.maps.model.LatLng;
import com.google.maps.android.PolyUtil;
import com.hu.sightseek.enums.SavedAttractionStatus;
import com.hu.sightseek.enums.TravelCategory;
import com.hu.sightseek.model.Activity;
import com.hu.sightseek.model.Attraction;

import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.overlay.Polyline;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

public class LocalDatabaseDAO {
    private final LocalDatabaseImpl dbHelper;

    public LocalDatabaseDAO(Context context) {
        dbHelper = new LocalDatabaseImpl(context);
    }

    public void close() {
        dbHelper.close();
    }

    /* ############### ACTIVITIES ############### */
    public long addActivity(String name, int category, String polyline, String startTime, double elapsedTime, double distance, long stravaid) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues values = new ContentValues();

        values.put(LocalDatabaseImpl.ACTIVITIES_NAME, name);
        values.put(LocalDatabaseImpl.ACTIVITIES_CATEGORY, category);
        values.put(LocalDatabaseImpl.ACTIVITIES_POLYLINE, polyline);
        values.put(LocalDatabaseImpl.ACTIVITIES_STARTTIME, startTime);
        values.put(LocalDatabaseImpl.ACTIVITIES_ELAPSEDTIME, elapsedTime);
        values.put(LocalDatabaseImpl.ACTIVITIES_DISTANCE, distance);
        values.put(LocalDatabaseImpl.ACTIVITIES_STRAVAID, stravaid);

        long id = db.insert(LocalDatabaseImpl.ACTIVITIES_TABLE, null, values);
        db.close();

        return id;
    }

    public HashMap<String, Serializable> getBaseStatistics(TravelCategory category) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();

        String sql;
        if(category == TravelCategory.INVALID) {
            sql =
                "SELECT " +
                "IFNULL(SUM(" + LocalDatabaseImpl.ACTIVITIES_DISTANCE + "), 0) AS total_distance, " +
                "IFNULL(SUM(" + LocalDatabaseImpl.ACTIVITIES_ELAPSEDTIME + "), 0) AS total_time, " +
                "IFNULL(MAX(" + LocalDatabaseImpl.ACTIVITIES_DISTANCE + "), 0) AS longest_distance, " +
                "IFNULL(MAX(" + LocalDatabaseImpl.ACTIVITIES_ELAPSEDTIME + "), 0) AS longest_time, " +
                "COUNT(*) AS activity_count, " +
                "SUM(CASE WHEN " + LocalDatabaseImpl.ACTIVITIES_STRAVAID + " != -1 THEN 1 ELSE 0 END) AS imported_count " +
                "FROM " + LocalDatabaseImpl.ACTIVITIES_TABLE;
        }
        else {
            sql =
                "SELECT " +
                "IFNULL(SUM(" + LocalDatabaseImpl.ACTIVITIES_DISTANCE + "), 0) AS total_distance, " +
                "IFNULL(SUM(" + LocalDatabaseImpl.ACTIVITIES_ELAPSEDTIME + "), 0) AS total_time, " +
                "IFNULL(MAX(" + LocalDatabaseImpl.ACTIVITIES_DISTANCE + "), 0) AS longest_distance, " +
                "IFNULL(MAX(" + LocalDatabaseImpl.ACTIVITIES_ELAPSEDTIME + "), 0) AS longest_time, " +
                "COUNT(*) AS activity_count, " +
                "SUM(CASE WHEN " + LocalDatabaseImpl.ACTIVITIES_STRAVAID + " != -1 THEN 1 ELSE 0 END) AS imported_count " +
                "FROM " + LocalDatabaseImpl.ACTIVITIES_TABLE + " " +
                "WHERE " + LocalDatabaseImpl.ACTIVITIES_CATEGORY + " = " + category.getIndex();
        }

        Cursor cursor = db.rawQuery(sql, null);

        HashMap<String, Serializable> res = new HashMap<>();
        if(cursor.moveToFirst()) {
            res.put("total_distance", cursor.getDouble(cursor.getColumnIndexOrThrow("total_distance")));
            res.put("total_time", cursor.getDouble(cursor.getColumnIndexOrThrow("total_time")));
            res.put("longest_distance", cursor.getDouble(cursor.getColumnIndexOrThrow("longest_distance")));
            res.put("longest_time", cursor.getDouble(cursor.getColumnIndexOrThrow("longest_time")));
            res.put("activity_count", cursor.getDouble(cursor.getColumnIndexOrThrow("activity_count")));
            res.put("imported_count", cursor.getDouble(cursor.getColumnIndexOrThrow("imported_count")));
        }

        cursor.close();
        db.close();

        return res;
    }

    public TravelCategory getMainTravelCategory() {
        SQLiteDatabase db = dbHelper.getReadableDatabase();

        String sql =
                "SELECT " + LocalDatabaseImpl.ACTIVITIES_CATEGORY + " AS category, " +
                "COUNT(*) AS occurrences " +
                "FROM " + LocalDatabaseImpl.ACTIVITIES_TABLE + " " +
                "GROUP BY " + LocalDatabaseImpl.ACTIVITIES_CATEGORY + " " +
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
                LocalDatabaseImpl.ACTIVITIES_TABLE,
                null,
                "id=" + id,
                null,
                null,
                null,
                null
        );

        if(cursor.moveToFirst()) {
            String name = cursor.getString(cursor.getColumnIndexOrThrow(LocalDatabaseImpl.ACTIVITIES_NAME));
            int categoryIndex = cursor.getInt(cursor.getColumnIndexOrThrow(LocalDatabaseImpl.ACTIVITIES_CATEGORY));
            String polyline = cursor.getString(cursor.getColumnIndexOrThrow(LocalDatabaseImpl.ACTIVITIES_POLYLINE));
            String starttime = cursor.getString(cursor.getColumnIndexOrThrow(LocalDatabaseImpl.ACTIVITIES_STARTTIME));
            double elapsedtime = cursor.getDouble(cursor.getColumnIndexOrThrow(LocalDatabaseImpl.ACTIVITIES_ELAPSEDTIME));
            double distance = cursor.getDouble(cursor.getColumnIndexOrThrow(LocalDatabaseImpl.ACTIVITIES_DISTANCE));

            cursor.close();
            db.close();

            return new Activity(id, name, categoryIndex, polyline, starttime, elapsedtime, distance, -1);
        }
        else {
            cursor.close();
            db.close();

            return null;
        }
    }

    public void deleteActivity(int id) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        db.delete(LocalDatabaseImpl.ACTIVITIES_TABLE, "id = ?", new String[]{String.valueOf(id)});
        db.close();
    }

    public ArrayList<Activity> getAllActivities() {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        ArrayList<Activity> activities = new ArrayList<>();

        Cursor cursor = db.query(
                LocalDatabaseImpl.ACTIVITIES_TABLE,
                null,
                null,
                null,
                null,
                null,
                LocalDatabaseImpl.ACTIVITIES_STARTTIME + " DESC"
        );

        if(cursor.moveToFirst()) {
            do {
                int id = cursor.getInt(cursor.getColumnIndexOrThrow("id"));
                String name = cursor.getString(cursor.getColumnIndexOrThrow(LocalDatabaseImpl.ACTIVITIES_NAME));
                int categoryIndex = cursor.getInt(cursor.getColumnIndexOrThrow(LocalDatabaseImpl.ACTIVITIES_CATEGORY));
                String polyline = cursor.getString(cursor.getColumnIndexOrThrow(LocalDatabaseImpl.ACTIVITIES_POLYLINE));
                String starttime = cursor.getString(cursor.getColumnIndexOrThrow(LocalDatabaseImpl.ACTIVITIES_STARTTIME));
                double elapsedtime = cursor.getDouble(cursor.getColumnIndexOrThrow(LocalDatabaseImpl.ACTIVITIES_ELAPSEDTIME));
                double distance = cursor.getDouble(cursor.getColumnIndexOrThrow(LocalDatabaseImpl.ACTIVITIES_DISTANCE));

                activities.add(new Activity(id, name, categoryIndex, polyline, starttime, elapsedtime, distance, -1));
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
                LocalDatabaseImpl.ACTIVITIES_TABLE,
                new String[]{LocalDatabaseImpl.ACTIVITIES_POLYLINE},
                null,
                null,
                null,
                null,
                null
        );

        if(cursor.moveToFirst()) {
            do {
                String polyline = cursor.getString(cursor.getColumnIndexOrThrow(LocalDatabaseImpl.ACTIVITIES_POLYLINE));
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
                LocalDatabaseImpl.ACTIVITIES_TABLE,
                new String[]{LocalDatabaseImpl.ACTIVITIES_POLYLINE},
                null,
                null,
                null,
                null,
                null
        );

        if(cursor.moveToFirst()) {
            do {
                String polylineString = cursor.getString(cursor.getColumnIndexOrThrow(LocalDatabaseImpl.ACTIVITIES_POLYLINE));
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
            System.out.println("###########");
            for(Activity a : activities) {
                System.out.println(a.toString());
            }
            System.out.println("###########");
        }
        else {
            System.out.println("No rows found.");
        }

        db.close();
    }

    /* ############### ATTRACTIONS ############### */

    public ArrayList<Attraction> getAllAttractions() {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        ArrayList<Attraction> attractions = new ArrayList<>();

        Cursor cursor = db.query(
                LocalDatabaseImpl.ATTRACTIONS_TABLE,
                null,
                null,
                null,
                null,
                null,
                LocalDatabaseImpl.ATTRACTIONS_STATUS + " DESC"
        );

        if(cursor.moveToFirst()) {
            do {
                long id = cursor.getLong(cursor.getColumnIndexOrThrow(LocalDatabaseImpl.ATTRACTIONS_ID));
                String name = cursor.getString(cursor.getColumnIndexOrThrow(LocalDatabaseImpl.ATTRACTIONS_NAME));
                String place = cursor.getString(cursor.getColumnIndexOrThrow(LocalDatabaseImpl.ATTRACTIONS_PLACE));
                double latitude = cursor.getDouble(cursor.getColumnIndexOrThrow(LocalDatabaseImpl.ATTRACTIONS_LATITUDE));
                double longitude = cursor.getDouble(cursor.getColumnIndexOrThrow(LocalDatabaseImpl.ATTRACTIONS_LONGITUDE));
                int status = cursor.getInt(cursor.getColumnIndexOrThrow(LocalDatabaseImpl.ATTRACTIONS_STATUS));

                attractions.add(new Attraction(id, name, place, latitude, longitude, SavedAttractionStatus.values()[status]));
            } while(cursor.moveToNext());
        }

        cursor.close();
        db.close();

        return attractions;
    }

    public ArrayList<Attraction> getSavedAttractions() {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        ArrayList<Attraction> attractions = new ArrayList<>();

        Cursor cursor = db.query(
                LocalDatabaseImpl.ATTRACTIONS_TABLE,
                null,
                LocalDatabaseImpl.ATTRACTIONS_STATUS + " = ?",
                new String[]{String.valueOf(SavedAttractionStatus.SAVED.getIndex())},
                null,
                null,
                LocalDatabaseImpl.ATTRACTIONS_ID + " DESC"
        );

        if(cursor.moveToFirst()) {
            do {
                long id = cursor.getLong(cursor.getColumnIndexOrThrow(LocalDatabaseImpl.ATTRACTIONS_ID));
                String name = cursor.getString(cursor.getColumnIndexOrThrow(LocalDatabaseImpl.ATTRACTIONS_NAME));
                String place = cursor.getString(cursor.getColumnIndexOrThrow(LocalDatabaseImpl.ATTRACTIONS_PLACE));
                double latitude = cursor.getDouble(cursor.getColumnIndexOrThrow(LocalDatabaseImpl.ATTRACTIONS_LATITUDE));
                double longitude = cursor.getDouble(cursor.getColumnIndexOrThrow(LocalDatabaseImpl.ATTRACTIONS_LONGITUDE));
                int status = cursor.getInt(cursor.getColumnIndexOrThrow(LocalDatabaseImpl.ATTRACTIONS_STATUS));

                attractions.add(new Attraction(id, name, place, latitude, longitude, SavedAttractionStatus.values()[status]));
            } while(cursor.moveToNext());
        }

        cursor.close();
        db.close();

        return attractions;
    }

    public long addAttraction(long id, String name, String place, double latitude, double longitude, int status) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues values = new ContentValues();

        values.put(LocalDatabaseImpl.ATTRACTIONS_ID, id);
        values.put(LocalDatabaseImpl.ATTRACTIONS_NAME, name);
        values.put(LocalDatabaseImpl.ATTRACTIONS_PLACE, place);
        values.put(LocalDatabaseImpl.ATTRACTIONS_LATITUDE, latitude);
        values.put(LocalDatabaseImpl.ATTRACTIONS_LONGITUDE, longitude);
        values.put(LocalDatabaseImpl.ATTRACTIONS_STATUS, status);

        db.insert(LocalDatabaseImpl.ATTRACTIONS_TABLE, null, values);
        db.close();

        return id;
    }

    public int updateAttractionStatus(long id, int newStatus) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues values = new ContentValues();

        values.put(LocalDatabaseImpl.ATTRACTIONS_STATUS, newStatus);

        int rowsAffected = db.update(
                LocalDatabaseImpl.ATTRACTIONS_TABLE,
                values,
                LocalDatabaseImpl.ATTRACTIONS_ID + " = ?",
                new String[]{String.valueOf(id)}
        );

        db.close();
        return rowsAffected;
    }

    public HashSet<Long> getAttractionIds() {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        HashSet<Long> ids = new HashSet<>();

        Cursor cursor = db.query(
                LocalDatabaseImpl.ATTRACTIONS_TABLE,
                new String[]{LocalDatabaseImpl.ATTRACTIONS_ID},
                null,
                null,
                null,
                null,
                null
        );

        if(cursor.moveToFirst()) {
            do {
                long id = cursor.getLong(cursor.getColumnIndexOrThrow(LocalDatabaseImpl.ATTRACTIONS_ID));
                ids.add(id);
            } while(cursor.moveToNext());
        }

        cursor.close();
        db.close();

        return ids;
    }

    public void deleteAttraction(long id) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        db.delete(LocalDatabaseImpl.ATTRACTIONS_TABLE, "id = ?", new String[]{String.valueOf(id)});
        db.close();
    }

    public void printAllAttractions() {
        SQLiteDatabase db = dbHelper.getReadableDatabase();

        List<Attraction> attractions = getAllAttractions();

        if(!attractions.isEmpty()) {
            System.out.println("###########");
            for(Attraction a : attractions) {
                System.out.println(a.toString());
            }
            System.out.println("###########");
        }
        else {
            System.out.println("No rows found.");
        }

        db.close();
    }
}