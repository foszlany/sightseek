package com.hu.sightseek.activity;

import android.content.Intent;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.auth.FirebaseAuth;
import com.hu.sightseek.R;
import com.hu.sightseek.adapter.AttractionAdapter;
import com.hu.sightseek.db.LocalDatabaseDAO;
import com.hu.sightseek.model.Attraction;

import org.osmdroid.config.Configuration;

import java.util.ArrayList;

public class IdeaManagerActivity extends AppCompatActivity {
    private RecyclerView recyclerView;
    private AttractionAdapter adapter;
    private ArrayList<Attraction> attractions;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_idea_manager);
        Configuration.getInstance().load(
                getApplicationContext(),
                PreferenceManager.getDefaultSharedPreferences(getApplicationContext())
        );
        Configuration.getInstance().setUserAgentValue(getPackageName());

        // Auth
        FirebaseAuth mAuth = FirebaseAuth.getInstance();
        if(mAuth.getCurrentUser() == null) {
            finish();
        }

        // Add Menu
        Toolbar toolbar = findViewById(R.id.ideamanager_topmenu);
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

        // Setup adapter
        recyclerView = findViewById(R.id.ideamanager_ideas);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        LocalDatabaseDAO dao = new LocalDatabaseDAO(this);
        attractions = new ArrayList<>();
        attractions = dao.getAllAttractions();
        dao.close();

        adapter = new AttractionAdapter(this, attractions);
        recyclerView.setAdapter(adapter);

        // Searchbar
        SearchView searchView = findViewById(R.id.ideamanager_searchbar);
        searchView.setOnClickListener(v -> searchView.onActionViewExpanded());
        searchView.setQuery(adapter.getSearchQuery(), false);
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                adapter.getFilter().filter(query);
                return true;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                adapter.getFilter().filter(newText);
                return true;
            }
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
}