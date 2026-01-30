package com.hu.sightseek.db;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import androidx.annotation.NonNull;

import com.google.android.gms.maps.model.LatLng;
import com.google.firebase.auth.FirebaseAuth;
import com.google.maps.android.PolyUtil;
import com.hu.sightseek.enums.SavedIdeaStatus;
import com.hu.sightseek.enums.TravelCategory;
import com.hu.sightseek.helper.WKConverter;
import com.hu.sightseek.model.Activity;
import com.hu.sightseek.model.Idea;
import com.hu.sightseek.provider.StatisticsProvider;
import com.hu.sightseek.util.SpatialUtils;

import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.io.ParseException;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.overlay.Polyline;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Provides data access from the local database */
public class LocalDatabaseDAO {
    /** Implementation */
    private final LocalDatabaseImpl dbHelper;

    /**
     * Constructor
     * @param context Context
     */
    public LocalDatabaseDAO(Context context) {
        dbHelper = new LocalDatabaseImpl(context);
    }

    /**
     * Closes database
     */
    public void close() {
        dbHelper.close();
    }

    /* ############### ACTIVITIES ############### */

    /**
     * Adds an Activity to the database
     * @param activity Activity
     * @return ID of the inserted Activity
     */
    public long addActivity(Activity activity) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues values = new ContentValues();

        values.put(LocalDatabaseImpl.ACTIVITIES_NAME, activity.getName());
        values.put(LocalDatabaseImpl.ACTIVITIES_UID, activity.getUid());
        values.put(LocalDatabaseImpl.ACTIVITIES_CATEGORY, activity.getCategory().getIndex());
        values.put(LocalDatabaseImpl.ACTIVITIES_POLYLINE, activity.getPolyline());
        values.put(LocalDatabaseImpl.ACTIVITIES_STARTTIME, activity.getStartTime());
        values.put(LocalDatabaseImpl.ACTIVITIES_ELAPSEDTIME, activity.getElapsedTime());
        values.put(LocalDatabaseImpl.ACTIVITIES_DISTANCE, activity.getDistance());
        values.put(LocalDatabaseImpl.ACTIVITIES_STRAVAID, activity.getStravaId());
        values.put(LocalDatabaseImpl.ACTIVITIES_VECTORIZEDDATA, activity.getVectorizedData());

        long id = db.insert(LocalDatabaseImpl.ACTIVITIES_TABLE, null, values);
        db.close();

