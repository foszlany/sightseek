package com.hu.sightseek.activity;

import static com.hu.sightseek.util.GenericUtils.hideKeyboard;

import android.content.Intent;
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

import com.google.firebase.auth.FirebaseAuth;
import com.hu.sightseek.R;
import com.hu.sightseek.adapter.IdeaAdapter;
import com.hu.sightseek.db.LocalDatabaseDAO;
import com.hu.sightseek.enums.SavedIdeaStatus;
import com.hu.sightseek.model.Idea;

import org.osmdroid.config.Configuration;

import java.util.ArrayList;

public class IdeaManagerActivity extends AppCompatActivity {
    private IdeaAdapter adapter;
    private ArrayList<Idea> ideas;
    private int checkedSortByMethod;
    private boolean isSavedChecked;
    private boolean isIgnoredChecked;
    private boolean isVisitedChecked;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_idea_manager);
        Configuration.getInstance().load(
                getApplicationContext(),
                PreferenceManager.getDefaultSharedPreferences(getApplicationContext())
        );
        Configuration.getInstance().setUserAgentValue(getPackageName());

        checkedSortByMethod = R.id.ideamanager_filtermenu_nameaz;
        isSavedChecked = true;
        isIgnoredChecked = true;
        isVisitedChecked = true;

        // Auth
        FirebaseAuth auth = FirebaseAuth.getInstance();
        if(auth.getCurrentUser() == null) {
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
        RecyclerView recyclerView = findViewById(R.id.ideamanager_ideas);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        LocalDatabaseDAO dao = new LocalDatabaseDAO(this);
        ideas = new ArrayList<>();
        ideas = dao.getAllIdeas();
        dao.close();

        adapter = new IdeaAdapter(this, ideas);
        recyclerView.setAdapter(adapter);

        // Searchbar
        SearchView searchView = findViewById(R.id.ideamanager_searchbar);
        searchView.setOnClickListener(v -> searchView.onActionViewExpanded());
        searchView.setQuery(adapter.getSearchQuery(), false);
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                hideKeyboard(IdeaManagerActivity.this);
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
        ImageButton filterButton = findViewById(R.id.ideamanager_filterbtn);
        filterButton.setOnClickListener(this::initFilterPopup);
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
        View popupView = LayoutInflater.from(this).inflate(R.layout.filter_idea, (ViewGroup) menuItemView.getParent(), false);
        PopupWindow popupWindow = new PopupWindow(popupView, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, true);
        popupWindow.showAsDropDown(menuItemView);

        RadioGroup radioGroup = popupView.findViewById(R.id.ideamanager_filtermenu_radiogroup);
        RadioButton previousSortByMethod = popupView.findViewById(checkedSortByMethod);
        previousSortByMethod.toggle();

        radioGroup.setOnCheckedChangeListener((group, checkedId) -> checkedSortByMethod = checkedId);

        CheckBox savedCheckBox = popupView.findViewById(R.id.ideamanager_filtermenu_saved);
        CheckBox ignoredCheckBox = popupView.findViewById(R.id.ideamanager_filtermenu_ignored);
        CheckBox visitedCheckBox = popupView.findViewById(R.id.ideamanager_filtermenu_visited);

        savedCheckBox.setChecked(isSavedChecked);
        ignoredCheckBox.setChecked(isIgnoredChecked);
        visitedCheckBox.setChecked(isVisitedChecked);

        Button applyButton = popupView.findViewById(R.id.ideamanager_filtermenu_applybtn);
        applyButton.setOnClickListener(v -> {
            // Sort by
            if(checkedSortByMethod == R.id.ideamanager_filtermenu_nameaz) {
                ideas.sort((a1, a2) -> a1.getName().compareTo(a2.getName()));
            }
            else if(checkedSortByMethod == R.id.ideamanager_filtermenu_nameza) {
                ideas.sort((a1, a2) -> a2.getName().compareTo(a1.getName()));
            }
            else if(checkedSortByMethod == R.id.ideamanager_filtermenu_placeaz) {
                ideas.sort((a1, a2) -> a1.getPlace().compareToIgnoreCase(a2.getPlace()));
            }
            else if(checkedSortByMethod == R.id.ideamanager_filtermenu_placeza) {
                ideas.sort((a1, a2) -> a2.getPlace().compareToIgnoreCase(a1.getPlace()));
            }

            // Categories
            isSavedChecked = savedCheckBox.isChecked();
            isIgnoredChecked = ignoredCheckBox.isChecked();
            isVisitedChecked = visitedCheckBox.isChecked();

            ArrayList<Idea> filtered = new ArrayList<>();
            if(!(!isSavedChecked && !isIgnoredChecked && !isVisitedChecked)) {
                for(Idea idea : ideas) {
                    if(idea.getStatus() == SavedIdeaStatus.SAVED && !isSavedChecked
                            || idea.getStatus() == SavedIdeaStatus.IGNORED && !isIgnoredChecked
                            || idea.getStatus() == SavedIdeaStatus.VISITED && !isVisitedChecked) {
                        continue;
                    }

                    filtered.add(idea);
                }
            }

            adapter.applyCategoryFilter(filtered);
            adapter.notifyDataSetChanged();

            popupWindow.dismiss();
        });
    }
}