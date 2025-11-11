package com.hu.sightseek.activity;

import static android.view.View.GONE;
import static android.view.View.VISIBLE;
import static com.hu.sightseek.helpers.WKConverter.convertGeometryToWKB;
import static com.hu.sightseek.utils.SightseekFirebaseUtils.updateCellsInFirebase;
import static com.hu.sightseek.utils.SightseekRegionalLeaderboardUtils.calculateRegionalDistance;
import static com.hu.sightseek.utils.SightseekSpatialUtils.getBoundingBox;
import static com.hu.sightseek.utils.SightseekSpatialUtils.getVisitedCells;
import static com.hu.sightseek.utils.SightseekGenericUtils.setupRouteLine;
import static com.hu.sightseek.utils.SightseekGenericUtils.setupZoomSettings;
import static com.hu.sightseek.utils.SightseekVectorizationUtils.vectorize;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.Paint;
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
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.google.firebase.auth.FirebaseAuth;
import com.hu.sightseek.R;
import com.hu.sightseek.enums.TravelCategory;
import com.hu.sightseek.db.LocalDatabaseDAO;
import com.hu.sightseek.model.VectorizedDataRecord;
import com.hu.sightseek.utils.SightseekSpatialUtils;

import org.osmdroid.config.Configuration;
import org.osmdroid.util.BoundingBox;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Polyline;
import org.osmdroid.views.overlay.TilesOverlay;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class SaveActivity extends AppCompatActivity {
    private FirebaseAuth mAuth;
    private VectorizedDataRecord vectorizedDataRecord;

    private String title;
    private TravelCategory categoryIndex;
    private final ExecutorService daoExecutor = Executors.newSingleThreadExecutor();
    private final ExecutorService vectorExecutor = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_save);
        Configuration.getInstance().load(
                getApplicationContext(),
                PreferenceManager.getDefaultSharedPreferences(getApplicationContext())
        );
        Configuration.getInstance().setUserAgentValue(getPackageName());

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

        String polylineString = "xu~zB}_b~Iiv@yJaKhRmaAzZ";
        String startTime = extras.getString("starttime");
        double elapsedTime = extras.getDouble("elapsedtime");
        double totalDist = extras.getDouble("dist");
        categoryIndex = TravelCategory.LOCOMOTOR;

        mAuth = FirebaseAuth.getInstance();

        // Spinner
        Spinner spinner = findViewById(R.id.save_category);
        ArrayAdapter<String> adapter = getStringArrayAdapter();
        spinner.setAdapter(adapter);

        // Set default value based on average speed
        double avgSpeed = totalDist / elapsedTime;
        if(avgSpeed < 3.61) { // 13 km/h
            spinner.setSelection(TravelCategory.LOCOMOTOR.getIndex());
        }
        else if(avgSpeed < 12.5) { // 45 km/h
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
        List<GeoPoint> pointList = SightseekSpatialUtils.decode(polylineString);
        Polyline polyline = new Polyline();
        for(GeoPoint point : pointList) {
            polyline.addPoint(point);
        }

        setupRouteLine(polyline, false);
        mapView.getOverlayManager().add(polyline);

        // Get vectorized dataset
        if(mAuth.getCurrentUser() != null) {
            Future<VectorizedDataRecord> future = vectorExecutor.submit(() -> vectorize(this, polyline));
            new Thread(() -> {
                try {
                    TextView loadingText = findViewById(R.id.save_loadingtext);
                    runOnUiThread(() -> loadingText.setVisibility(VISIBLE));

                    vectorizedDataRecord = future.get();

                    runOnUiThread(() -> loadingText.setVisibility(GONE));

                    Paint paint = new Paint();
                    paint.setColor(Color.parseColor("#FF0000"));
                    paint.setStrokeWidth(4.0f);
                    paint.setAntiAlias(false);

                    if(vectorizedDataRecord.getVectorizedDataPolylines() == null) {
                        return;
                    }

                    for(Polyline p : vectorizedDataRecord.getVectorizedDataPolylines()) {
                        p.getOutlinePaint().set(paint);
                        mapView.getOverlays().add(p);
                    }
                    mapView.invalidate();
                }
                catch(ExecutionException | InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }).start();
        }

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
            if(mAuth.getCurrentUser() != null && vectorizedDataRecord == null) {
                Toast.makeText(this, "Please wait for vectorization to finish!", Toast.LENGTH_LONG).show();
                return;
            }

            EditText titleEditText = findViewById(R.id.save_edittext_title);
            title = titleEditText.getText().toString();
            if(title.isBlank()) {
                title = "Untitled activity";
            }

            daoExecutor.execute(() -> {
                calculateRegionalDistance(SaveActivity.this, vectorizedDataRecord.getVectorizedDataGeometry(), vectorizedDataRecord.getCountryCodes());

                byte[] vectorizedDataBlob = convertGeometryToWKB(vectorizedDataRecord.getVectorizedDataGeometry());

                LocalDatabaseDAO dao = new LocalDatabaseDAO(this);
                long id = dao.addActivity(title, categoryIndex.getIndex(), polylineString, startTime, elapsedTime, totalDist, -1, vectorizedDataBlob);

                if(mAuth.getCurrentUser() != null) {
                    HashMap<String, Integer> visitedCells = getVisitedCells(pointList);
                    updateCellsInFirebase(mAuth, visitedCells, false);
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

    @NonNull
    private ArrayAdapter<String> getStringArrayAdapter() {
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

        return new ArrayAdapter<>(SaveActivity.this, R.layout.spinneritem_category, R.id.text, categories) {
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