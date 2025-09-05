package com.hu.sightseek.db;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.hu.sightseek.Activity;

import java.util.ArrayList;
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
                String name = cursor.getString(cursor.getColumnIndexOrThrow("name"));
                int categoryIndex = cursor.getInt(cursor.getColumnIndexOrThrow("category"));
                String polyline = cursor.getString(cursor.getColumnIndexOrThrow("polyline"));
                String starttime = cursor.getString(cursor.getColumnIndexOrThrow("starttime"));
                String endtime = cursor.getString(cursor.getColumnIndexOrThrow("endtime"));
                double elapsedtime = cursor.getDouble(cursor.getColumnIndexOrThrow("elapsedtime"));
                double distance = cursor.getDouble(cursor.getColumnIndexOrThrow("distance"));

                activities.add(new Activity(id, name, categoryIndex, polyline, starttime, endtime, elapsedtime, distance));
            } while(cursor.moveToNext());

            cursor.close();
        }

        cursor.close();

        return activities;
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