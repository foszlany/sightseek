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

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.hu.sightseek.R;

import org.osmdroid.config.Configuration;

public class MainActivity extends AppCompatActivity {
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

        // Add Menu
        Toolbar toolbar = findViewById(R.id.main_topmenu);
        setSupportActionBar(toolbar);
        if(getSupportActionBar() != null) {
            getSupportActionBar().setDisplayShowTitleEnabled(false);
        }

        // Home button
        toolbar.setNavigationIcon(R.drawable.baseline_home_24);
        toolbar.setNavigationOnClickListener(v -> {
            // scroll to top
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
            // TODO
            // Intent intent = new Intent(this, ProfileActivity.class);
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