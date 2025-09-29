package com.hu.sightseek.activity;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.PopupMenu;
import android.widget.PopupWindow;
import android.widget.RadioGroup;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.hu.sightseek.model.Activity;
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
        Configuration.getInstance().setCacheMapTileCount((short) 12);
        Configuration.getInstance().setCacheMapTileOvershoot((short) 2);

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

        dao.close();

        adapter = new ActivityAdapter(this, activities);
        recyclerView.setAdapter(adapter);

        // Refresh
        SwipeRefreshLayout swipeRefresh = findViewById(R.id.main_swipecontainer);
        swipeRefresh.setOnRefreshListener(() -> {
            LocalActivityDatabaseDAO dao2 = new LocalActivityDatabaseDAO(this);
            ArrayList<Activity> newActivities = dao2.getAllActivities();
            dao2.close();

            if(newActivities.size() != activities.size()) {
                activities.clear();
                activities.addAll(newActivities);

                adapter.updateActivities(newActivities);
                adapter.notifyDataSetChanged();
            }

            swipeRefresh.setRefreshing(false);
        });

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
        filterButton.setOnClickListener(this::initFilterPopup);

        // Bottombar listener
        BottomNavigationView bottomNav = findViewById(R.id.main_bottommenu);
        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();

            if(id == R.id.bottommenu_main_ideas) {
                Intent intent = new Intent(this, IdeaActivity.class);
                startActivity(intent);
            }
            else if(id == R.id.bottommenu_main_record) {
                Intent intent = new Intent(this, RecordActivity.class);
                startActivity(intent);
            }
            else if(id == R.id.bottommenu_main_leaderboard) {
                Intent intent = new Intent(this, LeaderboardActivity.class);
                startActivity(intent);
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
            Intent intent = new Intent(this, ProfileActivity.class);
            startActivity(intent);
            return true;
        }

        // Statistics
        if(id == R.id.topmenu_statistics) {
            Intent intent = new Intent(this, StatisticsActivity.class);
            startActivity(intent);
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    // Filter popup
    @NonNull
    private void initFilterPopup(View menuItemView) {
        View popupView = LayoutInflater.from(this).inflate(R.layout.filter_main, null);
        PopupWindow popupWindow = new PopupWindow(
                popupView,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                true
        );
        popupWindow.showAsDropDown(menuItemView);

        RadioGroup radioGroup = popupView.findViewById(R.id.main_filtermenu_radiogroup);

        radioGroup.setOnCheckedChangeListener((group, checkedId) -> {
            if(checkedId == R.id.main_filtermenu_date_recent) {
                Collections.sort(activities, (a1, a2) -> a2.getStarttime().compareTo(a1.getStarttime()));
            }
            else if(checkedId == R.id.main_filtermenu_date_old) {
                Collections.sort(activities, (a1, a2) -> a1.getStarttime().compareTo(a2.getStarttime()));
            }
            else if(checkedId == R.id.main_filtermenu_alpha_az) {
                Collections.sort(activities, (a1, a2) -> a1.getName().compareToIgnoreCase(a2.getName()));
            }
            else if(checkedId == R.id.main_filtermenu_alpha_za) {
                Collections.sort(activities, (a1, a2) -> a2.getName().compareToIgnoreCase(a1.getName()));
            }
            else if(checkedId == R.id.main_filtermenu_dist_lth) {
                Collections.sort(activities, (a1, a2) -> Double.compare(a1.getDistance(), a2.getDistance()));
            }
            else if(checkedId == R.id.main_filtermenu_dist_htl) {
                Collections.sort(activities, (a1, a2) -> Double.compare(a2.getDistance(), a1.getDistance()));
            }
            else if(checkedId == R.id.main_filtermenu_time_lth) {
                Collections.sort(activities, (a1, a2) -> Double.compare(a1.getElapsedtime(), a2.getElapsedtime()));
            }
            else if(checkedId == R.id.main_filtermenu_time_htl) {
                Collections.sort(activities, (a1, a2) -> Double.compare(a2.getElapsedtime(), a1.getElapsedtime()));
            }

            adapter.setActivityListFiltered(activities);
            adapter.notifyItemRangeChanged(0, adapter.getItemCount());

            popupWindow.dismiss();
        });
    }
}