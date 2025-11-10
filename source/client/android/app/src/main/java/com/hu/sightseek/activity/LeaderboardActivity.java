package com.hu.sightseek.activity;

import static android.view.View.GONE;
import static android.view.View.INVISIBLE;
import static android.view.View.VISIBLE;

import static com.hu.sightseek.utils.SightseekVectorizationUtils.copyShapefileToInternalStorage;

import android.content.Intent;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.PopupWindow;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.AggregateQuery;
import com.google.firebase.firestore.AggregateSource;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;
import com.hu.sightseek.R;
import com.hu.sightseek.adapter.LeaderboardEntryAdapter;
import com.hu.sightseek.model.LeaderboardEntry;

import org.osmdroid.config.Configuration;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.concurrent.Executors;

import diewald_shapeFile.shapeFile.ShapeFile;

public class LeaderboardActivity extends AppCompatActivity {
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private RecyclerView leaderboardRecyclerView;
    private String folderPath;
    private String countryCode;

    private boolean isGridView;
    private LeaderboardEntryAdapter leaderboardEntryAdapter;
    private ArrayList<LeaderboardEntry> leaderboardEntries;
    private LeaderboardEntry myEntry;
    private ImageButton regionFilterButton;
    private TextView descriptionTextView;

    private Animation fadeIn;
    private Animation rotate;

