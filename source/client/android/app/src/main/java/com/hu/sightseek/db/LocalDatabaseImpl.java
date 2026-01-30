package com.hu.sightseek.db;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

/** SQLiteOpenHelper implementation for managing local activity and idea storage */
public class LocalDatabaseImpl extends SQLiteOpenHelper {

    /** Database file name. */
    private static final String DATABASE_NAME = "activities.db";

    /** Schema version for migrations. */
    private static final int DATABASE_VERSION = 14;

    // Activity table
    /** Table storing recorded activities. */
    public static final String ACTIVITIES_TABLE = "activities";
    /** ID */
    public static final String ACTIVITIES_ID = "id";
    /** User ID, null if not present */
    public static final String ACTIVITIES_UID = "uid";
    /** Name */
    public static final String ACTIVITIES_NAME = "name";
    /** Travel category */
    public static final String ACTIVITIES_CATEGORY = "category";
    /** Route as an encoded polyline */
    public static final String ACTIVITIES_POLYLINE = "polyline";
    /** Start time (Format: YYYY-MM-DDTHH:MM:SS) */
    public static final String ACTIVITIES_STARTTIME = "starttime";
    /** Elapsed time in seconds */
    public static final String ACTIVITIES_ELAPSEDTIME = "elapsedtime";
    /** Distance in meters */
    public static final String ACTIVITIES_DISTANCE = "distance";
    /** Strava ID, -1 if activity is not imported */
    public static final String ACTIVITIES_STRAVAID = "stravaid";
    /** Vectorized data as WKB (Well-known binary) format */
    public static final String ACTIVITIES_VECTORIZEDDATA = "vectorizeddata";

    /** Table for storing Ideas (attractions) */
    public static final String IDEAS_TABLE = "attractions";
    /** ID */
    public static final String IDEAS_ID = "id";
    /** Name */
    public static final String IDEAS_NAME = "name";
    /** Place (city or country) */
    public static final String IDEAS_PLACE = "place";
    /** Latitude */
    public static final String IDEAS_LATITUDE = "latitude";
    /** Longitude */
    public static final String IDEAS_LONGITUDE = "longitude";
    /** Status */
    public static final String IDEAS_STATUS = "status";

    /** SQL statement for creating the Activities table */
    private static final String ACTIVITIES_TABLE_CREATE =
            "CREATE TABLE " + ACTIVITIES_TABLE + " (" +
                    ACTIVITIES_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    ACTIVITIES_UID + " TEXT, " +
                    ACTIVITIES_NAME + " TEXT NOT NULL, " +
                    ACTIVITIES_CATEGORY + " INTEGER NOT NULL, " +
                    ACTIVITIES_POLYLINE + " TEXT NOT NULL, " +
                    ACTIVITIES_STARTTIME + " TEXT NOT NULL, " +
                    ACTIVITIES_ELAPSEDTIME + " REAL, " +
                    ACTIVITIES_DISTANCE + " REAL, " +
                    ACTIVITIES_STRAVAID + " REAL, " +
                    ACTIVITIES_VECTORIZEDDATA + " BLOB);";

    /** SQL statement for creating the Ideas table */
    private static final String IDEAS_TABLE_CREATE =
            "CREATE TABLE " + IDEAS_TABLE + " (" +
                    IDEAS_ID + " BIGINT PRIMARY KEY, " +
                    IDEAS_NAME + " TEXT NOT NULL, " +
                    IDEAS_PLACE + " TEXT NOT NULL, " +
                    IDEAS_LATITUDE + " REAL NOT NULL, " +
                    IDEAS_LONGITUDE + " REAL NOT NULL, " +
                    IDEAS_STATUS + " TEXT NOT NULL);";

    /**
     * Constructor
     * @param context Context
     */
    public LocalDatabaseImpl(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    /**
     * Creates database tables on first install
     * @param db Writeable database instance
     */
    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(ACTIVITIES_TABLE_CREATE);
        db.execSQL(IDEAS_TABLE_CREATE);
    }

    /**
     * Handles database upgrades
     * @param db Writeable database instance
     * @param oldVersion Previous database version
     * @param newVersion New database version
     */
    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + ACTIVITIES_TABLE);
        db.execSQL("DROP TABLE IF EXISTS " + IDEAS_TABLE);
        onCreate(db);
    }
}