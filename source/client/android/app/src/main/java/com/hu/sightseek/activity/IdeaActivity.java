package com.hu.sightseek.activity;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.telecom.Call;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.RadioGroup;
import android.widget.SeekBar;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.google.android.gms.common.api.Response;
import com.google.android.gms.maps.model.LatLng;
import com.google.firebase.auth.FirebaseAuth;
import com.google.maps.android.PolyUtil;
import com.hu.sightseek.R;
import com.hu.sightseek.db.LocalActivityDatabaseDAO;
import com.hu.sightseek.model.Activity;
import com.hu.sightseek.utils.SightseekUtils;

import org.osmdroid.config.Configuration;
import org.osmdroid.util.BoundingBox;
import org.osmdroid.views.CustomZoomButtonsController;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.TilesOverlay;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collections;

import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;

public class IdeaActivity extends AppCompatActivity {
    private static final String overpassUrl = "https://overpass-api.de/api/interpreter";

    private LocalActivityDatabaseDAO dao;
    private ArrayList<Activity> activities;
    private LatLng medianPoint;
    private BoundingBox boundingBox;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_idea);
        Configuration.getInstance().load(
                getApplicationContext(),
                PreferenceManager.getDefaultSharedPreferences(getApplicationContext())
        );
        Configuration.getInstance().setUserAgentValue(getPackageName());

        // Auth
        FirebaseAuth mAuth = FirebaseAuth.getInstance();
        if(mAuth.getCurrentUser() == null) {
            Intent intent = new Intent(this, BannerActivity.class);
            startActivity(intent);
            finish();
        }

        medianPoint = null;
        boundingBox = null;

        // Add Menu
        Toolbar toolbar = findViewById(R.id.idea_topmenu);
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

        // Check if there's at least one stored activity
        dao = new LocalActivityDatabaseDAO(this);
        activities = dao.getAllActivities();
        if(activities.isEmpty()) {
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

        // Setup map
        MapView mapView = findViewById(R.id.idea_map);
        mapView.setBackgroundColor(Color.TRANSPARENT);
        mapView.setMultiTouchControls(true);
        mapView.setUseDataConnection(true);

        TilesOverlay tilesOverlay = mapView.getOverlayManager().getTilesOverlay();
        tilesOverlay.setLoadingBackgroundColor(Color.TRANSPARENT);
        tilesOverlay.setLoadingLineColor(Color.TRANSPARENT);

        mapView.getController().setZoom(10.0);
        mapView.setMinZoomLevel(3.0);
        mapView.setMaxZoomLevel(20.0);

        mapView.getZoomController().setVisibility(CustomZoomButtonsController.Visibility.NEVER);
        mapView.setVerticalMapRepetitionEnabled(false);

        findAttraction();

        // Ignore
        Button ignoreButton = findViewById(R.id.idea_ignorebtn);
        ignoreButton.setOnClickListener(v -> {
            // TODO
            findAttraction();
        });

        // Next
        Button nextButton = findViewById(R.id.idea_nextbtn);
        nextButton.setOnClickListener(v -> {
            findAttraction();
        });
    }

    public void findAttraction() {
        int radius;
        LatLng referencePoint = new LatLng(0, 0);

        SeekBar radiusBar = findViewById(R.id.idea_radiusbar);
        radius = radiusBar.getProgress();

        // Get values
        RadioGroup referenceRadioGroup = findViewById(R.id.idea_radiogroup);
        int checkedId = referenceRadioGroup.getCheckedRadioButtonId();

        // Current location
        if(checkedId == R.id.idea_radio_locationbtn) {

        }

        // Median point
        else if(checkedId == R.id.idea_radio_medianbtn && medianPoint == null) {
            ArrayList<Double> latPoints = new ArrayList<>();
            ArrayList<Double> lonPoints = new ArrayList<>();

            for(int i = 0; i < activities.size(); i++) {
                ArrayList<LatLng> points = new ArrayList<>(PolyUtil.decode(activities.get(i).getPolyline()));

                for(LatLng p : points) {
                    latPoints.add(p.latitude);
                    lonPoints.add(p.longitude);
                }
            }

            Collections.sort(latPoints);
            Collections.sort(lonPoints);

            double medianX = latPoints.get(latPoints.size() / 2);
            double medianY = lonPoints.get(lonPoints.size() / 2);

            medianPoint = new LatLng(medianX, medianY);

            referencePoint = medianPoint;
        }

        // Bounding box
        else if(checkedId == R.id.idea_radio_boundingboxbtn && boundingBox == null) {
            ArrayList<LatLng> allPoints = new ArrayList<>();

            for(int i = 0; i < activities.size(); i++) {
                ArrayList<LatLng> points = new ArrayList<>(PolyUtil.decode(activities.get(i).getPolyline()));

                allPoints.addAll(points);
            }
            
            boundingBox = SightseekUtils.getBoundingBox(allPoints);
            referencePoint = new LatLng(boundingBox.getCenterLatitude(), boundingBox.getCenterLongitude()); // TODO: Change?
        }

        // Query
        try {
            String query = "[out:json][timeout:5];"
                    + "node[\"tourism\"](around:" + radius * 1000 + "," + referencePoint.latitude + "," + referencePoint.longitude + ");"
                    + "out body;";
            String url = overpassUrl + "?data=" + URLEncoder.encode(query, "UTF-8");

            OkHttpClient client = new OkHttpClient();
            Request request = new Request.Builder()
                    .url(url)
                    .header("User-Agent", "com.hu.sightseek (nineforget42@gmail.com")
                    .build();

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onResponse(@NonNull okhttp3.Call call, @NonNull okhttp3.Response response) throws IOException {
                    if(!response.isSuccessful()) {
                        Toast.makeText(IdeaActivity.this, "Could not fetch data.", Toast.LENGTH_LONG).show();
                    }
                    else if(response.body() == null) {
                        Toast.makeText(IdeaActivity.this, "Nothing was found. Try increasing the radius.", Toast.LENGTH_LONG).show(); // TODO CHANGE
                    }
                    else {
                        System.out.println(response.body().string());
                    }
                }

                @Override
                public void onFailure(@NonNull okhttp3.Call call, @NonNull IOException e) {
                    Toast.makeText(IdeaActivity.this, "Unable to reach server. Please try again later.", Toast.LENGTH_LONG).show();
                }
            });
        }
        catch(UnsupportedEncodingException e) {
            Toast.makeText(IdeaActivity.this, "Unsupported encode exception, terminating query.", Toast.LENGTH_LONG).show();
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