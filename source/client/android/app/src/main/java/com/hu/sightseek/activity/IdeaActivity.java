package com.hu.sightseek.activity;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.google.firebase.auth.FirebaseAuth;
import com.hu.sightseek.R;
import com.hu.sightseek.db.LocalActivityDatabaseDAO;
import com.hu.sightseek.model.Activity;

import org.osmdroid.config.Configuration;
import org.osmdroid.views.CustomZoomButtonsController;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.TilesOverlay;

import java.util.ArrayList;

public class IdeaActivity extends AppCompatActivity {
    private LocalActivityDatabaseDAO dao;
    private ArrayList<Activity> activities;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_idea);
        Configuration.getInstance().load(
                getApplicationContext(),
                PreferenceManager.getDefaultSharedPreferences(getApplicationContext())
        );
        Configuration.getInstance().setUserAgentValue(getPackageName());

        // Auth
        FirebaseAuth mAuth = FirebaseAuth.getInstance();
        if(mAuth.getCurrentUser() == null) {
            Intent intent = new Intent(this, BannerActivity.class);
            startActivity(intent);
            finish();
        }

        // Add Menu
        Toolbar toolbar = findViewById(R.id.idea_topmenu);
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

        // Check if there's at least one stored activity
        dao = new LocalActivityDatabaseDAO(this);
        activities = dao.getAllActivities();
        if(activities.isEmpty()) {
            LayoutInflater inflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            ViewGroup root = findViewById(android.R.id.content);
            View overlayView = inflater.inflate(R.layout.overlay_noactivities, root, false);

            root.addView(overlayView);
            toolbar.post(() -> {
                FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
                params.topMargin = toolbar.getHeight();
                overlayView.setLayoutParams(params);
            });
            
            return;
        }

        // Setup map
        MapView mapView = findViewById(R.id.idea_map);
        mapView.setBackgroundColor(Color.TRANSPARENT);
        mapView.setMultiTouchControls(true);
        mapView.setUseDataConnection(true);

        TilesOverlay tilesOverlay = mapView.getOverlayManager().getTilesOverlay();
        tilesOverlay.setLoadingBackgroundColor(Color.TRANSPARENT);
        tilesOverlay.setLoadingLineColor(Color.TRANSPARENT);

        mapView.getController().setZoom(10.0);
        mapView.setMinZoomLevel(3.0);
        mapView.setMaxZoomLevel(20.0);

        mapView.getZoomController().setVisibility(CustomZoomButtonsController.Visibility.NEVER);
        mapView.setVerticalMapRepetitionEnabled(false);
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

        // Profile
        if(id == R.id.topmenu_profile) {
            // TODO: Check whether user is logged in
            Intent intent = new Intent(this, BannerActivity.class);
            startActivity(intent);
            return true;
        }

        // Statistics
        if(id == R.id.topmenu_statistics) {
            // TODO
            // Intent intent = new Intent(this, StatisticsActivity.class);
            return true;
        }

        return super.onOptionsItemSelected(item);
    }
}