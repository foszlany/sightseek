package com.hu.sightseek.activity;

import static com.hu.sightseek.utils.SightseekGenericUtils.STRAVA_CLIENT_ID;

import android.content.Intent;
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

import com.google.firebase.auth.FirebaseAuth;
import com.hu.sightseek.R;
import com.hu.sightseek.enums.TravelCategory;
import com.hu.sightseek.model.Activity;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.osmdroid.config.Configuration;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Objects;

import okhttp3.Callback;
import okhttp3.FormBody;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;

public class StravaImportActivity extends AppCompatActivity {
    private ArrayList<Activity> activities;
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
        FirebaseAuth mAuth = FirebaseAuth.getInstance();
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

        activities = new ArrayList<>();

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
                    .add("client_secret", "no secret for u")
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
                        onFailReturnToProfile();
                    }
                    else {
                        runOnUiThread(() -> {
                            try {
                                String responseBody = response.body().string();
                                JSONObject json = new JSONObject(responseBody);

                                accessToken = json.getString("access_token");
                            }
                            catch(JSONException | IOException e) {
                                onFailReturnToProfile();
                            }
                        });
                    }
                }

                @Override
                public void onFailure(@NonNull okhttp3.Call call, @NonNull IOException e) {
                    onFailReturnToProfile();
                }
            });
        }

        else {
            finish();
        }
    }

    private void onFailReturnToProfile() {
        runOnUiThread(() ->
                Toast.makeText(StravaImportActivity.this, "Failed to reach Strava. Please try again later.", Toast.LENGTH_LONG).show()
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

        Button importButton = findViewById(R.id.strava_importlatestbtn);
        importButton.setOnClickListener(v -> {
            importLatest(1);
        });
    }

    public void importLatest(int page) {
        HttpUrl url = Objects.requireNonNull(HttpUrl.parse("https://www.strava.com/api/v3/athlete/activities"))
                .newBuilder()
                .addQueryParameter("before", String.valueOf(System.currentTimeMillis() / 1000))
                .addQueryParameter("page", String.valueOf(page))
                .addQueryParameter("per_page", "3")
                .build();


        OkHttpClient client = new OkHttpClient();
        Request request = new Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer " + accessToken)
                .get()
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onResponse(@NonNull okhttp3.Call call, @NonNull okhttp3.Response response) {
                if(!response.isSuccessful() || response.body() == null) {
                    onFailReturnToProfile();
                }
                else {
                    runOnUiThread(() -> {
                        try {
                            String responseBody = response.body().string();
                            JSONArray activities = new JSONArray(responseBody);
                            for(int i = 0; i < activities.length(); i++) {
                                JSONObject jsonActivity = activities.getJSONObject(i);

                                int id = 1;
                                String name = jsonActivity.getString("name");
                                TravelCategory category = getCategoryFromStravaType(jsonActivity.getString("sport_type"));
                                String polyline = jsonActivity.getJSONObject("map").getString("summary_polyline");
                                int elapsedTime = jsonActivity.getInt("elapsed_time");
                                String startDate = jsonActivity.getString("start_date").replace("Z", "");
                                int distance = jsonActivity.getInt("distance");
                                long stravaId = jsonActivity.getInt("id");
                            }
                        }
                        catch(JSONException | IOException e) {
                            onFailReturnToProfile();
                        }
                    });
                }
            }

            @Override
            public void onFailure(@NonNull okhttp3.Call call, @NonNull IOException e) {
                onFailReturnToProfile();
            }
        });
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
}