package com.hu.sightseek.activity;

import static com.hu.sightseek.utils.SightseekFirebaseUtils.removeCellsFromFirebase;
import static com.hu.sightseek.utils.SightseekGenericUtils.STRAVA_CLIENT_ID;
import static com.hu.sightseek.utils.SightseekGenericUtils.getVisitedCells;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import android.preference.PreferenceManager;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.Toast;

import androidx.appcompat.widget.Toolbar;

import com.google.android.gms.maps.model.LatLng;
import com.google.firebase.auth.FirebaseAuth;

import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.hu.sightseek.R;
import com.hu.sightseek.db.LocalDatabaseDAO;

import org.osmdroid.config.Configuration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;

public class ProfileActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);
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
        Toolbar toolbar = findViewById(R.id.profile_topmenu);
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

        // Strava
        Button stravaButton = findViewById(R.id.profile_strava);
        stravaButton.setOnClickListener(v -> {
            Uri uri = Uri.parse("https://www.strava.com/oauth/mobile/authorize")
                    .buildUpon()
                    .appendQueryParameter("client_id", STRAVA_CLIENT_ID)
                    .appendQueryParameter("response_type", "code")
                    .appendQueryParameter("redirect_uri", "sightseek://strava-auth")
                    .appendQueryParameter("approval_prompt", "auto")
                    .appendQueryParameter("scope", "read,activity:read_all")
                    .build();

            Intent intent = new Intent(Intent.ACTION_VIEW, uri);
            startActivity(intent);
        });

        // Unlink Strava
        Button unlinkButton = findViewById(R.id.profile_unlinkbtn);
        unlinkButton.setOnClickListener(v -> {
            AlertDialog dialog = new AlertDialog.Builder(this)
                    .setTitle("Confirmation")
                    .setMessage("Are you sure you want to unlink your account? This will delete all activities that were imported!")
                    .setPositiveButton("Yes", (d, which) -> {
                        Executors.newSingleThreadExecutor().execute(() -> {
                            LocalDatabaseDAO dao2 = new LocalDatabaseDAO(this);
                            ArrayList<LatLng> points = dao2.getAllImportedPoints();
                            dao2.deleteImportedActivities();
                            dao2.close();

                            HashMap<String, Integer> cells = getVisitedCells(points);
                            removeCellsFromFirebase(mAuth, cells);

                            SharedPreferences prefs = getSharedPreferences("AppPrefs", Context.MODE_PRIVATE);
                            if(prefs.contains("StravaLatestImportDate")) {
                                prefs.edit().remove("StravaLatestImportDate").apply();
                            }

                            runOnUiThread(() -> {
                                Toast.makeText(this, "Successfully unlinked.", Toast.LENGTH_LONG).show();
                            });
                        });
                    })
                    .setNegativeButton("No", (d, which) -> d.dismiss())
                    .setCancelable(true)
                    .create();

            dialog.show();
        });

        // Logout
        Button logoutButton = findViewById(R.id.profile_logout);
        logoutButton.setOnClickListener(v -> {
            mAuth.signOut();
            finish();
        });
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

        return super.onOptionsItemSelected(item);
    }
}