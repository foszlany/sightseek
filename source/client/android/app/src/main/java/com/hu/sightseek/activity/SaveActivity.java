package com.hu.sightseek.activity;

import static android.view.View.GONE;
import static android.view.View.VISIBLE;
import static com.hu.sightseek.helper.WKConverter.convertGeometryToWKB;
import static com.hu.sightseek.util.FirebaseUtils.updateCellsInFirebase;
import static com.hu.sightseek.util.RegionalLeaderboardUtils.calculateRegionalDistance;
import static com.hu.sightseek.util.SpatialUtils.getBoundingBox;
import static com.hu.sightseek.util.SpatialUtils.getVisitedCells;
import static com.hu.sightseek.util.GenericUtils.setupRouteLine;
import static com.hu.sightseek.util.GenericUtils.setupZoomSettings;
import static com.hu.sightseek.util.VectorizationUtils.vectorize;

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
import com.hu.sightseek.util.SpatialUtils;

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
    public static final String KEY_POLYLINE_STRING = "polyline_string";
    public static final String KEY_START_TIME = "start_time";
    public static final String KEY_ELAPSED_TIME = "elapsed_time";
    public static final String KEY_DIST = "dist";

    private static final String KEY_TITLE = "title";
    private static final String KEY_SPINNER_POS = "spinner_pos";
    private static final String KEY_IS_VECTORIZATION_COMPLETED = "is_vectorization_completed";
    private static final String KEY_IS_VECTORIZATION_STARTED = "is_vectorization_started";
    private static final String KEY_VECTORIZED_DATA = "vectorized_data_record";

    private FirebaseAuth auth;
    private VectorizedDataRecord vectorizedDataRecord;

    private String title;
    private TravelCategory categoryIndex;
    private final ExecutorService daoExecutor = Executors.newSingleThreadExecutor();
    private final ExecutorService vectorExecutor = Executors.newSingleThreadExecutor();

    private String polylineString;
    private String startTime;
    private double elapsedTime;
    private double totalDist;
    private boolean isVectorizationComplete = false;
    private boolean isVectorizationStarted = false;
    private List<GeoPoint> pointList;
    private Polyline savedPolyline;
    private String savedFormattedTime;
    private int savedSpinnerPosition = -1;
    private String savedTitleText = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_save);

        Configuration.getInstance().load(
                getApplicationContext(),
                PreferenceManager.getDefaultSharedPreferences(getApplicationContext())
        );
        Configuration.getInstance().setUserAgentValue(getPackageName());

        auth = FirebaseAuth.getInstance();

        // Restore saved state if available
        if(savedInstanceState != null) {
            restoreInstanceState(savedInstanceState);
        }
        else {
            Bundle extras = getIntent().getExtras();
            if(extras == null) {
                Intent intent = new Intent(this, MainActivity.class);
                startActivity(intent);
                finish();
                return;
            }

            polylineString = extras.getString(KEY_POLYLINE_STRING);
            startTime = extras.getString(KEY_START_TIME);
            elapsedTime = extras.getDouble(KEY_ELAPSED_TIME);
            totalDist = extras.getDouble(KEY_DIST);
            categoryIndex = TravelCategory.LOCOMOTOR;
        }

        initializeUI();

        if(pointList == null && polylineString != null) {
            setupMapAndPolyline();
        }
        else if(pointList != null) {
            setupMapWithExistingPolyline();
        }

        // Save button
        Button saveButton = findViewById(R.id.save_savebtn);
        saveButton.setOnClickListener(view -> {
            if(auth.getCurrentUser() != null && isVectorizationStarted && !isVectorizationComplete) {
                Toast.makeText(this, "Please wait for vectorization to finish!", Toast.LENGTH_LONG).show();
                return;
            }

            EditText titleEditText = findViewById(R.id.save_edittext_title);
            title = titleEditText.getText().toString();
            if(title.isBlank()) {
                title = "Untitled activity";
            }

            daoExecutor.execute(() -> {
                if(vectorizedDataRecord != null) {
                    calculateRegionalDistance(SaveActivity.this, vectorizedDataRecord.getVectorizedDataGeometry(), vectorizedDataRecord.getCountryCodes());

                    byte[] vectorizedDataBlob = convertGeometryToWKB(vectorizedDataRecord.getVectorizedDataGeometry());

                    LocalDatabaseDAO dao = new LocalDatabaseDAO(this);
                    long id = dao.addActivity(title, categoryIndex.getIndex(), polylineString, startTime, elapsedTime, totalDist, -1, vectorizedDataBlob);

                    if(auth.getCurrentUser() != null) {
                        HashMap<String, Integer> visitedCells = getVisitedCells(pointList);
                        updateCellsInFirebase(auth, visitedCells, false);
                    }

                    Intent intent = new Intent(this, ActivityActivity.class);
                    Bundle bundle = new Bundle();

                    bundle.putInt("id", (int) id);
                    intent.putExtras(bundle);

                    startActivity(intent);
                    finish();
                }
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

    private void initializeUI() {
        // Add Menu
        Toolbar toolbar = findViewById(R.id.save_topmenu);
        setSupportActionBar(toolbar);
        if(getSupportActionBar() != null) {
            getSupportActionBar().setDisplayShowTitleEnabled(false);
        }

        // Home button
        toolbar.setNavigationIcon(R.drawable.baseline_home_24);
        toolbar.setNavigationOnClickListener(v -> createDiscardConfirmationDialog(new Intent(this, MainActivity.class)));

        // Spinner
        Spinner spinner = findViewById(R.id.save_category);
        ArrayAdapter<String> adapter = getStringArrayAdapter();
        spinner.setAdapter(adapter);

        if(savedSpinnerPosition >= 0) {
            spinner.setSelection(savedSpinnerPosition);
            categoryIndex = TravelCategory.values()[savedSpinnerPosition];
        }
        else {
            // Set default value based on average speed
            double avgSpeed = totalDist / elapsedTime;
            int selection;

            if(avgSpeed < 3.61) { // 13 km/h
                selection = TravelCategory.LOCOMOTOR.getIndex();
            }
            else if(avgSpeed < 12.5) { // 45 km/h
                selection = TravelCategory.MICROMOBILITY.getIndex();
            }
            else {
                selection = TravelCategory.OTHER.getIndex();
            }
            spinner.setSelection(selection);
            categoryIndex = TravelCategory.values()[selection];
        }

        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if(view != null) {
                    TextView text = view.findViewById(R.id.spinneritem_category_text);
                    if(text != null) {
                        text.setTextColor(Color.WHITE);
                    }
                }
                categoryIndex = TravelCategory.values()[position];
                savedSpinnerPosition = position;
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });

        // Set time and distance
        if(savedFormattedTime != null) {
            TextView timeText = findViewById(R.id.save_time);
            timeText.setText(savedFormattedTime);
        }
        else if(elapsedTime > 0) {
            int hours = (int) elapsedTime / 3600;
            int minutes = ((int) elapsedTime % 3600) / 60;
            int seconds = (int) elapsedTime % 60;

            savedFormattedTime = String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds);
            TextView timeText = findViewById(R.id.save_time);
            timeText.setText(savedFormattedTime);
        }

        TextView distanceText = findViewById(R.id.save_distance);
        if(totalDist > 0) {
            distanceText.setText(getString(R.string.main_distancevalue, totalDist / 1000.0));
        }

        EditText titleEditText = findViewById(R.id.save_edittext_title);
        if(!savedTitleText.isEmpty()) {
            titleEditText.setText(savedTitleText);
        }
    }

    private void setupMapAndPolyline() {
        MapView mapView = findViewById(R.id.save_map);
        mapView.setBackgroundColor(Color.TRANSPARENT);
        mapView.setUseDataConnection(true);

        setupZoomSettings(mapView, 14.0);

        TilesOverlay tilesOverlay = mapView.getOverlayManager().getTilesOverlay();
        tilesOverlay.setLoadingBackgroundColor(Color.TRANSPARENT);
        tilesOverlay.setLoadingLineColor(Color.TRANSPARENT);

        // Setup polyline
        pointList = SpatialUtils.decode(polylineString);
        savedPolyline = new Polyline();
        for(GeoPoint point : pointList) {
            savedPolyline.addPoint(point);
        }

        setupRouteLine(savedPolyline, false);
        mapView.getOverlayManager().add(savedPolyline);

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

        // Start vectorization if needed
        if(auth.getCurrentUser() != null && !isVectorizationStarted) {
            startVectorization(mapView);
        }
    }

    private void setupMapWithExistingPolyline() {
        MapView mapView = findViewById(R.id.save_map);
        mapView.setBackgroundColor(Color.TRANSPARENT);
        mapView.setUseDataConnection(true);

        setupZoomSettings(mapView, 14.0);

        TilesOverlay tilesOverlay = mapView.getOverlayManager().getTilesOverlay();
        tilesOverlay.setLoadingBackgroundColor(Color.TRANSPARENT);
        tilesOverlay.setLoadingLineColor(Color.TRANSPARENT);

        if(savedPolyline != null) {
            mapView.getOverlayManager().add(savedPolyline);

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
        }

        // If vectorization was started but not complete, restart it
        if(auth.getCurrentUser() != null && isVectorizationStarted && !isVectorizationComplete) {
            startVectorization(mapView);
        }
    }

    private void startVectorization(MapView mapView) {
        isVectorizationStarted = true;

        Future<VectorizedDataRecord> future = vectorExecutor.submit(() -> vectorize(this, savedPolyline));
        new Thread(() -> {
            try {
                TextView loadingText = findViewById(R.id.save_loadingtext);
                runOnUiThread(() -> {
                    if(loadingText != null) {
                        loadingText.setVisibility(VISIBLE);
                    }
                });

                vectorizedDataRecord = future.get();
                isVectorizationComplete = true;

                runOnUiThread(() -> {
                    if(loadingText != null) {
                        loadingText.setVisibility(GONE);
                    }
                });

                Paint paint = new Paint();
                paint.setColor(Color.parseColor("#FF0000"));
                paint.setStrokeWidth(4.0f);
                paint.setAntiAlias(false);

                if(vectorizedDataRecord != null && vectorizedDataRecord.getVectorizedDataPolylines() != null) {
                    for(Polyline p : vectorizedDataRecord.getVectorizedDataPolylines()) {
                        p.getOutlinePaint().set(paint);
                        mapView.getOverlays().add(p);
                    }
                    mapView.invalidate();
                }
            }
            catch(ExecutionException | InterruptedException e) {
                throw new RuntimeException(e);
            }
        }).start();
    }

    private void restoreInstanceState(Bundle savedInstanceState) {
        polylineString = savedInstanceState.getString(KEY_POLYLINE_STRING);
        startTime = savedInstanceState.getString(KEY_START_TIME);
        elapsedTime = savedInstanceState.getDouble(KEY_ELAPSED_TIME);
        totalDist = savedInstanceState.getDouble(KEY_DIST);
        savedTitleText = savedInstanceState.getString(KEY_TITLE, "");
        savedSpinnerPosition = savedInstanceState.getInt(KEY_SPINNER_POS, -1);
        isVectorizationComplete = savedInstanceState.getBoolean(KEY_IS_VECTORIZATION_COMPLETED, false);
        isVectorizationStarted = savedInstanceState.getBoolean(KEY_IS_VECTORIZATION_STARTED, false);
        vectorizedDataRecord = (VectorizedDataRecord) savedInstanceState.getSerializable(KEY_VECTORIZED_DATA);

        if(savedSpinnerPosition >= 0) {
            categoryIndex = TravelCategory.values()[savedSpinnerPosition];
        }
        else {
            categoryIndex = TravelCategory.LOCOMOTOR;
        }

        if(polylineString != null) {
            pointList = SpatialUtils.decode(polylineString);
            savedPolyline = new Polyline();
            for(GeoPoint point : pointList) {
                savedPolyline.addPoint(point);
            }
            setupRouteLine(savedPolyline, false);
        }

        if(elapsedTime > 0) {
            int hours = (int) elapsedTime / 3600;
            int minutes = ((int) elapsedTime % 3600) / 60;
            int seconds = (int) elapsedTime % 60;
            savedFormattedTime = String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds);
        }
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);

        outState.putString(KEY_POLYLINE_STRING, polylineString);
        outState.putString(KEY_START_TIME, startTime);
        outState.putDouble(KEY_ELAPSED_TIME, elapsedTime);
        outState.putDouble(KEY_DIST, totalDist);
        outState.putBoolean(KEY_IS_VECTORIZATION_COMPLETED, isVectorizationComplete);
        outState.putBoolean(KEY_IS_VECTORIZATION_STARTED, isVectorizationStarted);

        if(categoryIndex != null) {
            outState.putInt(KEY_SPINNER_POS, categoryIndex.ordinal());
        }

        EditText titleEditText = findViewById(R.id.save_edittext_title);
        if(titleEditText != null) {
            outState.putString(KEY_TITLE, titleEditText.getText().toString());
        }

        if(vectorizedDataRecord != null) {
            outState.putSerializable(KEY_VECTORIZED_DATA, vectorizedDataRecord);
        }
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

        return new ArrayAdapter<>(SaveActivity.this, R.layout.spinneritem_category, R.id.spinneritem_category_text, categories) {
            @NonNull
            @Override
            public View getView(int position, View convertView, @NonNull ViewGroup parent) {
                View view = super.getView(position, convertView, parent);
                ImageView icon = view.findViewById(R.id.spinneritem_category_icon);
                icon.setImageResource(icons[position]);
                return view;
            }

            @Override
            public View getDropDownView(int position, View convertView, @NonNull ViewGroup parent) {
                View view = super.getDropDownView(position, convertView, parent);
                ImageView icon = view.findViewById(R.id.spinneritem_category_icon);
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

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if(!daoExecutor.isShutdown()) {
            daoExecutor.shutdown();
        }

        if(!vectorExecutor.isShutdown()) {
            vectorExecutor.shutdown();
        }
    }
}