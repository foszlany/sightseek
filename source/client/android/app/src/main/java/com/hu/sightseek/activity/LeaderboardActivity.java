package com.hu.sightseek.activity;

import android.content.Intent;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.hu.sightseek.R;
import com.hu.sightseek.adapter.LeaderboardCellEntryAdapter;
import com.hu.sightseek.db.LocalDatabaseDAO;
import com.hu.sightseek.model.LeaderboardEntry;

import org.osmdroid.config.Configuration;

import java.util.ArrayList;

public class LeaderboardActivity extends AppCompatActivity {
    private LeaderboardCellEntryAdapter cellAdapter;
    private ArrayList<LeaderboardEntry> cellEntries;

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
            runOnUiThread(() -> {
                startActivity(new Intent(this, BannerActivity.class));
                finish();
            });
            return;
        }

        cellEntries = new ArrayList<>();

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
                // TODO
            }
            else if(id == R.id.bottommenu_leaderboard_distance) {
                // TODO
            }

            return true;
        });

        initCellLeaderboard();
    }

    private void initCellLeaderboard() {
        RecyclerView recyclerView = findViewById(R.id.leaderboard_entries);

        if(cellAdapter == null) {
            recyclerView.setLayoutManager(new LinearLayoutManager(this));

            FirebaseFirestore db = FirebaseFirestore.getInstance();
            db.collection("leaderboard_cells")
                    .orderBy("cellsVisited", Query.Direction.DESCENDING)
                    .limit(100)
                    .get()
                    .addOnCompleteListener(task -> {
                        if(task.isSuccessful()) {
                            for(QueryDocumentSnapshot document : task.getResult()) {
                                String username = document.getString("username");
                                Long cellsVisitedHolder = document.getLong("cellsVisited");
                                long cellsVisited = cellsVisitedHolder == null ? 0 : cellsVisitedHolder;

                                cellEntries.add(new LeaderboardEntry(username, cellsVisited));
                                cellAdapter = new LeaderboardCellEntryAdapter(this, cellEntries);
                                recyclerView.setAdapter(cellAdapter);
                            }
                        }
                        else {
                            Toast.makeText(this, "Couldn't reach servers, please try again later.", Toast.LENGTH_LONG).show();
                            finish();
                        }
                    });
        }
        else {
            recyclerView.setAdapter(cellAdapter);
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