    private ImageView loadingImage;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_leaderboard);
        Configuration.getInstance().load(
                getApplicationContext(),
                PreferenceManager.getDefaultSharedPreferences(getApplicationContext())
        );
        Configuration.getInstance().setUserAgentValue(getPackageName());

        // Check if user is logged in
        mAuth = FirebaseAuth.getInstance();
        if(mAuth.getCurrentUser() == null) {
            startActivity(new Intent(this, BannerActivity.class));
            finish();
            return;
        }

        db = FirebaseFirestore.getInstance();

        leaderboardRecyclerView = findViewById(R.id.leaderboard_entries);
        leaderboardRecyclerView.setLayoutManager(new LinearLayoutManager(this));

        folderPath = this.getFilesDir().getAbsolutePath();
        leaderboardEntries = new ArrayList<>();
        isGridView = false;
        regionFilterButton = findViewById(R.id.leaderboard_filterbtn);
        descriptionTextView = findViewById(R.id.leaderboard_description);

        fadeIn = AnimationUtils.loadAnimation(this, R.anim.fade_in);
        rotate = AnimationUtils.loadAnimation(this, R.anim.looping_rotation);

        // Add Menu
        Toolbar toolbar = findViewById(R.id.leaderboard_topmenu);
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

        // Bottombar listener
        BottomNavigationView bottomNav = findViewById(R.id.leaderboard_bottommenu);
        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();

            if(id == R.id.bottommenu_leaderboard_grid) {
                initCellLeaderboard();
            }
            else if(id == R.id.bottommenu_leaderboard_distance) {
                initRegionalLeaderboard();
            }

            return true;
        });

        initCellLeaderboard();
    }

    private void initCellLeaderboard() {
        if(isGridView) {
            return;
        }
        isGridView = true;

        leaderboardRecyclerView.setAdapter(null);
        regionFilterButton.setVisibility(GONE);

        Executors.newSingleThreadExecutor().execute(() -> {
            String currentUid = Objects.requireNonNull(FirebaseAuth.getInstance().getCurrentUser()).getUid();

            // Top 100
            Task<QuerySnapshot> leaderboardTask = db
                    .collection("leaderboard_cells")
                    .orderBy("cellsVisited", Query.Direction.DESCENDING)
                    .limit(100)
                    .get();

            // Username & cells visited
            Task<DocumentSnapshot> userTask = db
                    .collection("leaderboard_cells")
                    .document(currentUid)
                    .get();

            setupLeaderboardViews(leaderboardTask, userTask, "cellsVisited", null);
        });
    }

    private void initRegionalLeaderboard() {
        if(!isGridView) {
            return;
        }
        isGridView = false;

        leaderboardRecyclerView.setAdapter(null);
        regionFilterButton.setVisibility(VISIBLE);

        Executors.newSingleThreadExecutor().execute(() -> {
            setupRegionalLeaderboard("Global");
        });
    }

    private void setupLeaderboardViews(Task<QuerySnapshot> leaderboardTask, Task<DocumentSnapshot> userTask, String valueStr, String regionalQueryStr) {
        loadingImage = findViewById(R.id.leaderboard_loading);
        loadingImage.startAnimation(rotate);

        leaderboardEntries.clear();

        Tasks.whenAllSuccess(leaderboardTask, userTask).addOnSuccessListener(results -> {
            QuerySnapshot leaderboardSnapshot = (QuerySnapshot) results.get(0);
            DocumentSnapshot userSnapshot = (DocumentSnapshot) results.get(1);

            for(QueryDocumentSnapshot document : leaderboardSnapshot) {
                String username = document.getString("username");

                Double valueHolder = document.getDouble(valueStr);
                double value = valueHolder == null ? 0 : valueHolder;

                leaderboardEntries.add(new LeaderboardEntry(username, value));
            }

            leaderboardEntryAdapter = new LeaderboardEntryAdapter(this, leaderboardEntries, isGridView);

            runOnUiThread(() -> {
                Double valueHolder = userSnapshot.getDouble(valueStr);
                double value = valueHolder == null ? 0 : valueHolder;

                // User placing
                AggregateQuery countQuery;
                if("cellsVisited".equals(valueStr)) {
                    countQuery = db
                            .collection("leaderboard_cells")
                            .whereGreaterThan(valueStr, value)
                            .count();
                }
                else {
                    countQuery = db
                            .collection("leaderboard_regional")
                            .document(regionalQueryStr)
                            .collection("users")
                            .whereGreaterThan(valueStr, value)
                            .count();
                }

                countQuery.get(AggregateSource.SERVER).addOnSuccessListener(snapshot -> {
                    long placing = snapshot.getCount() + 1;

                    runOnUiThread(() -> {
                        TextView myPlacingTextView = findViewById(R.id.leaderboard_myplacing);
                        myPlacingTextView.setText(getString(R.string.leaderboard_entry_placing, placing));

                        String username = userSnapshot.getString("username");
                        myEntry = new LeaderboardEntry(username, value);

                        TextView myNameTextView = findViewById(R.id.leaderboard_myname);
                        myNameTextView.setText(myEntry.getUsername());

                        View myEntryView = findViewById(R.id.leaderboard_myentry);
                        myEntryView.startAnimation(fadeIn);
                        myEntryView.setVisibility(VISIBLE);

                        View separator = findViewById(R.id.leaderboard_separator);
                        separator.startAnimation(fadeIn);
                        separator.setVisibility(VISIBLE);

                        TextView myValueTextView = findViewById(R.id.leaderboard_myvalue);
                        if("cellsVisited".equals(valueStr)) {
                            myValueTextView.setText(getString(R.string.leaderboard_entry_cellvalue, (int) myEntry.getValue()));

                           descriptionTextView.setText(getString(R.string.leaderboard_cellsdescription));
                        }
                        else {
                            myValueTextView.setText(getString(R.string.leaderboard_entry_distancevalue, myEntry.getValue()));

                            String regionalDescription = regionalQueryStr.replace(";", " / ");
                            descriptionTextView.setText(regionalDescription);
                        }

                        loadingImage.clearAnimation();
                        loadingImage.setVisibility(GONE);

                        leaderboardRecyclerView.setAdapter(leaderboardEntryAdapter);
                        leaderboardRecyclerView.setVisibility(VISIBLE);
                        leaderboardRecyclerView.startAnimation(fadeIn);
                    });
                });
            });
        });
    }

    private void setupRegionalLeaderboard(String queryStr) {
        String currentUid = Objects.requireNonNull(mAuth.getCurrentUser()).getUid();

        // Top 100
        Task<QuerySnapshot> leaderboardTask = db.collection("leaderboard_regional")
                .document(queryStr)
                .collection("users")
                .orderBy("distance", Query.Direction.DESCENDING)
                .limit(100)
                .get();

        // Username & regional distances
        Task<DocumentSnapshot> userTask = db.collection("leaderboard_regional")
                .document(queryStr)
                .collection("users")
                .document(currentUid)
                .get();

        setupLeaderboardViews(leaderboardTask, userTask, "distance", queryStr);
    }

    private String getQueryString(Spinner continentSpinner, Spinner countrySpinner, Spinner regionSpinner, Spinner subRegionSpinner) {
        String continent = continentSpinner.getSelectedItem().toString();
        String country = countrySpinner.getSelectedItem().toString();
        String region = regionSpinner.getSelectedItem().toString();
        String subRegion = subRegionSpinner.getSelectedItem().toString();

        // Build query
        StringBuilder queryBuilder = new StringBuilder();

        if(continent.equalsIgnoreCase("None")) {
            queryBuilder.append("Global");
        }
        else {
            queryBuilder.append(continent);

            if(!country.equalsIgnoreCase("None")) {
                queryBuilder.append(";").append(country);

                if(!region.equalsIgnoreCase("None")) {
                    queryBuilder.append(";").append(region);

                    if(!subRegion.equalsIgnoreCase("None")) {
                        queryBuilder.append(";").append(subRegion);
                    }
                }
            }
        }

        return queryBuilder.toString();
    }

    @Override
    protected void onStart() {
        super.onStart();

        regionFilterButton.setOnClickListener(v -> {
            View popupView = LayoutInflater.from(this).inflate(R.layout.filter_region, null);
            PopupWindow popupWindow = new PopupWindow(popupView, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, true);
            popupWindow.showAsDropDown(v);

            // continent
            ArrayList<String> continentOptions = new ArrayList<>();
            continentOptions.add("None");
            ArrayList<String> continentFiles = new ArrayList<>();

            // [ISO 3166]_country
            ArrayList<String> countryOptions = new ArrayList<>();
            countryOptions.add("None");
            ArrayList<String> countryCodeOptions = new ArrayList<>();

            // [ISO 3166]_largeregion
            // .dbf file has two columns: first has the regions, second has subregions if they exist (one-to-many)
            LinkedHashSet<String> regionOptions = new LinkedHashSet<>();
            regionOptions.add("None");

            // [ISO 3166]_smallregion
            ArrayList<String> subRegionOptions = new ArrayList<>();
            subRegionOptions.add("None");

            // Query continents
            try {
                copyShapefileToInternalStorage(this, "global");

                ShapeFile shapeFile = new ShapeFile(folderPath, "global");
                shapeFile.READ();

                for(int i = 0; i < shapeFile.getSHP_shapeCount(); i++) {
                    continentOptions.add((shapeFile.getDBF_record(i)[0]).trim());
                    continentFiles.add((shapeFile.getDBF_record(i)[1]).trim());
                }
            }
            catch(Exception e) {
                throw new RuntimeException(e);
            }

            // Continent
            Spinner continentSpinner = popupView.findViewById(R.id.leaderboard_narrowsearch_continentspinner);
            ArrayAdapter<String> continentAdapter = new ArrayAdapter<>(
                    this,
                    android.R.layout.simple_spinner_item,
                    continentOptions
            );
            continentAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            continentSpinner.setAdapter(continentAdapter);

            // Country
            Spinner countrySpinner = popupView.findViewById(R.id.leaderboard_narrowsearch_countryspinner);
            ArrayAdapter<String> countryAdapter = new ArrayAdapter<>(
                    this,
                    android.R.layout.simple_spinner_item,
                    countryOptions
            );
            countryAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            countrySpinner.setAdapter(countryAdapter);

            // Region
            Spinner regionSpinner = popupView.findViewById(R.id.leaderboard_narrowsearch_regionspinner);
            ArrayAdapter<String> regionAdapter = new ArrayAdapter<>(
                    this,
                    android.R.layout.simple_spinner_item,
                    new ArrayList<>(regionOptions)
            );
            regionAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            regionSpinner.setAdapter(regionAdapter);

            // Subregion
            Spinner subRegionSpinner = popupView.findViewById(R.id.leaderboard_narrowsearch_subregionspinner);
            ArrayAdapter<String> subRegionAdapter = new ArrayAdapter<>(
                    this,
                    android.R.layout.simple_spinner_item,
                    subRegionOptions
            );
            subRegionAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            subRegionSpinner.setAdapter(subRegionAdapter);

            // Continent listener
            continentSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                    countryCodeOptions.clear();

                    countryOptions.clear();
                    countryOptions.add("None");
                    countrySpinner.setSelection(0);
                    countryAdapter.notifyDataSetChanged();

                    regionOptions.clear();
                    regionOptions.add("None");
                    regionSpinner.setSelection(0);
                    regionAdapter.notifyDataSetChanged();

                    subRegionOptions.clear();
                    subRegionOptions.add("None");
                    subRegionSpinner.setSelection(0);
                    subRegionAdapter.notifyDataSetChanged();

                    if(position == 0) {
                        return;
                    }

                    String continent = continentFiles.get(position - 1);
                    copyShapefileToInternalStorage(LeaderboardActivity.this, continent);

                    try {
                        ShapeFile shapeFile = new ShapeFile(folderPath, continent);
                        shapeFile.READ();

                        for(int i = 0; i < shapeFile.getSHP_shapeCount(); i++) {
                            countryOptions.add((shapeFile.getDBF_record(i)[0]).trim());
                            countryCodeOptions.add((shapeFile.getDBF_record(i)[1]).trim());
                        }

                        countryAdapter.notifyDataSetChanged();
                    }
                    catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }

                @Override
                public void onNothingSelected(AdapterView<?> parent) {}
            });

            // Country listener
            countrySpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                    regionOptions.clear();
                    regionOptions.add("None");
                    regionSpinner.setSelection(0);
                    regionAdapter.notifyDataSetChanged();

                    subRegionOptions.clear();
                    subRegionOptions.add("None");
                    subRegionSpinner.setSelection(0);
                    subRegionAdapter.notifyDataSetChanged();

                    if(position == 0) {
                        return;
                    }

                    countryCode = countryCodeOptions.get(position - 1);

                    // Allow for non-existent region-data
                    boolean success = copyShapefileToInternalStorage(LeaderboardActivity.this, countryCode + "_largeregion");
                    if(!success) {
                        return;
                    }

                    try {
                        ShapeFile shapeFile = new ShapeFile(folderPath, countryCode + "_largeregion");
                        shapeFile.READ();

                        for(int i = 0; i < shapeFile.getDBF_recordCount(); i++) {
                            regionOptions.add((shapeFile.getDBF_record(i)[0]).trim());
                        }

                        ArrayAdapter<String> regionAdapter = new ArrayAdapter<>(
                                LeaderboardActivity.this,
                                android.R.layout.simple_spinner_item,
                                new ArrayList<>(regionOptions)
                        );
                        regionAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                        regionSpinner.setAdapter(regionAdapter);
                    }
                    catch(Exception e) {
                        throw new RuntimeException(e);
                    }
                }

                @Override
                public void onNothingSelected(AdapterView<?> parent) {}
            });

            // Region listener
            regionSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                    subRegionOptions.clear();
                    subRegionOptions.add("None");
                    subRegionSpinner.setSelection(0);
                    subRegionAdapter.notifyDataSetChanged();

                    if(position == 0) {
                        return;
                    }

                    // Allow for non-existent region-data
                    boolean success = copyShapefileToInternalStorage(LeaderboardActivity.this, countryCode + "_largeregion");
                    if(!success) {
                        return;
                    }

                    ArrayList<String> regionOptionsList = new ArrayList<>(regionOptions);

                    try {
                        ShapeFile shapeFile = new ShapeFile(folderPath, countryCode + "_largeregion");
                        shapeFile.READ();

                        String selectedRegion = regionOptionsList.get(position).trim();

                        for(int i = 0; i < shapeFile.getDBF_recordCount(); i++) {
                            String regionName = shapeFile.getDBF_record(i)[0].trim();
                            String subregionName = shapeFile.getDBF_record(i)[1].trim();

                            if(selectedRegion.equals(regionName)) {
                                subRegionOptions.add(subregionName);
                            }
                        }

                        subRegionAdapter.notifyDataSetChanged();
                    }
                    catch(Exception e) {
                        throw new RuntimeException(e);
                    }
                }

                @Override
                public void onNothingSelected(AdapterView<?> parent) {}
            });

            // Search
            Button queryButton = popupView.findViewById(R.id.leaderboard_narrowsearch_search);
            queryButton.setOnClickListener(w -> {
                Executors.newSingleThreadExecutor().execute(() -> {
                    String queryStr = getQueryString(continentSpinner, countrySpinner, regionSpinner, subRegionSpinner);

                    runOnUiThread(popupWindow::dismiss);

                    setupRegionalLeaderboard(queryStr);
                });
            });
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

        // Statistics
        if(id == R.id.topmenu_profile) {
            Intent intent = new Intent(this, ProfileActivity.class);
            startActivity(intent);
            return true;
        }

        return super.onOptionsItemSelected(item);
    }
}