package com.hu.sightseek.activity;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.hu.sightseek.Activity;
import com.hu.sightseek.R;
import com.hu.sightseek.adapter.ActivityAdapter;
import com.hu.sightseek.db.LocalActivityDatabaseDAO;

import org.osmdroid.config.Configuration;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    private LocalActivityDatabaseDAO dao;
    private RecyclerView recyclerView;
    private ActivityAdapter adapter;
    private List<Activity> activities;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Configuration.getInstance().load(
                getApplicationContext(),
                PreferenceManager.getDefaultSharedPreferences(getApplicationContext())
        );
        Configuration.getInstance().setUserAgentValue(getPackageName());

        // Show banner when launching for the first time
        SharedPreferences prefs = getSharedPreferences("AppPrefs", MODE_PRIVATE);
        boolean isFirstLaunch = prefs.getBoolean("isFirstLaunch", true);

        if(isFirstLaunch) {
            startActivity(new Intent(this, BannerActivity.class));
            prefs.edit().putBoolean("isFirstLaunch", false).apply();
            finish();
        }

        // Setup adapter
        dao = new LocalActivityDatabaseDAO(this);

        recyclerView = findViewById(R.id.main_activities);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        activities = new ArrayList<>();
        activities = dao.getAllActivities();

        adapter = new ActivityAdapter(this, activities);
        recyclerView.setAdapter(adapter);

        // Add Menu
        Toolbar toolbar = findViewById(R.id.main_topmenu);
        setSupportActionBar(toolbar);
        if(getSupportActionBar() != null) {
            getSupportActionBar().setDisplayShowTitleEnabled(false);
        }

        // Home button
        toolbar.setNavigationIcon(R.drawable.baseline_home_24);
        toolbar.setNavigationOnClickListener(v -> {
            // TODO: scroll to top
        });

        // Bottombar listener
        BottomNavigationView bottomNav = findViewById(R.id.main_bottommenu);
        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();

            // Ideas
            if(id == R.id.bottommenu_main_ideas) {
                // TODO
            }
            else if (id == R.id.bottommenu_main_record) {
                Intent intent = new Intent(this, RecordActivity.class);
                startActivity(intent);
            }
            else if (id == R.id.bottommenu_main_leaderboard) {
                // TODO
            }

            return true;
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