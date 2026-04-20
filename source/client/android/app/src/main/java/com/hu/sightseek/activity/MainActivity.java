package com.hu.sightseek.activity;

import static com.hu.sightseek.helper.LocaleHelper.setLocale;
import static com.hu.sightseek.util.GenericUtils.hideKeyboard;

import android.content.Context;
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
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ImageButton;
import android.widget.PopupWindow;
import android.widget.RadioButton;
import android.widget.RadioGroup;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.hu.sightseek.enums.TravelCategory;
import com.hu.sightseek.helper.LocaleHelper;
import com.hu.sightseek.model.Activity;
import com.hu.sightseek.R;
import com.hu.sightseek.adapter.ActivityAdapter;
import com.hu.sightseek.db.LocalDatabaseDAO;

import org.osmdroid.config.Configuration;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    private RecyclerView recyclerView;
    private ActivityAdapter adapter;
    private List<Activity> activities;
    private int checkedSortByMethod;
    private boolean isLocoChecked;
    private boolean isMicroChecked;
    private boolean isOtherChecked;

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

        LocaleHelper.setLocale(this);

        checkedSortByMethod = R.id.main_filtermenu_date_recent;
        isLocoChecked = true;
        isMicroChecked = true;
        isOtherChecked = true;

        // Setup adapter
        LocalDatabaseDAO dao = new LocalDatabaseDAO(this);

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
            LocalDatabaseDAO dao2 = new LocalDatabaseDAO(this);
            List<Activity> newActivities = dao2.getAllActivities();
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
        searchView.setQuery(adapter.getSearchQuery(), false);
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                hideKeyboard(MainActivity.this);
                adapter.getFilter().filter(query);
                return true;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                adapter.getFilter().filter(newText);
                return true;
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
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
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

    private void initFilterPopup(View menuItemView) {
        View popupView = LayoutInflater.from(this).inflate(R.layout.filter_main, (ViewGroup) menuItemView.getParent(), false);
        PopupWindow popupWindow = new PopupWindow(popupView, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, true);
        popupWindow.showAsDropDown(menuItemView);

        RadioGroup radioGroup = popupView.findViewById(R.id.main_filtermenu_radiogroup);
        RadioButton previousSortByMethod = popupView.findViewById(checkedSortByMethod);
        previousSortByMethod.toggle();

        radioGroup.setOnCheckedChangeListener((group, checkedId) -> checkedSortByMethod = checkedId);

        CheckBox locoCheckBox = popupView.findViewById(R.id.main_filtermenu_loco);
        CheckBox microCheckBox = popupView.findViewById(R.id.main_filtermenu_micro);
        CheckBox otherCheckBox = popupView.findViewById(R.id.main_filtermenu_other);

        locoCheckBox.setChecked(isLocoChecked);
        microCheckBox.setChecked(isMicroChecked);
        otherCheckBox.setChecked(isOtherChecked);

        Button applyButton = popupView.findViewById(R.id.main_filtermenu_applybtn);
        applyButton.setOnClickListener(v -> {
            // Sort by
            if(checkedSortByMethod == R.id.main_filtermenu_date_recent) {
                activities.sort((a1, a2) -> a2.getStartTime().compareTo(a1.getStartTime()));
            }
            else if(checkedSortByMethod == R.id.main_filtermenu_date_old) {
                activities.sort((a1, a2) -> a1.getStartTime().compareTo(a2.getStartTime()));
            }
            else if(checkedSortByMethod == R.id.main_filtermenu_alpha_az) {
                activities.sort((a1, a2) -> a1.getName().compareToIgnoreCase(a2.getName()));
            }
            else if(checkedSortByMethod == R.id.main_filtermenu_alpha_za) {
                activities.sort((a1, a2) -> a2.getName().compareToIgnoreCase(a1.getName()));
            }
            else if(checkedSortByMethod == R.id.main_filtermenu_dist_lth) {
                activities.sort((a1, a2) -> Double.compare(a1.getDistance(), a2.getDistance()));
            }
            else if(checkedSortByMethod == R.id.main_filtermenu_dist_htl) {
                activities.sort((a1, a2) -> Double.compare(a2.getDistance(), a1.getDistance()));
            }
            else if(checkedSortByMethod == R.id.main_filtermenu_time_lth) {
                activities.sort((a1, a2) -> Double.compare(a1.getElapsedTime(), a2.getElapsedTime()));
            }
            else if(checkedSortByMethod == R.id.main_filtermenu_time_htl) {
                activities.sort((a1, a2) -> Double.compare(a2.getElapsedTime(), a1.getElapsedTime()));
            }

            // Categories
            isLocoChecked = locoCheckBox.isChecked();
            isMicroChecked = microCheckBox.isChecked();
            isOtherChecked = otherCheckBox.isChecked();

            ArrayList<Activity> filtered = new ArrayList<>();
            if(!(!isLocoChecked && !isMicroChecked && !isOtherChecked)) {
                for(Activity activity : activities) {
                    if(activity.getCategory() == TravelCategory.LOCOMOTOR && !isLocoChecked
                            || activity.getCategory() == TravelCategory.MICROMOBILITY && !isMicroChecked
                            || activity.getCategory() == TravelCategory.OTHER && !isOtherChecked) {
                        continue;
                    }

                    filtered.add(activity);
                }
            }

            adapter.applyCategoryFilter(filtered);
            adapter.notifyDataSetChanged();

            popupWindow.dismiss();
        });
    }

    // Change language
    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(setLocale(newBase));
    }
}