package com.hu.sightseek.activity;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageButton;
import android.widget.PopupMenu;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;
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
import java.util.Collections;

public class MainActivity extends AppCompatActivity {
    private RecyclerView recyclerView;
    private ActivityAdapter adapter;
    private ArrayList<Activity> activities;

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
        LocalActivityDatabaseDAO dao = new LocalActivityDatabaseDAO(this);

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
        toolbar.setNavigationOnClickListener(v -> recyclerView.scrollToPosition(0));

        // Searchbar
        SearchView searchView = findViewById(R.id.main_searchbar);
        searchView.setOnClickListener(v -> searchView.onActionViewExpanded());
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String s) {
                adapter.getFilter().filter(s);
                return false;
            }

            @Override
            public boolean onQueryTextChange(String s) {
                adapter.getFilter().filter(s);
                return false;
            }
        });

        // Filter button
        ImageButton filterButton = findViewById(R.id.main_filterbtn);
        filterButton.setOnClickListener(v -> { // TODO: Add some animation
            PopupMenu popup = initFilterPopup(v);
            popup.show();
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

    // Filter popup
    @NonNull
    private PopupMenu initFilterPopup(View menuItemView) {
        PopupMenu popup = new PopupMenu(this, menuItemView);

        popup.inflate(R.menu.menu_main_filter);
        popup.setOnMenuItemClickListener(menuItem -> {
            int menuId = menuItem.getItemId();

            if(menuId == R.id.menu_filter_date_recent) {
                Collections.sort(activities, (a1, a2) -> a2.getStarttime().compareTo(a1.getStarttime()));
            }
            else if(menuId == R.id.menu_filter_date_old) {
                Collections.sort(activities, (a1, a2) -> a1.getStarttime().compareTo(a2.getStarttime()));
            }
            else if(menuId == R.id.menu_filter_alpha_az) {
                Collections.sort(activities, (a1, a2) -> a1.getName().compareToIgnoreCase(a2.getName()));
            }
            else if(menuId == R.id.menu_filter_alpha_za) {
                Collections.sort(activities, (a1, a2) -> a2.getName().compareToIgnoreCase(a1.getName()));
            }
            else if(menuId == R.id.menu_filter_dist_lth) {
                Collections.sort(activities, (a1, a2) -> Double.compare(a1.getDistance(), a2.getDistance()));
            }
            else if(menuId == R.id.menu_filter_dist_htl) {
                Collections.sort(activities, (a1, a2) -> Double.compare(a2.getDistance(), a1.getDistance()));
            }
            else if(menuId == R.id.menu_filter_time_lth) {
                Collections.sort(activities, (a1, a2) -> Double.compare(a1.getElapsedtime(), a2.getElapsedtime()));
            }
            else if(menuId == R.id.menu_filter_time_htl) {
                Collections.sort(activities, (a1, a2) -> Double.compare(a2.getElapsedtime(), a1.getElapsedtime()));
            }

            adapter.setActivityListFiltered(activities);
            adapter.notifyItemRangeChanged(0, adapter.getItemCount());

            return true;
        });

        return popup;
    }
}