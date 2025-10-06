package com.hu.sightseek.activity;

import static com.hu.sightseek.utils.SightseekGenericUtils.getBoundingBox;
import static com.hu.sightseek.utils.SightseekGenericUtils.setupRouteLine;
import static com.hu.sightseek.utils.SightseekGenericUtils.setupZoomSettings;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.firebase.geofire.GeoFireUtils;
import com.firebase.geofire.GeoLocation;
import com.google.android.gms.maps.model.LatLng;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;
import com.google.maps.android.PolyUtil;
import com.hu.sightseek.R;
import com.hu.sightseek.enums.TravelCategory;
import com.hu.sightseek.db.LocalDatabaseDAO;

import org.osmdroid.config.Configuration;
import org.osmdroid.util.BoundingBox;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Polyline;
import org.osmdroid.views.overlay.TilesOverlay;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SaveActivity extends AppCompatActivity {
    private FirebaseFirestore fireStoreDb;
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

        fireStoreDb = (FirebaseAuth.getInstance().getCurrentUser() != null) ? FirebaseFirestore.getInstance() : null;

        // Add Menu
        Toolbar toolbar = findViewById(R.id.save_topmenu);
        setSupportActionBar(toolbar);
        if(getSupportActionBar() != null) {
            getSupportActionBar().setDisplayShowTitleEnabled(false);
        }

        // Home button
        toolbar.setNavigationIcon(R.drawable.baseline_home_24);
        toolbar.setNavigationOnClickListener(v -> createDiscardConfirmationDialog(new Intent(this, MainActivity.class)));

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
        double elapsedTime = extras.getDouble("elapsedtime");
        double totalDist = extras.getDouble("dist");
        categoryIndex = TravelCategory.LOCOMOTOR;

        // Spinner
        Spinner spinner = findViewById(R.id.save_category);
        String[] categories = {
            TravelCategory.LOCOMOTOR.toString(),
            TravelCategory.MICROMOBILITY.toString(),
            TravelCategory.OTHER.toString()
        };

        // Custom icons
        int[] icons = {
                R.drawable.baseline_directions_run_24,
                R.drawable.baseline_pedal_bike_24,
                R.drawable.baseline_directions_car_24
        };

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, R.layout.spinneritem_category, R.id.text, categories) {
            @NonNull
            @Override
            public View getView(int position, View convertView, @NonNull ViewGroup parent) {
                View view = super.getView(position, convertView, parent);
                ImageView icon = view.findViewById(R.id.icon);
                icon.setImageResource(icons[position]);
                return view;
            }

            @Override
            public View getDropDownView(int position, View convertView, @NonNull ViewGroup parent) {
                View view = super.getDropDownView(position, convertView, parent);
                ImageView icon = view.findViewById(R.id.icon);
                icon.setImageResource(icons[position]);
                return view;
            }
        };
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
                TextView text = view.findViewById(R.id.text);
                text.setTextColor(Color.WHITE);
                categoryIndex = TravelCategory.values()[position];
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });

        // Initialize mapview
        MapView mapView = findViewById(R.id.save_map);
        mapView.setBackgroundColor(Color.TRANSPARENT);
        mapView.setUseDataConnection(true);

        setupZoomSettings(mapView, 14.0);

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
                LocalDatabaseDAO dao = new LocalDatabaseDAO(this);
                long id = dao.addActivity(title, categoryIndex.getIndex(), polylineString, startTime, elapsedTime, totalDist, -1);

                if(fireStoreDb != null) {
                    // Calculate geohashes
                    Map<String, Integer> visitedCells = new HashMap<>();
                    for(LatLng p : pointList) {
                        String hash = GeoFireUtils.getGeoHashForLocation(new GeoLocation(p.latitude, p.longitude), 3);

                        Integer count = visitedCells.get(hash);
                        if(count == null) {
                            count = 0;
                        }
                        visitedCells.put(hash, count + 1);
                    }

                    String uid = FirebaseAuth.getInstance().getCurrentUser().getUid();

                    fireStoreDb.collection("users")
                            .document(uid)
                            .set(Collections.singletonMap("visitedCells", visitedCells), SetOptions.merge());
                }

                Intent intent = new Intent(this, ActivityActivity.class);
                Bundle bundle = new Bundle();

                bundle.putInt("id", (int) id);
                intent.putExtras(bundle);

                startActivity(intent);
                finish();
            });
        });

        // Discard button
        Button discardButton = findViewById(R.id.save_discardbtn);
        discardButton.setOnClickListener(view -> createDiscardConfirmationDialog(new Intent(this, MainActivity.class)));

        // Handle back button
        OnBackPressedCallback onBackPressedCallback = new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                createDiscardConfirmationDialog(new Intent(SaveActivity.this, MainActivity.class));
            }
        };
        getOnBackPressedDispatcher().addCallback(this, onBackPressedCallback);
    }

    public void createDiscardConfirmationDialog(Intent intent) {
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Confirmation")
                .setMessage("Are you sure you want to discard this activity? This cannot be undone!")
                .setPositiveButton("Yes", (d, which) -> {
                    startActivity(intent);
                    finish();
                })
                .setNegativeButton("No", (d, which) -> d.dismiss())
                .setCancelable(true)
                .create();

        dialog.show();
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
            createDiscardConfirmationDialog(new Intent(this, ProfileActivity.class));
            return true;
        }

        // Statistics
        if(id == R.id.topmenu_statistics) {
            createDiscardConfirmationDialog(new Intent(this, StatisticsActivity.class));
            return true;
        }

        return super.onOptionsItemSelected(item);
    }
}