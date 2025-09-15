package com.hu.sightseek.activity;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.google.firebase.auth.FirebaseAuth;
import com.hu.sightseek.R;
import com.hu.sightseek.TravelCategory;
import com.hu.sightseek.db.LocalActivityDatabaseDAO;

import org.osmdroid.config.Configuration;

import java.util.HashMap;
import java.util.Locale;
import java.util.Random;

public class StatisticsActivity extends AppCompatActivity {
    private ImageButton cardViewButton;
    private ImageButton detailViewButton;

    // Card view data
    private double totalDistance;
    private double totalDays;
    private TravelCategory mainCategory;
    private double longestDistance;
    private double longestTime;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_statistics);
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

        // Variables
        LocalActivityDatabaseDAO dao = new LocalActivityDatabaseDAO(this);
        HashMap<String, Double> cardMap = dao.getStatistics();
        if(cardMap == null) {
            // TODO
            Intent intent = new Intent(this, MainActivity.class);
            startActivity(intent);
            finish();
            return;
        }

        Double valueHolder;

        valueHolder = cardMap.get("total_distance");
        totalDistance = (valueHolder != null) ? (valueHolder / 1000.0) : 0.0;

        valueHolder = cardMap.get("total_time");
        totalDays = (valueHolder != null) ? (valueHolder / 86400.0) : 0.0;

        valueHolder = cardMap.get("longest_distance");
        longestDistance = (valueHolder != null) ? (valueHolder / 1000.0) : 0.0;

        valueHolder = cardMap.get("longest_time");
        longestTime = (valueHolder != null) ? valueHolder : 0.0;

        cardViewButton = findViewById(R.id.statistics_nav_cardbtn);
        detailViewButton = findViewById(R.id.statistics_nav_detailedbtn);

        // Add Menu
        Toolbar toolbar = findViewById(R.id.menubar_statistics);
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

        // Init cardview
        initCardView();
    }

    public void initCardView() {
        // Animations
        Animation slideToRightAnim = AnimationUtils.loadAnimation(this, R.anim.fade_slide_toright);
        Animation slideToLeftAnim = AnimationUtils.loadAnimation(this, R.anim.fade_slide_toleft);
        Animation slideToUpAnim = AnimationUtils.loadAnimation(this, R.anim.fade_slide_toup);

        View distanceCardView = findViewById(R.id.statistics_distancecard);
        distanceCardView.startAnimation(slideToRightAnim);

        View timeCardView = findViewById(R.id.statistics_timecard);
        timeCardView.startAnimation(slideToLeftAnim);

        View categoryCardView = findViewById(R.id.statistics_categorycard);
        categoryCardView.startAnimation(slideToRightAnim);

        View topCardView = findViewById(R.id.statistics_topcard);
        topCardView.startAnimation(slideToUpAnim);

        // Set values
        Random rnd = new Random();

        TextView distanceTextView = findViewById(R.id.statistics_distancecard_distancevalue);
        TextView distanceContextTextView = findViewById(R.id.statistics_distancecard_incontext);
        distanceTextView.setText(getString(R.string.statistics_distancecard_distancevalue, totalDistance));
        switch(rnd.nextInt(5)) {
            case 0: // Big Mac
                distanceContextTextView.setText(getString(R.string.statistics_distancecard_randomcontext1, (float) Math.round(totalDistance / 0.0001016)));
                break;

            case 1: // Polar bear
                distanceContextTextView.setText(getString(R.string.statistics_distancecard_randomcontext2, totalDistance / 0.0026));
                break;

            case 2: // Football fields
                distanceContextTextView.setText(getString(R.string.statistics_distancecard_randomcontext3, totalDistance / 0.11));
                break;

            case 3: // Liechtenstein
                distanceContextTextView.setText(getString(R.string.statistics_distancecard_randomcontext4, totalDistance / 26.0));
                break;

            case 4: // Moon
                distanceContextTextView.setText(getString(R.string.statistics_distancecard_randomcontext5, totalDistance / 3475.0));
                break;
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

        // Profile
        if(id == R.id.topmenu_profile) {
            Intent intent = new Intent(this, ProfileActivity.class);
            startActivity(intent);
            return true;
        }

        return super.onOptionsItemSelected(item);
    }
}