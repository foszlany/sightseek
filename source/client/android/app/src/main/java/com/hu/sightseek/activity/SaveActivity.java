package com.hu.sightseek.activity;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.maps.model.LatLng;
import com.google.maps.android.PolyUtil;
import com.hu.sightseek.R;
import com.hu.sightseek.TravelCategory;
import com.hu.sightseek.db.LocalActivityDatabaseDAO;

import org.osmdroid.config.Configuration;
import org.osmdroid.util.BoundingBox;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.CustomZoomButtonsController;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Polyline;
import org.osmdroid.views.overlay.TilesOverlay;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SaveActivity extends AppCompatActivity {
    private String title;
    private TravelCategory categoryIndex;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_save);
        Configuration.getInstance().load(
                getApplicationContext(),
                PreferenceManager.getDefaultSharedPreferences(getApplicationContext())
        );
        Configuration.getInstance().setUserAgentValue(getPackageName());

        // Retrieve data
        Bundle extras = getIntent().getExtras();

        if(extras == null) {
            Intent intent = new Intent(this, MainActivity.class);
            startActivity(intent);
            finish();
            return;
        }

        String polylineString = extras.getString("polyline");
        String startTime = extras.getString("starttime");
        String endTime = extras.getString("endtime");
        double elapsedTime = extras.getDouble("elapsedtime");
        double totalDist = extras.getDouble("dist");
        categoryIndex = TravelCategory.LOCOMOTOR;

        // Spinner
        Spinner spinner = findViewById(R.id.save_category); // TODO ADD ICONS
        String[] categories = {
            TravelCategory.LOCOMOTOR.toString(),
            TravelCategory.MICROMOBILITY.toString(),
            TravelCategory.OTHER.toString()
        };

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, categories);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);

        // Set default value based on average speed
        double avgSpeed = totalDist / elapsedTime;
        if(avgSpeed < 4.17) { // 15 km/h
            spinner.setSelection(TravelCategory.LOCOMOTOR.getIndex());
        }
        else if(avgSpeed < 15) { // 54 km/h
            spinner.setSelection(TravelCategory.MICROMOBILITY.getIndex());
        }
        else {
            spinner.setSelection(TravelCategory.OTHER.getIndex());
        }

        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
              @Override
              public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                  ((TextView) parent.getChildAt(0)).setTextColor(Color.BLACK);
                  categoryIndex = TravelCategory.values()[position];
              }

              @Override
              public void onNothingSelected(AdapterView<?> parent) {}
        });

        // Initialize mapview
        MapView mapView = findViewById(R.id.save_map);
        mapView.setBackgroundColor(Color.TRANSPARENT);
        mapView.setMultiTouchControls(true);
        mapView.setUseDataConnection(true);

        TilesOverlay tilesOverlay = mapView.getOverlayManager().getTilesOverlay();
        tilesOverlay.setLoadingBackgroundColor(Color.TRANSPARENT);
        tilesOverlay.setLoadingLineColor(Color.TRANSPARENT);

        // Setup polyline
        assert polylineString != null : "Polyline string is null, unable to save activity!";
        List<LatLng> pointList = PolyUtil.decode(polylineString);
        Polyline polyline = new Polyline();
        for(LatLng point : pointList) {
            polyline.addPoint(new GeoPoint(point.latitude, point.longitude));
        }

        polyline.getOutlinePaint().setColor(Color.BLUE);
        polyline.getOutlinePaint().setStrokeWidth(7.0f);
        mapView.getOverlayManager().add(polyline);

        // Calculate bounding box
        double minLat = Double.MAX_VALUE;
        double maxLat = -Double.MAX_VALUE;
        double minLon = Double.MAX_VALUE;
        double maxLon = -Double.MAX_VALUE;

        for(LatLng p : pointList) {
            if(p.latitude < minLat) {
                minLat = p.latitude;
            }
            if(p.latitude > maxLat) {
                maxLat = p.latitude;
            }
            if(p.longitude < minLon) {
                minLon = p.longitude;
            }
            if(p.longitude > maxLon) {
                maxLon = p.longitude;
            }
        }

        BoundingBox box = new BoundingBox(maxLat, maxLon, minLat, minLon);

        // Set zoom based on bounding box
        mapView.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                mapView.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                mapView.zoomToBoundingBox(box.increaseByScale(1.4f), false);
            }
        });

        mapView.getZoomController().setVisibility(CustomZoomButtonsController.Visibility.NEVER);
        mapView.setVerticalMapRepetitionEnabled(false);

        // Set time and distance
        int hours = (int) elapsedTime / 3600;
        int minutes = ((int) elapsedTime % 3600) / 60;
        int seconds = (int) elapsedTime % 60;

        String formattedTime = String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds);
        TextView timeText = findViewById(R.id.save_time);
        timeText.setText(formattedTime);

        TextView distanceText = findViewById(R.id.save_distance);
        distanceText.setText(getString(R.string.main_distancevalue, totalDist / 1000.0));

        // Save button
        Button saveButton = findViewById(R.id.save_savebtn);
        saveButton.setOnClickListener(view -> {
            EditText titleEditText = findViewById(R.id.save_edittext_title);
            title = titleEditText.getText().toString();
            if(title.isBlank()) {
                title = "Untitled activity";
            }

            executor.execute(() -> {
                LocalActivityDatabaseDAO dao = new LocalActivityDatabaseDAO(this);
                dao.addActivity(title, categoryIndex.getIndex(), polylineString, startTime, endTime, elapsedTime, totalDist);

                // dao.printAllActivities();
            });

            Intent intent = new Intent(this, MainActivity.class);
            startActivity(intent);
        });

        // Discard button
        Button discardButton = findViewById(R.id.save_discardbtn);
        discardButton.setOnClickListener(view -> {
            AlertDialog dialog = new AlertDialog.Builder(this)
                    .setTitle("Confirmation")
                    .setMessage("Are you sure you want to discard this activity? This cannot be undone!")
                    .setPositiveButton("Yes", (d, which) -> {
                        Intent intent = new Intent(this, RecordActivity.class);
                        startActivity(intent);
                    })
                    .setNegativeButton("No", (d, which) -> d.dismiss())
                    .setCancelable(true)
                    .create();

            dialog.show();
        });
    }
}