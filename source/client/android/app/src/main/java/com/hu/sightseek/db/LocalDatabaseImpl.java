package com.hu.sightseek.db;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

public class LocalDatabaseImpl extends SQLiteOpenHelper {
    private static final String DATABASE_NAME = "activities.db";
    private static final int DATABASE_VERSION = 6;

    // Activity table
    public static final String ACTIVITIES_TABLE = "activities";
    public static final String ACTIVITIES_ID = "id";
    public static final String ACTIVITIES_NAME = "name";
    public static final String ACTIVITIES_CATEGORY = "category";
    public static final String ACTIVITIES_POLYLINE = "polyline";
    public static final String ACTIVITIES_STARTTIME = "starttime";
    public static final String ACTIVITIES_ENDTIME = "endtime";
    public static final String ACTIVITIES_ELAPSEDTIME = "elapsedtime";
    public static final String ACTIVITIES_DISTANCE = "distance";

    // Saved attractions table
    public static final String ATTRACTIONS_TABLE = "attractions";
    public static final String ATTRACTIONS_ID = "id";
    public static final String ATTRACTIONS_NAME = "name";
    public static final String ATTRACTIONS_PLACE = "place";
    public static final String ATTRACTIONS_STATUS = "status";

    private static final String ACTIVITIES_TABLE_CREATE =
            "CREATE TABLE " + ACTIVITIES_TABLE + " (" +
                    ACTIVITIES_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    ACTIVITIES_NAME + " TEXT NOT NULL, " +
                    ACTIVITIES_CATEGORY + " INTEGER NOT NULL, " +
                    ACTIVITIES_POLYLINE + " TEXT NOT NULL, " +
                    ACTIVITIES_STARTTIME + " TEXT NOT NULL, " +
                    ACTIVITIES_ENDTIME + " TEXT NOT NULL, " +
                    ACTIVITIES_ELAPSEDTIME + " REAL, " +
                    ACTIVITIES_DISTANCE + " REAL);";

    private static final String ATTRACTIONS_TABLE_CREATE =
            "CREATE TABLE " + ATTRACTIONS_TABLE + " (" +
                    ATTRACTIONS_ID + " BIGINT PRIMARY KEY, " +
                    ATTRACTIONS_NAME + " TEXT NOT NULL, " +
                    ATTRACTIONS_PLACE + " TEXT NOT NULL, " +
                    ATTRACTIONS_STATUS + " TEXT NOT NULL);";

    public LocalDatabaseImpl(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(ACTIVITIES_TABLE_CREATE);
        db.execSQL(ATTRACTIONS_TABLE_CREATE);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + ACTIVITIES_TABLE);
        db.execSQL("DROP TABLE IF EXISTS " + ATTRACTIONS_TABLE);
        onCreate(db);
    }
}
