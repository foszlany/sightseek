package com.hu.sightseek.db;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

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

    public Cursor getAllActivities() {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        return db.query(
                LocalActivityDatabaseImpl.ACTIVITIES_TABLE,
                null,
                null,
                null,
                null,
                null,
                LocalActivityDatabaseImpl.ACTIVITIES_ENDTIME + " DESC"
        );
    }

    public void printAllActivities() {
        SQLiteDatabase db = dbHelper.getReadableDatabase();

        Cursor cursor = getAllActivities();

        if(cursor.moveToFirst()) {
            do {
                StringBuilder row = new StringBuilder();
                for(int i = 0; i < cursor.getColumnCount(); i++) {
                    row.append(cursor.getColumnName(i))
                            .append("=")
                            .append(cursor.getString(i))
                            .append("  ");
                }
                System.out.println(row);
            }
            while(cursor.moveToNext());
        }
        else {
            System.out.println("No rows found.");
        }

        cursor.close();
        db.close();
    }


    public void close() {
        dbHelper.close();
    }
}