package com.hu.sightseek.activity;

import static android.view.View.VISIBLE;

import android.content.Intent;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
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
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;
import com.hu.sightseek.R;
import com.hu.sightseek.adapter.LeaderboardCellEntryAdapter;
import com.hu.sightseek.model.LeaderboardEntry;

import org.osmdroid.config.Configuration;

import java.util.ArrayList;

public class LeaderboardActivity extends AppCompatActivity {
    private LeaderboardCellEntryAdapter cellAdapter;
    private ArrayList<LeaderboardEntry> cellEntries;
    private LeaderboardEntry myCellEntry;

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
            FirebaseAuth auth = FirebaseAuth.getInstance();
            String currentUid = auth.getCurrentUser().getUid();

            Task<QuerySnapshot> leaderboardTask = db.collection("leaderboard_cells")
                    .orderBy("cellsVisited", Query.Direction.DESCENDING)
                    .limit(100)
                    .get();

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

                if(userSnapshot.exists()) {
                    String username = userSnapshot.getString("username");
                    Long cellsVisitedHolder = userSnapshot.getLong("cellsVisited");
                    long cellsVisited = cellsVisitedHolder == null ? 0 : cellsVisitedHolder;
                    myCellEntry = new LeaderboardEntry(username, cellsVisited);

                    TextView myPlacingTextView = findViewById(R.id.leaderboard_myplacing);
                    myPlacingTextView.setText(getString(R.string.leaderboard_entry_placing, 0));

                    TextView myNameTextView = findViewById(R.id.leaderboard_myname);
                    myNameTextView.setText(myCellEntry.getUsername());

                    TextView myValueTextView = findViewById(R.id.leaderboard_myvalue);
                    myValueTextView.setText(getString(R.string.leaderboard_entry_cellvalue, (int) myCellEntry.getValue()));
                }

                cellAdapter = new LeaderboardCellEntryAdapter(this, cellEntries);
                recyclerView.setAdapter(cellAdapter);

                View myEntryView = findViewById(R.id.leaderboard_myentry);
                myEntryView.setVisibility(VISIBLE);

                View separator = findViewById(R.id.leaderboard_separator);
                separator.setVisibility(VISIBLE);
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