        return id;
    }

    /**
     * Adds a list of Activity to the database
     * @param activities List of Activity objects
     */
    public void addActivities(List<Activity> activities) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();

        for(Activity activity : activities) {
            ContentValues values = new ContentValues();
            values.put(LocalDatabaseImpl.ACTIVITIES_NAME, activity.getName());
            values.put(LocalDatabaseImpl.ACTIVITIES_UID, activity.getUid());
            values.put(LocalDatabaseImpl.ACTIVITIES_CATEGORY, activity.getCategory().getIndex());
            values.put(LocalDatabaseImpl.ACTIVITIES_POLYLINE, activity.getPolyline());
            values.put(LocalDatabaseImpl.ACTIVITIES_STARTTIME, activity.getStartTime());
            values.put(LocalDatabaseImpl.ACTIVITIES_ELAPSEDTIME, activity.getElapsedTime());
            values.put(LocalDatabaseImpl.ACTIVITIES_DISTANCE, activity.getDistance());
            values.put(LocalDatabaseImpl.ACTIVITIES_STRAVAID, activity.getStravaId());
            values.put(LocalDatabaseImpl.ACTIVITIES_VECTORIZEDDATA, activity.getVectorizedData());

            db.insert(LocalDatabaseImpl.ACTIVITIES_TABLE, null, values);
        }

        db.close();
    }

    /**
     * Gets the following statistics: total distance, total time, longest distance, longest time, activity count, imported activity count
     * @param category Category to get the statistics of. When "Invalid', the data will include all activities.
     * @return Map containing the statistics
     */
    public HashMap<String, Serializable> getBaseStatistics(TravelCategory category) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();

        String sql;
        if(category == TravelCategory.INVALID) {
            sql =
                "SELECT " +
                "IFNULL(SUM(" + LocalDatabaseImpl.ACTIVITIES_DISTANCE + "), 0) AS " + StatisticsProvider.STATISTICS_KEY_TOTAL_DISTANCE + ", " +
                "IFNULL(SUM(" + LocalDatabaseImpl.ACTIVITIES_ELAPSEDTIME + "), 0) AS " + StatisticsProvider.STATISTICS_KEY_TOTAL_TIME + ", " +
                "IFNULL(MAX(" + LocalDatabaseImpl.ACTIVITIES_DISTANCE + "), 0) AS " + StatisticsProvider.STATISTICS_KEY_LONGEST_DISTANCE + ", " +
                "IFNULL(MAX(" + LocalDatabaseImpl.ACTIVITIES_ELAPSEDTIME + "), 0) AS " + StatisticsProvider.STATISTICS_KEY_LONGEST_TIME + ", " +
                "COUNT(*) AS " + StatisticsProvider.STATISTICS_KEY_ACTIVITY_COUNT + ", " +
                "SUM(CASE WHEN " + LocalDatabaseImpl.ACTIVITIES_STRAVAID + " != -1 THEN 1 ELSE 0 END) AS " + StatisticsProvider.STATISTICS_KEY_IMPORTED_COUNT + " " +
                "FROM " + LocalDatabaseImpl.ACTIVITIES_TABLE + " " +
                "WHERE " + getUidFilter();
        }
        else {
            sql =
                "SELECT " +
                "IFNULL(SUM(" + LocalDatabaseImpl.ACTIVITIES_DISTANCE + "), 0) AS " + StatisticsProvider.STATISTICS_KEY_TOTAL_DISTANCE + ", " +
                "IFNULL(SUM(" + LocalDatabaseImpl.ACTIVITIES_ELAPSEDTIME + "), 0) AS " + StatisticsProvider.STATISTICS_KEY_TOTAL_TIME + ", " +
                "IFNULL(MAX(" + LocalDatabaseImpl.ACTIVITIES_DISTANCE + "), 0) AS " + StatisticsProvider.STATISTICS_KEY_LONGEST_DISTANCE + ", " +
                "IFNULL(MAX(" + LocalDatabaseImpl.ACTIVITIES_ELAPSEDTIME + "), 0) AS " + StatisticsProvider.STATISTICS_KEY_LONGEST_TIME + ", " +
                "COUNT(*) AS " + StatisticsProvider.STATISTICS_KEY_ACTIVITY_COUNT + ", " +
                "SUM(CASE WHEN " + LocalDatabaseImpl.ACTIVITIES_STRAVAID + " != -1 THEN 1 ELSE 0 END) AS " + StatisticsProvider.STATISTICS_KEY_IMPORTED_COUNT + " " +
                "FROM " + LocalDatabaseImpl.ACTIVITIES_TABLE + " " +
                "WHERE " + LocalDatabaseImpl.ACTIVITIES_CATEGORY + " = ? " +
                "AND " + getUidFilter();
        }

        Cursor cursor = db.rawQuery(sql, category == TravelCategory.INVALID ? null : new String[]{String.valueOf(category.getIndex())});

        HashMap<String, Serializable> res = new HashMap<>();
        if(cursor.moveToFirst()) {
            res.put(StatisticsProvider.STATISTICS_KEY_TOTAL_DISTANCE, cursor.getDouble(cursor.getColumnIndexOrThrow(StatisticsProvider.STATISTICS_KEY_TOTAL_DISTANCE)));
            res.put(StatisticsProvider.STATISTICS_KEY_TOTAL_TIME, cursor.getDouble(cursor.getColumnIndexOrThrow(StatisticsProvider.STATISTICS_KEY_TOTAL_TIME)));
            res.put(StatisticsProvider.STATISTICS_KEY_LONGEST_DISTANCE, cursor.getDouble(cursor.getColumnIndexOrThrow(StatisticsProvider.STATISTICS_KEY_LONGEST_DISTANCE)));
            res.put(StatisticsProvider.STATISTICS_KEY_LONGEST_TIME, cursor.getDouble(cursor.getColumnIndexOrThrow(StatisticsProvider.STATISTICS_KEY_LONGEST_TIME)));
            res.put(StatisticsProvider.STATISTICS_KEY_ACTIVITY_COUNT, cursor.getDouble(cursor.getColumnIndexOrThrow(StatisticsProvider.STATISTICS_KEY_ACTIVITY_COUNT)));
            res.put(StatisticsProvider.STATISTICS_KEY_IMPORTED_COUNT, cursor.getDouble(cursor.getColumnIndexOrThrow(StatisticsProvider.STATISTICS_KEY_IMPORTED_COUNT)));
        }

        cursor.close();
        db.close();

        return res;
    }

    /**
     * Gets the following statistics regardless of category: total distance, total time, longest distance, longest time, activity count, imported activity count
     * @return Map containing the statistics
     */
    public HashMap<String, Serializable> getBaseStatistics() {
        return getBaseStatistics(TravelCategory.INVALID);
    }

    /**
     * Gets the monthly distances
     * @return Map containing the month as an integer (1-12) and their associated distance values
     */
    public HashMap<Integer, Double> getMonthlyTotalDistance() {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        HashMap<Integer, Double> data = new HashMap<>();
        for(int i = 1; i <= 12; i++) {
            data.put(i, 0d);
        }

        String sql =
                "SELECT strftime('%m', " + LocalDatabaseImpl.ACTIVITIES_STARTTIME + ") AS month, " +
                "SUM (" + LocalDatabaseImpl.ACTIVITIES_DISTANCE + ") AS total_distance " +
                "FROM " + LocalDatabaseImpl.ACTIVITIES_TABLE + " " +
                "WHERE " + getUidFilter() + " " +
                "GROUP BY month" + " " +
                "ORDER BY month ASC";

        Cursor cursor = db.rawQuery(sql, null);

        while(cursor.moveToNext()) {
            int month = Integer.parseInt(cursor.getString(cursor.getColumnIndexOrThrow("month")));
            double distance = cursor.getDouble(cursor.getColumnIndexOrThrow("total_distance")) / 1000.0;

            data.put(month, distance);
        }
        cursor.close();

        return data;
    }

    /**
     * Gets the most common used TravelCategory
     * @return Most used TravelCategory
     */
    public TravelCategory getMainTravelCategory() {
        SQLiteDatabase db = dbHelper.getReadableDatabase();

        String sql =
                "SELECT " + LocalDatabaseImpl.ACTIVITIES_CATEGORY + " AS category, " +
                "COUNT(*) AS occurrences " +
                "FROM " + LocalDatabaseImpl.ACTIVITIES_TABLE + " " +
                "WHERE " + getUidFilter() + " " +
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

    /**
     * Gets an Activity
     * @param id ID of the Activity
     * @return Activity
     */
    public Activity getActivity(int id) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();

        String sql =
                "SELECT * FROM " + LocalDatabaseImpl.ACTIVITIES_TABLE + " " +
                "WHERE " + LocalDatabaseImpl.ACTIVITIES_ID + "= ?";

        Cursor cursor = db.rawQuery(sql, new String[]{String.valueOf(id)});

        if(cursor.moveToFirst()) {
            String name = cursor.getString(cursor.getColumnIndexOrThrow(LocalDatabaseImpl.ACTIVITIES_NAME));
            String uid = cursor.getString(cursor.getColumnIndexOrThrow(LocalDatabaseImpl.ACTIVITIES_UID));
            int categoryIndex = cursor.getInt(cursor.getColumnIndexOrThrow(LocalDatabaseImpl.ACTIVITIES_CATEGORY));
            String polyline = cursor.getString(cursor.getColumnIndexOrThrow(LocalDatabaseImpl.ACTIVITIES_POLYLINE));
            String starttime = cursor.getString(cursor.getColumnIndexOrThrow(LocalDatabaseImpl.ACTIVITIES_STARTTIME));
            double elapsedtime = cursor.getDouble(cursor.getColumnIndexOrThrow(LocalDatabaseImpl.ACTIVITIES_ELAPSEDTIME));
            double distance = cursor.getDouble(cursor.getColumnIndexOrThrow(LocalDatabaseImpl.ACTIVITIES_DISTANCE));
            long stravaId = cursor.getLong(cursor.getColumnIndexOrThrow(LocalDatabaseImpl.ACTIVITIES_STRAVAID));
            byte[] vectorizedData = cursor.getBlob(cursor.getColumnIndexOrThrow(LocalDatabaseImpl.ACTIVITIES_VECTORIZEDDATA));

            cursor.close();
            db.close();

            return new Activity(id, uid, name, TravelCategory.values()[categoryIndex], polyline, starttime, elapsedtime, distance, stravaId, vectorizedData);
        }
        else {
            cursor.close();
            db.close();

            return null;
        }
    }

    /**
     * Gets all activities
     * @return List of Activity objects
     */
    public List<Activity> getAllActivities() {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        List<Activity> activities = new ArrayList<>();

        String sql =
                "SELECT * FROM " + LocalDatabaseImpl.ACTIVITIES_TABLE + " " +
                "WHERE " + getUidFilter() + " " +
                "ORDER BY " + LocalDatabaseImpl.ACTIVITIES_STARTTIME + " DESC";

        Cursor cursor = db.rawQuery(sql, null);

        if(cursor.moveToFirst()) {
            do {
                int id = cursor.getInt(cursor.getColumnIndexOrThrow(LocalDatabaseImpl.ACTIVITIES_ID));
                String uid = cursor.getString(cursor.getColumnIndexOrThrow(LocalDatabaseImpl.ACTIVITIES_UID));
                String name = cursor.getString(cursor.getColumnIndexOrThrow(LocalDatabaseImpl.ACTIVITIES_NAME));
                int categoryIndex = cursor.getInt(cursor.getColumnIndexOrThrow(LocalDatabaseImpl.ACTIVITIES_CATEGORY));
                String polyline = cursor.getString(cursor.getColumnIndexOrThrow(LocalDatabaseImpl.ACTIVITIES_POLYLINE));
                String starttime = cursor.getString(cursor.getColumnIndexOrThrow(LocalDatabaseImpl.ACTIVITIES_STARTTIME));
                double elapsedtime = cursor.getDouble(cursor.getColumnIndexOrThrow(LocalDatabaseImpl.ACTIVITIES_ELAPSEDTIME));
                double distance = cursor.getDouble(cursor.getColumnIndexOrThrow(LocalDatabaseImpl.ACTIVITIES_DISTANCE));
                long stravaId = cursor.getLong(cursor.getColumnIndexOrThrow(LocalDatabaseImpl.ACTIVITIES_STRAVAID));
                byte[] vectorizedData = cursor.getBlob(cursor.getColumnIndexOrThrow(LocalDatabaseImpl.ACTIVITIES_VECTORIZEDDATA));

                activities.add(new Activity(id, uid, name, TravelCategory.values()[categoryIndex], polyline, starttime, elapsedtime, distance, stravaId, vectorizedData));
            } while(cursor.moveToNext());
        }

        cursor.close();
        db.close();

        return activities;
    }

    /**
     * Deletes an Activity
     * @param id ID of the Activity
     */
    public void deleteActivity(int id) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        db.delete(LocalDatabaseImpl.ACTIVITIES_TABLE, LocalDatabaseImpl.ACTIVITIES_ID + " = ?", new String[]{String.valueOf(id)});
        db.close();
    }

    /**
     * Gets all Strava IDs from the imported activities
     * @return Set of Strava IDs
     */
    public HashSet<Long> getAllStravaIds() {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        HashSet<Long> ids = new HashSet<>();

        Cursor cursor = db.query(
                LocalDatabaseImpl.ACTIVITIES_TABLE,
                new String[]{LocalDatabaseImpl.ACTIVITIES_STRAVAID},
                null,
                null,
                null,
                null,
                null
        );

        if(cursor.moveToFirst()) {
            do {
                long id = cursor.getLong(cursor.getColumnIndexOrThrow(LocalDatabaseImpl.ACTIVITIES_STRAVAID));
                ids.add(id);
            } while(cursor.moveToNext());
        }

        cursor.close();
        db.close();

        return ids;
    }

    /**
     * Gets all route points
     * @return List of points
     */
    public List<LatLng> getAllPoints() {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        List<LatLng> polylines = new ArrayList<>();

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

    /**
     * Gets all imported route points
     * @return List of imported points
     */
    public List<GeoPoint> getAllImportedPoints() {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        List<GeoPoint> points = new ArrayList<>();

        String sql =
                "SELECT " + LocalDatabaseImpl.ACTIVITIES_POLYLINE +
                " FROM " + LocalDatabaseImpl.ACTIVITIES_TABLE +
                " WHERE " + LocalDatabaseImpl.ACTIVITIES_STRAVAID + " != -1";

        Cursor cursor = db.rawQuery(sql, null);

        if(cursor.moveToFirst()) {
            do {
                String polylineString = cursor.getString(cursor.getColumnIndexOrThrow(LocalDatabaseImpl.ACTIVITIES_POLYLINE));
                List<GeoPoint> geoPoints;

                geoPoints = SpatialUtils.decode(polylineString);
                points.addAll(geoPoints);
            } while(cursor.moveToNext());
        }

        cursor.close();
        db.close();

        return points;
    }

    /**
     * Gets all imported activities
     * @return List of imported activities
     */
    public List<Activity> getAllImportedActivities() {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        List<Activity> importedActivities = new ArrayList<>();

        Cursor cursor = db.query(
                LocalDatabaseImpl.ACTIVITIES_TABLE,
                null,
                LocalDatabaseImpl.ACTIVITIES_STRAVAID + "!= -1",
                null,
                null,
                null,
                null
        );

        if(cursor.moveToFirst()) {
            do {
                int id = cursor.getInt(cursor.getColumnIndexOrThrow(LocalDatabaseImpl.ACTIVITIES_ID));
                String uid = cursor.getString(cursor.getColumnIndexOrThrow(LocalDatabaseImpl.ACTIVITIES_UID));
                String name = cursor.getString(cursor.getColumnIndexOrThrow(LocalDatabaseImpl.ACTIVITIES_NAME));
                int categoryIndex = cursor.getInt(cursor.getColumnIndexOrThrow(LocalDatabaseImpl.ACTIVITIES_CATEGORY));
                String polyline = cursor.getString(cursor.getColumnIndexOrThrow(LocalDatabaseImpl.ACTIVITIES_POLYLINE));
                String starttime = cursor.getString(cursor.getColumnIndexOrThrow(LocalDatabaseImpl.ACTIVITIES_STARTTIME));
                double elapsedtime = cursor.getDouble(cursor.getColumnIndexOrThrow(LocalDatabaseImpl.ACTIVITIES_ELAPSEDTIME));
                double distance = cursor.getDouble(cursor.getColumnIndexOrThrow(LocalDatabaseImpl.ACTIVITIES_DISTANCE));
                long stravaId = cursor.getLong(cursor.getColumnIndexOrThrow(LocalDatabaseImpl.ACTIVITIES_STRAVAID));
                byte[] vectorizedData = cursor.getBlob(cursor.getColumnIndexOrThrow(LocalDatabaseImpl.ACTIVITIES_VECTORIZEDDATA));

                importedActivities.add(new Activity(id, uid, name, TravelCategory.values()[categoryIndex], polyline, starttime, elapsedtime, distance, stravaId, vectorizedData));
            } while(cursor.moveToNext());
        }

        cursor.close();
        db.close();

        return importedActivities;
    }

    /**
     * Gets all route Polylines
     * @param tolerance When positive, the polylines will be simplified by tolerance meters
     * @return List of Polyline objects
     */
    public List<Polyline> getAllPolylines(int tolerance) {
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

    /**
     * Gets all vectorized roads
     * @param ignoredActivity ID of the Activity to ignore
     * @return List of vectorized roads
     */
    public List<Geometry> getAllVectorizedRoads(int ignoredActivity) {
        return getAllVectorizedRoads(ignoredActivity, null);
    }

    /**
     * Gets all vectorized roads except a set of activities
     * @param ignoredActivities Set of Activity IDs to ignore
     * @return List of vectorized roads
     */

    public List<Geometry> getAllVectorizedRoads(Set<Integer> ignoredActivities) {
        if(ignoredActivities == null || ignoredActivities.isEmpty()) {
            return getAllVectorizedRoads(-1, null);
        }

        return getAllVectorizedRoads(-1, ignoredActivities);
    }

    /**
     * Gets all vectorized roads except one
     * @param ignoredActivity ID of the activity to ignore, set to a negative number to ignore
     * @param ignoredActivities Set of Activity IDs to ignore, set to null to ignore
     * @return List of vectorized roads
     */
    private List<Geometry> getAllVectorizedRoads(long ignoredActivity, Set<Integer> ignoredActivities) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        ArrayList<Geometry> roads = new ArrayList<>();

        Cursor cursor = db.query(
                LocalDatabaseImpl.ACTIVITIES_TABLE,
                new String[]{
                        LocalDatabaseImpl.ACTIVITIES_ID,
                        LocalDatabaseImpl.ACTIVITIES_VECTORIZEDDATA
                },
                null,
                null,
                null,
                null,
                null
        );

        if(cursor.moveToFirst()) {
            do {
                try {
                    int id = cursor.getInt(cursor.getColumnIndexOrThrow(LocalDatabaseImpl.ACTIVITIES_ID));
                    byte[] vectorizedDataBlob = cursor.getBlob(cursor.getColumnIndexOrThrow(LocalDatabaseImpl.ACTIVITIES_VECTORIZEDDATA));
                    if(vectorizedDataBlob == null || vectorizedDataBlob.length == 0 || id == ignoredActivity || (ignoredActivities != null && ignoredActivities.contains(id))) {
                        continue;
                    }

                    Geometry vectorizedDataGeometry = WKConverter.convertWKBToGeometry(vectorizedDataBlob);
                    roads.add(vectorizedDataGeometry);
                }
                catch(ParseException e) {
                    throw new RuntimeException(e);
                }
            } while(cursor.moveToNext());
        }

        cursor.close();
        db.close();

        return roads;
    }

    /**
     * Deletes all imported Activities
     */
    public void deleteImportedActivities() {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        db.delete(LocalDatabaseImpl.ACTIVITIES_TABLE, LocalDatabaseImpl.ACTIVITIES_STRAVAID + " != -1", null);
        db.close();
    }

    /**
     * Prints all Activities for debug purposes
     */
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

    /* ############### IDEAS ############### */

    /**
     * Gets all Ideas
     * @return List of Idea objects
     */
    public List<Idea> getAllIdeas() {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        List<Idea> ideas = new ArrayList<>();

        Cursor cursor = db.query(
                LocalDatabaseImpl.IDEAS_TABLE,
                null,
                null,
                null,
                null,
                null,
                LocalDatabaseImpl.IDEAS_STATUS + " ASC"
        );

        if(cursor.moveToFirst()) {
            do {
                long id = cursor.getLong(cursor.getColumnIndexOrThrow(LocalDatabaseImpl.IDEAS_ID));
                String name = cursor.getString(cursor.getColumnIndexOrThrow(LocalDatabaseImpl.IDEAS_NAME));
                String place = cursor.getString(cursor.getColumnIndexOrThrow(LocalDatabaseImpl.IDEAS_PLACE));
                double latitude = cursor.getDouble(cursor.getColumnIndexOrThrow(LocalDatabaseImpl.IDEAS_LATITUDE));
                double longitude = cursor.getDouble(cursor.getColumnIndexOrThrow(LocalDatabaseImpl.IDEAS_LONGITUDE));
                int status = cursor.getInt(cursor.getColumnIndexOrThrow(LocalDatabaseImpl.IDEAS_STATUS));

                ideas.add(new Idea(id, name, place, latitude, longitude, SavedIdeaStatus.values()[status]));
            } while(cursor.moveToNext());
        }

        cursor.close();
        db.close();

        return ideas;
    }

    /**
     * Gets all Ideas with Saved status
     * @return List of Idea objects
     */
    public List<Idea> getSavedIdeas() {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        ArrayList<Idea> ideas = new ArrayList<>();

        Cursor cursor = db.query(
                LocalDatabaseImpl.IDEAS_TABLE,
                null,
                LocalDatabaseImpl.IDEAS_STATUS + " = ?",
                new String[]{String.valueOf(SavedIdeaStatus.SAVED.getIndex())},
                null,
                null,
                LocalDatabaseImpl.IDEAS_ID + " DESC"
        );

        if(cursor.moveToFirst()) {
            do {
                long id = cursor.getLong(cursor.getColumnIndexOrThrow(LocalDatabaseImpl.IDEAS_ID));
                String name = cursor.getString(cursor.getColumnIndexOrThrow(LocalDatabaseImpl.IDEAS_NAME));
                String place = cursor.getString(cursor.getColumnIndexOrThrow(LocalDatabaseImpl.IDEAS_PLACE));
                double latitude = cursor.getDouble(cursor.getColumnIndexOrThrow(LocalDatabaseImpl.IDEAS_LATITUDE));
                double longitude = cursor.getDouble(cursor.getColumnIndexOrThrow(LocalDatabaseImpl.IDEAS_LONGITUDE));
                int status = cursor.getInt(cursor.getColumnIndexOrThrow(LocalDatabaseImpl.IDEAS_STATUS));

                ideas.add(new Idea(id, name, place, latitude, longitude, SavedIdeaStatus.values()[status]));
            } while(cursor.moveToNext());
        }

        cursor.close();
        db.close();

        return ideas;
    }

    /**
     * Adds an Idea to the database
     * @param idea Idea
     */
    public void addIdea(Idea idea) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues values = new ContentValues();

        values.put(LocalDatabaseImpl.IDEAS_ID, idea.getId());
        values.put(LocalDatabaseImpl.IDEAS_NAME, idea.getName());
        values.put(LocalDatabaseImpl.IDEAS_PLACE, idea.getPlace());
        values.put(LocalDatabaseImpl.IDEAS_LATITUDE, idea.getLatitude());
        values.put(LocalDatabaseImpl.IDEAS_LONGITUDE, idea.getLongitude());
        values.put(LocalDatabaseImpl.IDEAS_STATUS, idea.getStatus().getIndex());

        db.insert(LocalDatabaseImpl.IDEAS_TABLE, null, values);
        db.close();
    }

    /**
     * Updates the status of an Idea
     * @param id ID of the Idea
     * @param newStatus New status
     */
    public void updateIdeaStatus(long id, SavedIdeaStatus newStatus) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues values = new ContentValues();

        values.put(LocalDatabaseImpl.IDEAS_STATUS, newStatus.getIndex());

        db.update(
                LocalDatabaseImpl.IDEAS_TABLE,
                values,
                LocalDatabaseImpl.IDEAS_ID + " = ?",
                new String[]{String.valueOf(id)}
        );

        db.close();
    }

    /**
     * Gets all Idea ids
     * @return Set of ids
     */
    public HashSet<Long> getIdeaIds() {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        HashSet<Long> ids = new HashSet<>();

        Cursor cursor = db.query(
                LocalDatabaseImpl.IDEAS_TABLE,
                new String[]{LocalDatabaseImpl.IDEAS_ID},
                null,
                null,
                null,
                null,
                null
        );

        if(cursor.moveToFirst()) {
            do {
                long id = cursor.getLong(cursor.getColumnIndexOrThrow(LocalDatabaseImpl.IDEAS_ID));
                ids.add(id);
            } while(cursor.moveToNext());
        }

        cursor.close();
        db.close();

        return ids;
    }

    /**
     * Deletes an Idea
     * @param id ID of the Idea
     */
    public void deleteIdea(long id) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        db.delete(LocalDatabaseImpl.IDEAS_TABLE, "id = ?", new String[]{String.valueOf(id)});
        db.close();
    }

    /**
     * Prints all Ideas for debug purposes
     */
    public void printAllIdeas() {
        SQLiteDatabase db = dbHelper.getReadableDatabase();

        List<Idea> ideas = getAllIdeas();

        if(!ideas.isEmpty()) {
            System.out.println("###########");
            for(Idea a : ideas) {
                System.out.println(a.toString());
            }
            System.out.println("###########");
        }
        else {
            System.out.println("No rows found.");
        }

        db.close();
    }

    @NonNull
    private static String getUidFilter() {
        String currentUid = FirebaseAuth.getInstance().getUid();
        String uidFilter;
        if(currentUid == null) {
            uidFilter = LocalDatabaseImpl.ACTIVITIES_UID + " IS NULL";
        }
        else {
            uidFilter = LocalDatabaseImpl.ACTIVITIES_UID + " = '" + currentUid + "'";
        }
        return uidFilter;
    }
}