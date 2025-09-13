package com.hu.sightseek.activity;

import android.content.Intent;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.hu.sightseek.R;
import com.hu.sightseek.db.LocalActivityDatabaseDAO;
import com.hu.sightseek.model.Activity;

import org.osmdroid.config.Configuration;

public class ActivityActivity extends AppCompatActivity {
    private Activity activity;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_activity);
        Configuration.getInstance().load(
                getApplicationContext(),
                PreferenceManager.getDefaultSharedPreferences(getApplicationContext())
        );
        Configuration.getInstance().setUserAgentValue(getPackageName());

        // Add Menu
        Toolbar toolbar = findViewById(R.id.menubar_activity);
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

        // Retrieve data
        Bundle extras = getIntent().getExtras();

        if(extras == null || !extras.containsKey("id")) {
            Intent intent = new Intent(this, MainActivity.class);
            startActivity(intent);
            finish();
            return;
        }

        int activityId = extras.getInt("id");

        // Get activity
        LocalActivityDatabaseDAO dao = new LocalActivityDatabaseDAO(this);
        activity = dao.getActivity(activityId);

        if(activity == null) {
            Intent intent = new Intent(this, MainActivity.class);
            startActivity(intent);
            finish();
            return;
        }

        // Set views
        TextView titleTextView = findViewById(R.id.activity_title);
        titleTextView.setText(activity.getName());
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
}