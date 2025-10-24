package com.hu.sightseek.activity;

import static android.view.View.GONE;
import static android.view.View.VISIBLE;

import android.content.Context;
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
import com.hu.sightseek.adapter.LeaderboardCellEntryAdapter;
import com.hu.sightseek.model.LeaderboardEntry;

import org.osmdroid.config.Configuration;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.concurrent.Executors;

import diewald_shapeFile.shapeFile.ShapeFile;

public class LeaderboardActivity extends AppCompatActivity {
    private String folderPath;
    private String countryCode;

    private boolean isGridView;
    private LeaderboardCellEntryAdapter cellAdapter;
    private ArrayList<LeaderboardEntry> cellEntries;
    private LeaderboardEntry myCellEntry;
    private ImageButton narrowSearchButton;

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
        FirebaseAuth mAuth = FirebaseAuth.getInstance();
        if(mAuth.getCurrentUser() == null) {
            startActivity(new Intent(this, BannerActivity.class));
            finish();
            return;
        }

        folderPath = this.getFilesDir().getAbsolutePath();
        cellEntries = new ArrayList<>();
        isGridView = false;
        narrowSearchButton = findViewById(R.id.leaderboard_filterbtn);

        fadeIn = AnimationUtils.loadAnimation(this, R.anim.fade_in);
        rotate = AnimationUtils.loadAnimation(this, R.anim.looping_rotation);

        loadingImage = findViewById(R.id.leaderboard_loading);
        loadingImage.startAnimation(rotate);

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

        RecyclerView recyclerView = findViewById(R.id.leaderboard_entries);

        if(cellAdapter == null) {
            recyclerView.setLayoutManager(new LinearLayoutManager(this));

            Executors.newSingleThreadExecutor().execute(() -> {
                FirebaseFirestore db = FirebaseFirestore.getInstance();
                FirebaseAuth auth = FirebaseAuth.getInstance();
                String currentUid = Objects.requireNonNull(auth.getCurrentUser()).getUid();

                // Top 100
                Task<QuerySnapshot> leaderboardTask = db.collection("leaderboard_cells")
                        .orderBy("cellsVisited", Query.Direction.DESCENDING)
                        .limit(100)
                        .get();

                // Username & cells visited
                Task<DocumentSnapshot> userTask = db.collection("leaderboard_cells")
                        .document(currentUid)
                        .get();

                Tasks.whenAllSuccess(leaderboardTask, userTask).addOnSuccessListener(results -> {
                    QuerySnapshot leaderboardSnapshot = (QuerySnapshot) results.get(0);
                    DocumentSnapshot userSnapshot = (DocumentSnapshot) results.get(1);

                    for(QueryDocumentSnapshot document : leaderboardSnapshot) {
                        String username = document.getString("username");

                        Long cellsVisitedHolder = document.getLong("cellsVisited");
                        long cellsVisited = cellsVisitedHolder == null ? 0 : cellsVisitedHolder;
                        cellEntries.add(new LeaderboardEntry(username, cellsVisited));
                    }

                    cellAdapter = new LeaderboardCellEntryAdapter(this, cellEntries);

                    if(userSnapshot.exists()) {
                        runOnUiThread(() -> {
                            Long userCellsVisitedHolder = userSnapshot.getLong("cellsVisited");
                            long userCellsVisited = userCellsVisitedHolder == null ? 0 : userCellsVisitedHolder;

                            // User placing
                            AggregateQuery countQuery = db.collection("leaderboard_cells")
                                    .whereGreaterThan("cellsVisited", userCellsVisited)
                                    .count();

                                    countQuery.get(AggregateSource.SERVER).addOnSuccessListener(snapshot -> {
                                        long placing = snapshot.getCount() + 1;

                                        runOnUiThread(() -> {
                                            TextView myPlacingTextView = findViewById(R.id.leaderboard_myplacing);
                                            myPlacingTextView.setText(getString(R.string.leaderboard_entry_placing, placing));

                                            String username = userSnapshot.getString("username");
                                            myCellEntry = new LeaderboardEntry(username, userCellsVisited);

                                            TextView myNameTextView = findViewById(R.id.leaderboard_myname);
                                            myNameTextView.setText(myCellEntry.getUsername());

                                            TextView myValueTextView = findViewById(R.id.leaderboard_myvalue);
                                            myValueTextView.setText(getString(R.string.leaderboard_entry_cellvalue, (int) myCellEntry.getValue()));

                                            View myEntryView = findViewById(R.id.leaderboard_myentry);
                                            myEntryView.startAnimation(fadeIn);
                                            myEntryView.setVisibility(VISIBLE);

                                            View separator = findViewById(R.id.leaderboard_separator);
                                            separator.startAnimation(fadeIn);
                                            separator.setVisibility(VISIBLE);

                                            recyclerView.startAnimation(fadeIn);
                                            recyclerView.setAdapter(cellAdapter);

                                            loadingImage.clearAnimation();
                                            loadingImage.setVisibility(GONE);
                                        });
                                    });
                        });
                    }
                    else {
                        cellAdapter = new LeaderboardCellEntryAdapter(this, cellEntries);
                        recyclerView.startAnimation(fadeIn);
                        recyclerView.setAdapter(cellAdapter);

                        loadingImage.clearAnimation();
                        loadingImage.setVisibility(GONE);
                    }
                });
            });
        }
        else {
            recyclerView.setAdapter(cellAdapter);
        }

