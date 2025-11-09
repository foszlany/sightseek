package com.hu.sightseek.activity;

import static android.view.View.VISIBLE;
import static com.hu.sightseek.utils.SightseekGenericUtils.createScreenshot;
import static com.hu.sightseek.utils.SightseekSpatialUtils.getBoundingBox;
import static com.hu.sightseek.utils.SightseekSpatialUtils.getVisitedCells;
import static com.hu.sightseek.utils.SightseekGenericUtils.setupRouteLine;
import static com.hu.sightseek.utils.SightseekGenericUtils.setupZoomSettings;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.hu.sightseek.R;
import com.hu.sightseek.db.LocalDatabaseDAO;
import com.hu.sightseek.model.Activity;
import com.hu.sightseek.utils.SightseekFirebaseUtils;
import com.hu.sightseek.utils.SightseekSpatialUtils;

import org.osmdroid.config.Configuration;
import org.osmdroid.util.BoundingBox;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.FolderOverlay;
import org.osmdroid.views.overlay.Polyline;
import org.osmdroid.views.overlay.TilesOverlay;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executors;

public class ActivityActivity extends AppCompatActivity {
    private Button processedDataButton;
    private Activity activity;
    private FolderOverlay vectorizedDataGroup;
    boolean isVectorizedDataVisible;

    @SuppressLint("ClickableViewAccessibility")
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

        isVectorizedDataVisible = false;
        processedDataButton = findViewById(R.id.activity_vectortogglebtn);

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
        mapView.setLayerType(View.LAYER_TYPE_HARDWARE, null);

        setupZoomSettings(mapView, 14.0);

        TilesOverlay tilesOverlay = mapView.getOverlayManager().getTilesOverlay();
        tilesOverlay.setLoadingBackgroundColor(Color.TRANSPARENT);
        tilesOverlay.setLoadingLineColor(Color.TRANSPARENT);

        // Setup polyline
        String polylineString = activity.getPolyline();
        List<GeoPoint> pointList = SightseekSpatialUtils.decode(polylineString);
        Polyline polyline = new Polyline();
        for(GeoPoint point : pointList) {
            polyline.addPoint(point);
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

                        if(mAuth.getCurrentUser() == null) {
                            if(activity.getStravaId() != -1) {
                                Toast.makeText(this, "Imported activities cannot be deleted while offline.", Toast.LENGTH_LONG).show();
                                return;
                            }
                        }
                        else {
                            HashMap<String, Integer> cells = getVisitedCells(SightseekSpatialUtils.decode(polylineString));
                            SightseekFirebaseUtils.updateCellsInFirebase(mAuth, cells, true);
                        }

                        LocalDatabaseDAO dao2 = new LocalDatabaseDAO(this);
                        dao2.deleteActivity(activityId);
                        dao2.close();

                        Intent intent = new Intent(this, MainActivity.class);
                        startActivity(intent);
                        finish();
                    })
                    .setNegativeButton("No", (d, which) -> d.dismiss())
                    .setCancelable(true)
                    .create();

            dialog.show();
        });

        // Processed data button
        if(FirebaseAuth.getInstance().getCurrentUser() != null) {
            processedDataButton.setVisibility(VISIBLE);

            processedDataButton.setOnClickListener(v -> {
                isVectorizedDataVisible = !isVectorizedDataVisible;

                if(vectorizedDataGroup == null) {
                    vectorizedDataGroup = new FolderOverlay();

                    Paint paint = new Paint();
                    paint.setColor(Color.parseColor("#FF0000"));
                    paint.setStrokeWidth(7.0f);
                    paint.setAntiAlias(false);

                    Executors.newSingleThreadExecutor().execute(() -> {
                        String vectorizedDataString = activity.getVectorizedData();
                        String[] vectorizedDataArray = vectorizedDataString.split(";");

                        for(String encodedRoute : vectorizedDataArray) {
                            List<GeoPoint> geoPoints = SightseekSpatialUtils.decode(encodedRoute);

                            Polyline p = new Polyline();
                            p.setPoints(geoPoints);
                            p.getOutlinePaint().set(paint);

                            vectorizedDataGroup.add(p);
                        }

                        mapView.getOverlays().add(1, vectorizedDataGroup);

                        runOnUiThread(() -> {
                            mapView.invalidate();
                            updateProcessedDataButton(isVectorizedDataVisible);
                        });
                    });
                }
                else {
                    vectorizedDataGroup.setEnabled(isVectorizedDataVisible);
                    mapView.invalidate();

                    updateProcessedDataButton(isVectorizedDataVisible);
                }
            });
        }

        // Screenshot button
        ImageButton screenshotButton = findViewById(R.id.activity_screenshotbtn);
        screenshotButton.setOnClickListener(v ->
                createScreenshot(this, findViewById(R.id.activity_layoutcontainer), activity.getName().replace(" ", "_"), findViewById(R.id.activity_btns))
        );

        // Prevent unzoomable map
        ScrollView scrollView = findViewById(R.id.activity_scrollview);
        mapView.setOnTouchListener((v, event) -> {
            scrollView.requestDisallowInterceptTouchEvent(true);
            return false;
        });
    }

    private void updateProcessedDataButton(boolean enable) {
        processedDataButton.setText(enable ? R.string.activity_hideprocesseddata : R.string.activity_showprocesseddata);

        int tintColor = ContextCompat.getColor(this, enable ? R.color.red : R.color.light_purple);
        processedDataButton.setBackgroundTintList(ColorStateList.valueOf(tintColor));
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