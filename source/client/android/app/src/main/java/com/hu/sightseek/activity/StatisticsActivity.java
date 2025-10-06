package com.hu.sightseek.activity;

import static android.view.View.INVISIBLE;
import static android.view.View.VISIBLE;
import static com.hu.sightseek.utils.SightseekUtils.createScreenshot;
import static com.hu.sightseek.utils.SightseekUtils.getDetailedGenericStatistics;
import static com.hu.sightseek.utils.SightseekUtils.getLocationString;

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
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.google.firebase.auth.FirebaseAuth;
import com.hu.sightseek.R;
import com.hu.sightseek.enums.TravelCategory;
import com.hu.sightseek.db.LocalDatabaseDAO;

import org.osmdroid.config.Configuration;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Locale;
import java.util.Random;
import java.util.concurrent.Executors;

public class StatisticsActivity extends AppCompatActivity {
    // TODO: REFACTOR THESE INTO ONE BIG HASHMAP
    private double totalDistance;
    private double totalDays;
    private TravelCategory mainCategory;
    private double longestDistance;
    private double longestTime;
    private String longestTimeText;
    private double activityCount;
    private double importedCount;
    private boolean isCardView;
    private HashMap<String, Serializable> detailedGenericStatistics;

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

        // Add Menu
        Toolbar toolbar = findViewById(R.id.statistics_topmenu);
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

        // Variables
        LocalDatabaseDAO dao = new LocalDatabaseDAO(this);

        HashMap<String, Double> baseStatistics = dao.getBaseStatistics();
        detailedGenericStatistics = new HashMap<>();
        mainCategory = dao.getMainTravelCategory();

        dao.close();

        if(baseStatistics == null || mainCategory == null) {
            LayoutInflater inflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            ViewGroup root = findViewById(android.R.id.content);
            View overlayView = inflater.inflate(R.layout.overlay_noactivities, root, false);

            root.addView(overlayView);
            toolbar.post(() -> {
                FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
                params.topMargin = toolbar.getHeight();
                overlayView.setLayoutParams(params);
            });
            return;
        }

        Double valueHolder;

        valueHolder = baseStatistics.get("total_distance");
        totalDistance = (valueHolder != null) ? (valueHolder / 1000.0) : 0.0;

        valueHolder = baseStatistics.get("total_time");
        totalDays = (valueHolder != null) ? (valueHolder / 86400.0) : 0.0;

        valueHolder = baseStatistics.get("longest_distance");
        longestDistance = (valueHolder != null) ? (valueHolder / 1000.0) : 0.0;

        valueHolder = baseStatistics.get("longest_time");
        longestTime = (valueHolder != null) ? valueHolder : 0.0;

        valueHolder = baseStatistics.get("activity_count");
        activityCount = (valueHolder != null) ? valueHolder : 0.0;

        valueHolder = baseStatistics.get("imported_count");
        importedCount = (valueHolder != null) ? valueHolder : 0.0;

        initCardView();

        // Listeners
        ImageButton cardViewButton = findViewById(R.id.statistics_nav_cardbtn);
        cardViewButton.setOnClickListener(v -> initCardView());

        ImageButton detailViewButton = findViewById(R.id.statistics_nav_detailedbtn);
        detailViewButton.setOnClickListener(v -> initDetailedView());