        TextView descriptionTextView = findViewById(R.id.leaderboard_description);
        descriptionTextView.setText(getString(R.string.leaderboard_cellsdescription));

        narrowSearchButton.setVisibility(GONE);
    }

    private void initRegionalLeaderboard() {
        if(!isGridView) {
            return;
        }
        isGridView = false;

        narrowSearchButton.setVisibility(VISIBLE);
    }

    @Override
    protected void onStart() {
        super.onStart();

        narrowSearchButton.setOnClickListener(v -> {
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
                copyAssetToInternalStorage(this, "shapefiles/global.shp", "global.shp");
                copyAssetToInternalStorage(this, "shapefiles/global.dbf", "global.dbf");
                copyAssetToInternalStorage(this, "shapefiles/global.shx", "global.shx");

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
                    copyAssetToInternalStorage(LeaderboardActivity.this, "shapefiles/" + continent + ".shp", continent + ".shp");
                    copyAssetToInternalStorage(LeaderboardActivity.this, "shapefiles/" + continent + ".dbf", continent + ".dbf");
                    copyAssetToInternalStorage(LeaderboardActivity.this, "shapefiles/" + continent + ".shx", continent + ".shx");

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
                    boolean success = copyAssetToInternalStorage(LeaderboardActivity.this, "shapefiles/" + countryCode + "_largeregion.shp", countryCode + "_largeregion.shp");
                    if(!success) {
                        return;
                    }

                    copyAssetToInternalStorage(LeaderboardActivity.this, "shapefiles/" + countryCode + "_largeregion.dbf", countryCode + "_largeregion.dbf");
                    copyAssetToInternalStorage(LeaderboardActivity.this, "shapefiles/" + countryCode + "_largeregion.shx", countryCode + "_largeregion.shx");

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
                    catch (Exception e) {
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
                    boolean success = copyAssetToInternalStorage(LeaderboardActivity.this, "shapefiles/" + countryCode + "_smallregion.shp", countryCode + "_smallregion.shp");
                    if(!success) {
                        return;
                    }

                    copyAssetToInternalStorage(LeaderboardActivity.this, "shapefiles/" + countryCode + "_smallregion.dbf", countryCode + "_smallregion.dbf");
                    copyAssetToInternalStorage(LeaderboardActivity.this, "shapefiles/" + countryCode + "_smallregion.shx", countryCode + "_smallregion.shx");

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
        });
    }

    // TODO: REFACTOR TO COPY ALL FILES AT ONCE (Which files are needed?)
    private boolean copyAssetToInternalStorage(Context context, String assetPath, String outputName) {
        try {
            File outFile = new File(context.getFilesDir(), outputName);
            //if(outFile.exists()) { TODO remove when done
            //    return true;
            //}

            try(InputStream in = context.getAssets().open(assetPath);
                OutputStream out = new FileOutputStream(outFile)) {
                byte[] buffer = new byte[1024];
                int read;

                while((read = in.read(buffer)) != -1) {
                    out.write(buffer, 0, read);
                }
            }

            return true;
        }
        catch(IOException e) {
            return false;
        }
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