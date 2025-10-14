package com.hu.sightseek.activity;

import static com.hu.sightseek.utils.SightseekGenericUtils.createScreenshot;
import static com.hu.sightseek.utils.SightseekGenericUtils.getBoundingBox;
import static com.hu.sightseek.utils.SightseekGenericUtils.getVisitedCells;
import static com.hu.sightseek.utils.SightseekGenericUtils.setupRouteLine;
import static com.hu.sightseek.utils.SightseekGenericUtils.setupZoomSettings;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.ViewTreeObserver;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.google.android.gms.maps.model.LatLng;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.maps.android.PolyUtil;
import com.hu.sightseek.R;
import com.hu.sightseek.db.LocalDatabaseDAO;
import com.hu.sightseek.model.Activity;

import org.osmdroid.config.Configuration;
import org.osmdroid.util.BoundingBox;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Polyline;
import org.osmdroid.views.overlay.TilesOverlay;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;

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
        Toolbar toolbar = findViewById(R.id.activity_topmenu);
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
        LocalDatabaseDAO dao = new LocalDatabaseDAO(this);
        activity = dao.getActivity(activityId);
        dao.close();

        if(activity == null) {
            Intent intent = new Intent(this, MainActivity.class);
            startActivity(intent);
            finish();
            return;
        }

        // Set views
        TextView titleTextView = findViewById(R.id.activity_title);
        titleTextView.setText(activity.getName());

        String startTime = activity.getStarttime().replace("T", ". ").replace("-", ".");
        TextView dateTextView = findViewById(R.id.activity_date);
        dateTextView.setText(startTime);

        TextView categoryTextView = findViewById(R.id.activity_category);
        categoryTextView.setText(activity.getCategory().toShortString());

        double elapsedTime = activity.getElapsedtime();
        int hours = (int) elapsedTime / 3600;
        int minutes = ((int) elapsedTime % 3600) / 60;
        int seconds = (int) elapsedTime % 60;
        String formattedTime = String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds);

        TextView timeTextView = findViewById(R.id.activity_elapsedtime);
        timeTextView.setText(formattedTime);

        TextView distanceTextView = findViewById(R.id.activity_distance);
        distanceTextView.setText(String.format(Locale.US, "%.2f km", activity.getDistance() / 1000.0));

        // Setup map
        // Initialize mapview
        MapView mapView = findViewById(R.id.activity_map);
        mapView.setBackgroundColor(Color.TRANSPARENT);
        mapView.setUseDataConnection(true);

        setupZoomSettings(mapView, 14.0);

        TilesOverlay tilesOverlay = mapView.getOverlayManager().getTilesOverlay();
        tilesOverlay.setLoadingBackgroundColor(Color.TRANSPARENT);
        tilesOverlay.setLoadingLineColor(Color.TRANSPARENT);

        // Setup polyline
        String polylineString = activity.getPolyline();
        List<LatLng> pointList = PolyUtil.decode(polylineString);
        Polyline polyline = new Polyline();
        for(LatLng point : pointList) {
            polyline.addPoint(new GeoPoint(point.latitude, point.longitude));
        }

        setupRouteLine(polyline, false);
        mapView.getOverlayManager().add(polyline);

        // Calculate bounding box
        BoundingBox box = getBoundingBox(pointList);

        // Set zoom based on bounding box
        mapView.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                mapView.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                mapView.zoomToBoundingBox(box.increaseByScale(1.4f), false);
            }
        });

        // Delete button
        ImageButton deleteButton = findViewById(R.id.activity_deletebtn);
        deleteButton.setOnClickListener(v -> {
            AlertDialog dialog = new AlertDialog.Builder(this)
                    .setTitle("Confirmation")
                    .setMessage("Are you sure you want to delete this activity? This cannot be undone!")
                    .setPositiveButton("Yes", (d, which) -> {
                        FirebaseAuth mAuth = FirebaseAuth.getInstance();

                        if(mAuth.getCurrentUser() == null && activity.getStravaId() != -1) {
                            Toast.makeText(this, "Imported activities cannot be deleted while offline.", Toast.LENGTH_LONG).show();
                            return;
                        }

                        LocalDatabaseDAO dao2 = new LocalDatabaseDAO(this);
                        dao2.deleteActivity(activityId);
                        dao2.close();

                        if(mAuth.getCurrentUser() != null) {
                            HashMap<String, Integer> cells = getVisitedCells(PolyUtil.decode(polylineString));

                            String uid = mAuth.getUid();
                            FirebaseFirestore.getInstance().collection("users")
                                    .document(uid)
                                    .get()
                                    .addOnSuccessListener(documentSnapshot -> {
                                        HashMap<String, Long> firestoreMap = (HashMap<String, Long>) documentSnapshot.get("visitedCells");
                                        HashMap<String, Object> newMap = new HashMap<>();

                                        for(HashMap.Entry<String, Integer> entry : cells.entrySet()) {
                                            String key = entry.getKey();
                                            long subtractValue = entry.getValue();
                                            Long currentValue = firestoreMap.get(key);

                                            if(currentValue != null) {
                                                long newValue = Math.max(0, currentValue - subtractValue);
                                                newMap.put("visitedCells." + key, newValue);
                                            }
                                        }

                                        FirebaseFirestore.getInstance().collection("users")
                                                .document(uid)
                                                .update(newMap);
                                    });
                        }

                        Intent intent = new Intent(this, MainActivity.class);
                        startActivity(intent);
                        finish();
                    })
                    .setNegativeButton("No", (d, which) -> d.dismiss())
                    .setCancelable(true)
                    .create();

            dialog.show();
        });

        ImageButton screenshotButton = findViewById(R.id.activity_screenshotbtn);
        screenshotButton.setOnClickListener(v -> createScreenshot(this, findViewById(R.id.activity_layoutcontainer), activity.getName().replace(" ", "_"), findViewById(R.id.activity_btns)));
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

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }
}