        ImageButton screenshotButton = findViewById(R.id.statistics_screenshotbtn);
        screenshotButton.setOnClickListener(v -> createScreenshot(this, isCardView ? findViewById(R.id.statistics_cardcontainer) : findViewById(R.id.statistics_detailedcontainer), "MyStatistics", null));
    }

    public void initCardView() {
        if(isCardView) {
            return;
        }
        isCardView = true;

        // Inflate view
        ViewGroup container = findViewById(R.id.statistics_container);
        View detailedView = container.findViewById(R.id.statistics_detailedcontainer);
        if(detailedView != null) {
            container.removeView(detailedView);
        }
        View cardView = LayoutInflater.from(this).inflate(R.layout.activity_statistics_cardview, container, false);
        container.addView(cardView);

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

        TextView totalDistanceTextView = findViewById(R.id.statistics_distancecard_distancevalue);
        TextView totalDistanceContextTextView = findViewById(R.id.statistics_distancecard_incontext);
        totalDistanceTextView.setText(getString(R.string.statistics_distancecard_distancevalue, totalDistance));
        switch(rnd.nextInt(5)) {
            case 0: // Big Mac
                totalDistanceContextTextView.setText(getString(R.string.statistics_distancecard_randomcontext1, (float) Math.round(totalDistance / 0.0001016)));
                break;

            case 1: // Polar bear
                totalDistanceContextTextView.setText(getString(R.string.statistics_distancecard_randomcontext2, totalDistance / 0.0026));
                break;

            case 2: // Football fields
                totalDistanceContextTextView.setText(getString(R.string.statistics_distancecard_randomcontext3, totalDistance / 0.11));
                break;

            case 3: // Liechtenstein
                totalDistanceContextTextView.setText(getString(R.string.statistics_distancecard_randomcontext4, totalDistance / 26.0));
                break;

            case 4: // Moon
                totalDistanceContextTextView.setText(getString(R.string.statistics_distancecard_randomcontext5, totalDistance / 3475.0));
                break;
        }

        TextView totalTimeTextView = findViewById(R.id.statistics_timecard_timevalue);
        TextView totalTimeContextTextView = findViewById(R.id.statistics_timecard_incontext);
        totalTimeTextView.setText(getString(R.string.statistics_timecard_timevalue, totalDays));
        if(totalDays < 1) {
            totalTimeContextTextView.setText(getString(R.string.statistics_timecard_context1));
        }
        else if(totalDays < 7) {
            totalTimeContextTextView.setText(getString(R.string.statistics_timecard_context2));
        }
        else if(totalDays < 30) {
            totalTimeContextTextView.setText(getString(R.string.statistics_timecard_context3));
        }
        else if(totalDays < 180) {
            totalTimeContextTextView.setText(getString(R.string.statistics_timecard_context4));
        }
        else if(totalDays < 365) {
            totalTimeContextTextView.setText(getString(R.string.statistics_timecard_context5));
        }
        else {
            totalTimeContextTextView.setText(getString(R.string.statistics_timecard_context6));
        }

        TextView mainCategoryTextView = findViewById(R.id.statistics_categorycard_categoryvalue);
        TextView mainCategoryContextTextView = findViewById(R.id.statistics_categorycard_incontext);
        mainCategoryTextView.setText(mainCategory.toShortString());
        switch(mainCategory) {
            case LOCOMOTOR:
                mainCategoryContextTextView.setText(getString(R.string.statistics_categorycard_locomotor));
                break;

            case MICROMOBILITY:
                mainCategoryContextTextView.setText(getString(R.string.statistics_categorycard_micromobility));
                break;

            case OTHER:
                mainCategoryContextTextView.setText(getString(R.string.statistics_categorycard_other));
                break;

            case INVALID:
                mainCategoryContextTextView.setText(getString(R.string.statistics_categorycard_default));
        }

        TextView longestDistanceTextView = findViewById(R.id.statistics_topcard_distancevalue);
        longestDistanceTextView.setText(getString(R.string.main_distancevalue, longestDistance));

        TextView longestTimeTextView = findViewById(R.id.statistics_topcard_timevalue);
        longestTimeText = String.format(Locale.US, "%02d:%02d:%02d", (int) longestTime / 3600, ((int) longestTime % 3600) / 60, (int) longestTime % 60);
        longestTimeTextView.setText(longestTimeText);
    }

    public void initDetailedView() {
        if(!isCardView) {
            return;
        }
        isCardView = false;

        // Inflate view
        ViewGroup container = findViewById(R.id.statistics_container);
        View cardView = container.findViewById(R.id.statistics_cardcontainer);
        if(cardView != null) {
            container.removeView(cardView);
        }
        View detailedView = LayoutInflater.from(this).inflate(R.layout.activity_statistics_detailedview, container, false);
        detailedView.setVisibility(INVISIBLE);
        container.addView(detailedView);

        Executors.newSingleThreadExecutor().execute(() -> {
            if(detailedGenericStatistics.isEmpty()) {
                detailedGenericStatistics = getDetailedGenericStatistics(this);
            }

            runOnUiThread(() -> {
                TextView totalActivitiesTextView = findViewById(R.id.statistics_generalcard_activitiescreated);
                totalActivitiesTextView.setText(
                        getString(R.string.statistics_generalcard_activitiescreated, activityCount)
                );

                TextView totalPointsTextView = findViewById(R.id.statistics_generalcard_totalpoints);
                totalPointsTextView.setText(
                        getString(R.string.statistics_generalcard_totalpoints, (Double)detailedGenericStatistics.get("total_points"))
                );

                TextView totalKilometersTextView = findViewById(R.id.statistics_generalcard_totalkilometers);
                totalKilometersTextView.setText(
                        getString(R.string.statistics_generalcard_totalkilometers, totalDistance)
                );

                TextView totalTimeTextView = findViewById(R.id.statistics_generalcard_totaltime);
                totalTimeTextView.setText(
                        getString(R.string.statistics_generalcard_totaltime, totalDays)
                );

                TextView averageSpeedTextView = findViewById(R.id.statistics_generalcard_averagespeed);
                averageSpeedTextView.setText(
                        getString(R.string.statistics_generalcard_averagespeed, totalDistance / (totalDays * 24))
                );

                TextView mainActivityTypeTextView = findViewById(R.id.statistics_generalcard_mainactivitytype);
                mainActivityTypeTextView.setText(
                        getString(R.string.statistics_generalcard_mainactivitytype, mainCategory.toShortString())
                );

                // todo invis if -1
                TextView visitedCellsTextView = findViewById(R.id.statistics_generalcard_visitedcells);
                visitedCellsTextView.setText(
                        getString(R.string.statistics_generalcard_visitedcells, (Double)detailedGenericStatistics.get("visited_cells"))
                );

                TextView longestDistanceTextView = findViewById(R.id.statistics_generalcard_longestdistance);
                longestDistanceTextView.setText(
                        getString(R.string.statistics_generalcard_longestdistance, longestDistance)
                );

                TextView longestTimeTextView = findViewById(R.id.statistics_generalcard_longesttime);
                longestTimeTextView.setText(
                        getString(R.string.statistics_generalcard_longesttime, longestTimeText)
                );

                Double medianLatitude = (Double)detailedGenericStatistics.get("median_lat");
                Double medianLongitude = (Double)detailedGenericStatistics.get("median_lon");
                if(medianLatitude == null || medianLongitude == null) {
                    throw new ClassCastException();
                }

                TextView medianPointTextView = findViewById(R.id.statistics_generalcard_medianpoint);
                medianPointTextView.setText(
                        getString(R.string.statistics_generalcard_medianpoint, medianLatitude, medianLongitude, getLocationString(this, medianLatitude, medianLongitude))
                );

                Double isolatedLatitude = (Double)detailedGenericStatistics.get("isolated_lat");
                Double isolatedLongitude = (Double)detailedGenericStatistics.get("isolated_lon");
                if(isolatedLatitude == null || isolatedLongitude == null) {
                    throw new ClassCastException();
                }

                TextView isolatedPointTextView = findViewById(R.id.statistics_generalcard_isolatedpoint);
                isolatedPointTextView.setText(
                        getString(R.string.statistics_generalcard_isolatedpoint, isolatedLatitude, isolatedLongitude, getLocationString(this, isolatedLatitude, isolatedLongitude))
                );

                TextView importedActivitiesTextView = findViewById(R.id.statistics_generalcard_importedactivities);
                importedActivitiesTextView.setText(
                        getString(R.string.statistics_generalcard_importedactivities, importedCount)
                );

                // Anim
                Animation slideToRightAnim = AnimationUtils.loadAnimation(this, R.anim.fade_slide_toright);

                View statistics_generalcontainer = findViewById(R.id.statistics_generalcontainer);
                statistics_generalcontainer.startAnimation(slideToRightAnim);

                detailedView.setVisibility(VISIBLE);
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

        // Profile
        if(id == R.id.topmenu_profile) {
            Intent intent = new Intent(this, ProfileActivity.class);
            startActivity(intent);
            return true;
        }

        return super.onOptionsItemSelected(item);
    }
}