package com.hu.sightseek.activity;

import static com.hu.sightseek.utils.SightseekFirebaseUtils.updateCellsInFirebase;
import static com.hu.sightseek.utils.SightseekGenericUtils.STRAVA_CLIENT_ID;
import static com.hu.sightseek.utils.SightseekGenericUtils.getVisitedCells;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.gms.maps.model.LatLng;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.maps.android.PolyUtil;
import com.hu.sightseek.BuildConfig;
import com.hu.sightseek.R;
import com.hu.sightseek.adapter.ConsoleAdapter;
import com.hu.sightseek.db.LocalDatabaseDAO;
import com.hu.sightseek.enums.TravelCategory;
import com.hu.sightseek.model.Activity;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.osmdroid.config.Configuration;

import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.TimeZone;
import java.util.concurrent.Executors;

import okhttp3.Callback;
import okhttp3.FormBody;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;

public class StravaImportActivity extends AppCompatActivity {
    private final int ACTIVITIES_PER_PAGE = 12;
    private RecyclerView consoleRecyclerView;
    private ConsoleAdapter consoleAdapter;

    private FirebaseAuth mAuth;
    private SharedPreferences prefs;
    private ArrayList<Activity> activities;
    private HashSet<Long> stravaIds;
    HashMap<String, Integer> visitedCells;
    private String importDate;
    private String tempImportDate;
    private String accessToken;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_strava_import);
        Configuration.getInstance().load(
                getApplicationContext(),
                PreferenceManager.getDefaultSharedPreferences(getApplicationContext())
        );
        Configuration.getInstance().setUserAgentValue(getPackageName());

        // Check if user is logged in
        mAuth = FirebaseAuth.getInstance();
        if(mAuth.getCurrentUser() == null) {
            runOnUiThread(() -> {
                startActivity(new Intent(this, BannerActivity.class));
                finish();
            });
            return;
        }

        // Add Menu
        Toolbar toolbar = findViewById(R.id.strava_topmenu);
        setSupportActionBar(toolbar);
        if(getSupportActionBar() != null) {
            getSupportActionBar().setDisplayShowTitleEnabled(false);
        }

        // Home button
        toolbar.setNavigationIcon(R.drawable.baseline_home_24);
        toolbar.setNavigationOnClickListener(v -> {
            Intent intent = new Intent(this, MainActivity.class);
            startActivity(intent);
        });

        prefs = getSharedPreferences("AppPrefs", Context.MODE_PRIVATE);
        activities = new ArrayList<>();
        stravaIds = new HashSet<>();
        visitedCells = new HashMap<>();

        consoleRecyclerView = findViewById(R.id.strava_console);
        consoleAdapter = new ConsoleAdapter();
        consoleRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        consoleRecyclerView.setAdapter(consoleAdapter);

        logIntoConsole("Progress will appear here.\n\n" +
                       "\"Import Latest\" fetches recent unimported activities.\n" +
                       "\"Import Missing\" fetches deleted activities too.\n");

        // Strava ids
        Executors.newSingleThreadExecutor().execute(() -> {
            LocalDatabaseDAO dao = new LocalDatabaseDAO(StravaImportActivity.this);
            stravaIds = dao.getAllStravaIds();
            dao.close();
        });

        // Get access token
        Uri uri = getIntent().getData();
        if(uri != null && uri.getQueryParameter("code") != null) {
            String code = uri.getQueryParameter("code");
            if(code == null) {
                finish();
                return;
            }

            RequestBody body = new FormBody.Builder()
                    .add("client_id", STRAVA_CLIENT_ID)
                    .add("client_secret", BuildConfig.STRAVA_API_KEY)
                    .add("code", code)
                    .add("grant_type", "authorization_code")
                    .build();

            OkHttpClient client = new OkHttpClient();
            Request request = new Request.Builder()
                    .url("https://www.strava.com/oauth/token")
                    .post(body)
                    .build();

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onResponse(@NonNull okhttp3.Call call, @NonNull okhttp3.Response response) {
                    if(!response.isSuccessful() || response.body() == null) {
                        onFailReturnToProfile("Failed to reach Strava. Please try again later.");
                    }
                    else {
                        runOnUiThread(() -> {
                            try {
                                String responseBody = response.body().string();
                                JSONObject json = new JSONObject(responseBody);

                                String uid = Objects.requireNonNull(mAuth.getUid());
                                DocumentReference userDocument = FirebaseFirestore.getInstance()
                                        .collection("users")
                                        .document(uid);

                                // Check if stravaid matches stored one
                                long stravaId = json.getJSONObject("athlete").getLong("id");
                                userDocument.get().addOnSuccessListener(documentSnapshot -> {
                                    if(!documentSnapshot.contains("stravaId")) {
                                        userDocument.update("stravaId", stravaId);
                                    }
                                    else {
                                        documentSnapshot.getLong("stravaId");
                                        Long storedStravaId = documentSnapshot.getLong("stravaId");
                                        if(storedStravaId != null && storedStravaId != stravaId) {
                                            onFailReturnToProfile("You have a different account linked!");
                                        }
                                    }
                                });

                                accessToken = json.getString("access_token");

                                // Last import date for faster queries
                                if(!prefs.contains("StravaLatestImportDate")) {
                                    importDate = json.getJSONObject("athlete").getString("created_at").replace("Z", "");
                                    prefs.edit().putString("StravaLatestImportDate", importDate).apply();
                                }
                                else {
                                    importDate = prefs.getString("StravaLatestImportDate", "");
                                }
                            }
                            catch(JSONException | IOException e) {
                                onFailReturnToProfile("Failed to reach Strava. Please try again later.");
                            }
                        });
                    }
                }

                @Override
                public void onFailure(@NonNull okhttp3.Call call, @NonNull IOException e) {
                    onFailReturnToProfile("Failed to reach Strava. Please try again later.");
                }
            });
        }

        else {
            finish();
        }
    }

    private void onFailReturnToProfile(String reason) {
        runOnUiThread(() ->
                Toast.makeText(StravaImportActivity.this, reason, Toast.LENGTH_LONG).show()
        );

        finish();
    }

    // Create top menubar
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu_top, menu);
        return true;
    }

    // Top menubar actions
    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();

        // Statistics
        if(id == R.id.topmenu_statistics) {
            Intent intent = new Intent(this, StatisticsActivity.class);
            startActivity(intent);
            return true;
        }

        // Profile
        if(id == R.id.topmenu_profile) {
            Intent intent = new Intent(this, ProfileActivity.class);
            startActivity(intent);
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onStart() {
        super.onStart();

        Button importLatestButton = findViewById(R.id.strava_importlatestbtn);
        importLatestButton.setOnClickListener(v -> {
            consoleAdapter.clearLogs();
            importLatest(1, "after");
        });

        Button importMissingButton = findViewById(R.id.strava_importmissingbtn);
        importMissingButton.setOnClickListener(v -> {
            consoleAdapter.clearLogs();
            importLatest(1, "before");
        });
    }

    public void importLatest(int page, String mode) {
        HttpUrl url = Objects.requireNonNull(HttpUrl.parse("https://www.strava.com/api/v3/athlete/activities"))
                .newBuilder()
                .addQueryParameter(mode, Long.toString("before".equals(mode) ? (System.currentTimeMillis() / 1000) : getUnixTimestamp(importDate)))
                .addQueryParameter("page", String.valueOf(page))
                .addQueryParameter("per_page", Integer.toString(ACTIVITIES_PER_PAGE))
                .build();

        OkHttpClient client = new OkHttpClient();
        Request request = new Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer " + accessToken)
                .get()
                .build();

        logIntoConsole("Fetching page " + page + "...");

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onResponse(@NonNull okhttp3.Call call, @NonNull okhttp3.Response response) {
                if(response.code() == 429) {
                    logIntoConsole("The API has reached its rate limit. Please try again later.");
                }
                if(!response.isSuccessful() || response.body() == null) {
                    logIntoConsole("An unknown error has occurred. Please try again later.");
                }
                else {
                    runOnUiThread(() -> {
                        try {
                            String responseBody = response.body().string();
                            JSONArray jsonActivities = new JSONArray(responseBody);

                            // End query chain
                            if(jsonActivities.length() == 0) {
                                if(page == 1) {
                                    consoleAdapter.clearLogs();
                                    logIntoConsole("Nothing new was found.\n" +
                                                   "Use \"Import missing\" if you wish to restore deleted activities.\n");
                                    return;
                                }

                                logIntoConsole("Nothing else was found!\n\n" +
                                               "Saving to database...");

                                LocalDatabaseDAO dao = new LocalDatabaseDAO(StravaImportActivity.this);
                                dao.addActivities(activities);
                                dao.close();

                                updateCellsInFirebase(mAuth, visitedCells);

                                if("after".equals(mode)) {
                                    prefs.edit().putString("StravaLatestImportDate", tempImportDate).apply();
                                    importDate = tempImportDate;
                                }

                                logIntoConsole("Importing has been completed.\n" +
                                                activities.size() + " activities were fetched.");

                                return;
                            }

                            for(int i = 0; i < jsonActivities.length(); i++) {
                                JSONObject jsonActivity = jsonActivities.getJSONObject(i);

                                String name = jsonActivity.getString("name");
                                long stravaId = jsonActivity.getLong("id");
                                if(stravaIds.contains(stravaId)) {
                                    logIntoConsole("Activity " + name + " already exists. (" + (i + 1) + "/" + jsonActivities.length() + ")");

                                    if(i + 1 == jsonActivities.length()) {
                                        logIntoConsole("\n");
                                    }
                                    continue;
                                }

                                TravelCategory category = getCategoryFromStravaType(jsonActivity.getString("sport_type"));
                                String polyline = jsonActivity.getJSONObject("map").getString("summary_polyline");
                                String startDate = jsonActivity.getString("start_date").replace("Z", "");
                                int elapsedTime = jsonActivity.getInt("moving_time");
                                int distance = jsonActivity.getInt("distance");

                                Activity a = new Activity(0, name, category.getIndex(), polyline, startDate, elapsedTime, distance, stravaId);
                                activities.add(a);

                                List<LatLng> pointList = PolyUtil.decode(polyline);
                                HashMap<String, Integer> newCells = getVisitedCells(pointList);

                                for(HashMap.Entry<String, Integer> entry : newCells.entrySet()) {
                                    String key = entry.getKey();
                                    int newCount = entry.getValue();

                                    Integer count = visitedCells.get(key);
                                    if(count == null) {
                                        count = 0;
                                    }
                                    visitedCells.put(key, count + newCount);
                                }


                                logIntoConsole("Fetched " + name + " (" + (i + 1) + "/" + jsonActivities.length() + ")");

                                if(i + 1 == jsonActivities.length()) {
                                    tempImportDate = startDate;
                                    logIntoConsole("\n");
                                }
                            }

                            importLatest(page + 1, mode);
                        }
                        catch(JSONException | IOException e) {
                            logIntoConsole("An unknown error has occurred. Nothing was saved. Please try again later.");
                        }
                    });
                }
            }

            @Override
            public void onFailure(@NonNull okhttp3.Call call, @NonNull IOException e) {
                onFailReturnToProfile("Failed to reach Strava. Please try again later.");
            }
        });
    }

    private long getUnixTimestamp(String dateStr) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US);
        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));

        try {
            Date date = sdf.parse(dateStr);
            return date == null ? 0 : date.getTime() / 1000;
        }
        catch(ParseException e) {
            return 0;
        }
    }

    private TravelCategory getCategoryFromStravaType(String type) {
        if(type == null) {
            return TravelCategory.INVALID;
        }

        switch(type) {
            case "Run":
            case "TrailRun":
            case "Walk":
            case "Hike":
            case "Swim":
            case "Snowshoe":
            case "Wheelchair":
            case "VirtualRun":
            case "VirtualRow":
            case "Rowing":
            case "StairStepper":
            case "Elliptical":
                return TravelCategory.LOCOMOTOR;

            case "InlineSkate":
            case "RollerSki":
            case "NordicSki":
            case "BackcountrySki":
            case "AlpineSki":
            case "IceSkate":
            case "Ride":
            case "GravelRide":
            case "MountainBikeRide":
            case "EBikeRide":
            case "EMountainBikeRide":
            case "Velomobile":
            case "Handcycle":
            case "Skateboard":
            case "VirtualRide":
            case "Canoeing":
            case "Kayaking":
            case "StandUpPaddling":
            case "Kitesurf":
            case "Windsurf":
            case "Sail":
            case "Surfing":
                return TravelCategory.MICROMOBILITY;

            default:
                return TravelCategory.OTHER;
        }
    }

    private void logIntoConsole(String message) {
        consoleAdapter.addLog(message);
        consoleRecyclerView.scrollToPosition(consoleAdapter.getItemCount() - 1);
    }
}