package com.hu.sightseek.activity;

import static android.view.View.INVISIBLE;
import static android.view.View.VISIBLE;

import static com.hu.sightseek.utils.SightseekGenericUtils.getBitmapFromVectorDrawable;
import static com.hu.sightseek.utils.SightseekGenericUtils.setupRouteLine;
import static com.hu.sightseek.utils.SightseekGenericUtils.defaultToBudapest;
import static com.hu.sightseek.utils.SightseekGenericUtils.setupZoomSettings;

import android.animation.ValueAnimator;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.location.Location;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Looper;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.Chronometer;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.res.ResourcesCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.maps.android.BuildConfig;
import com.google.maps.android.PolyUtil;
import com.google.maps.android.SphericalUtil;
import com.hu.sightseek.R;
import com.hu.sightseek.db.LocalDatabaseDAO;
import com.hu.sightseek.fragment.AttractionInfoWindow;
import com.hu.sightseek.model.Attraction;
import com.hu.sightseek.model.AttractionGeoPoint;
import com.hu.sightseek.service.RecordingService;
import com.hu.sightseek.utils.SightseekGenericUtils;

import org.osmdroid.api.IGeoPoint;
import org.osmdroid.config.Configuration;
import org.osmdroid.events.MapEventsReceiver;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.FolderOverlay;
import org.osmdroid.views.overlay.IconOverlay;
import org.osmdroid.views.overlay.MapEventsOverlay;
import org.osmdroid.views.overlay.Overlay;
import org.osmdroid.views.overlay.Polyline;
import org.osmdroid.views.overlay.TilesOverlay;
import org.osmdroid.views.overlay.infowindow.InfoWindow;
import org.osmdroid.views.overlay.mylocation.DirectedLocationOverlay;
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay;
import org.osmdroid.views.overlay.simplefastpoint.SimpleFastPointOverlay;
import org.osmdroid.views.overlay.simplefastpoint.SimpleFastPointOverlayOptions;
import org.osmdroid.views.overlay.simplefastpoint.SimplePointTheme;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class RecordActivity extends AppCompatActivity {
    private static final int UPDATE_INTERVAL_MAX = 4000;
    private static final int UPDATE_INTERVAL_MIN = 4000;
    private static final int REQUEST_PERMISSIONS_REQUEST_CODE = 1;
    private static final int MINIMUM_REQUIRED_POINTS_PER_ACTIVITY = 4; // TODO CHANGE LATER

    private BottomNavigationView bottomNav;
    private LinearLayout statOverlay;

    private MapView mapView;
    private FusedLocationProviderClient fusedLocationClient;
    private SimpleDateFormat dateFormat;
    private LocationCallback locationCallback;
    private DirectedLocationOverlay locationOverlay;
    private BroadcastReceiver locationModeReceiver;
    private Intent recordServiceIntent;
    private ArrayList<LatLng> importedPoints;
    private ArrayList<LatLng> recordedPoints;
    private boolean isRecording;
    private boolean didPressStopWhileLowPointCount;
    private String startTime;
    private Polyline route;
    private Chronometer chronometer;
    private long elapsedTime;
    private double totalDist;
    private double currentSpeed;

    private boolean isLocked;

    private org.osmdroid.views.overlay.GroundOverlay heatmapOverlay;
    private boolean isHeatmapOn;

    FolderOverlay polylineGroup;
    private boolean isPolylinesOverlayOn;

    private boolean areAttractionsOn;
    private SimpleFastPointOverlay attractionsOverlay;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_record);
        Configuration.getInstance().load(
                this,
                PreferenceManager.getDefaultSharedPreferences(this)
        );
        Configuration.getInstance().setUserAgentValue(getPackageName());

        // Default values
        dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US);
        dateFormat.setTimeZone(TimeZone.getDefault());

        importedPoints = null;
        recordedPoints = new ArrayList<>();
        isRecording = false;
        didPressStopWhileLowPointCount = false;
        route = new Polyline();
        elapsedTime = 0;
        totalDist = 0;
        currentSpeed = 0;

        isLocked = true;
        isHeatmapOn = false;
        polylineGroup = new FolderOverlay();
        areAttractionsOn = false;

        isPolylinesOverlayOn = false;

        chronometer = findViewById(R.id.record_chronometer);
        chronometer.setVisibility(INVISIBLE);

        statOverlay = findViewById(R.id.record_statoverlay);
        statOverlay.setVisibility(INVISIBLE);

        // Add Menu
        Toolbar toolbar = findViewById(R.id.record_topmenu);
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

        // Initialize MapView
        Configuration.getInstance().setUserAgentValue(BuildConfig.LIBRARY_PACKAGE_NAME);
        Configuration.getInstance().setOsmdroidBasePath(getCacheDir());
        Configuration.getInstance().setOsmdroidTileCache(getCacheDir());
        Configuration.getInstance().setCacheMapTileCount((short) 2000);
        Configuration.getInstance().setCacheMapTileOvershoot((short) 800);

        mapView = findViewById(R.id.record_map);
        mapView.setBackgroundColor(Color.TRANSPARENT);
        mapView.setUseDataConnection(true);

        TilesOverlay tilesOverlay = mapView.getOverlayManager().getTilesOverlay();
        tilesOverlay.setLoadingBackgroundColor(Color.TRANSPARENT);
        tilesOverlay.setLoadingLineColor(Color.TRANSPARENT);

        setupZoomSettings(mapView, 14.0);

        // Initialize route overlay
        setupRouteLine(route, false);
        mapView.getOverlays().add(0, route);

        // Marker for current location
        Bitmap markerIcon = getBitmapFromVectorDrawable(this, R.drawable.baseline_navigation_48);

        locationOverlay = new DirectedLocationOverlay(mapView.getContext());
        locationOverlay.setShowAccuracy(false);
        locationOverlay.setEnabled(true);
        locationOverlay.setDirectionArrow(markerIcon);

        mapView.getOverlays().add(1, locationOverlay);

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        // Ask for permissions
        if(ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION}, REQUEST_PERMISSIONS_REQUEST_CODE);
        }
        else {
            startLocationUpdates();
        }

        // Detect location access changes
        IntentFilter filter = new IntentFilter(LocationManager.MODE_CHANGED_ACTION);
        locationModeReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if(LocationManager.MODE_CHANGED_ACTION.equals(intent.getAction())) {
                    if(!isLocationEnabled(RecordActivity.this)) {
                        locationOverlay.setEnabled(true);
                    }
                    else {
                        locationOverlay.setEnabled(false);
                        if(isRecording) {
                            pauseRecord();
                            Toast.makeText(RecordActivity.this, "Location disabled, recording paused", Toast.LENGTH_LONG).show();
                        }
                    }
                    mapView.invalidate();
                }
            }
        };
        registerReceiver(locationModeReceiver, filter);
    }

    private void animateButton(ImageButton polylineButton, boolean on, int color) {
        ValueAnimator animator;

        if(on) {
             animator = ValueAnimator.ofArgb(
                    ContextCompat.getColor(this, R.color.lock_overlay),
                    ContextCompat.getColor(this, color)
             );
        }
        else {
            animator = ValueAnimator.ofArgb(
                    ContextCompat.getColor(this, color),
                    ContextCompat.getColor(this, R.color.lock_overlay)
            );
        }

        GradientDrawable polylineBackground = (GradientDrawable) polylineButton.getBackground().mutate();

        animator.addUpdateListener(valueAnimator -> {
            int animatedColor = (Integer) valueAnimator.getAnimatedValue();
            polylineBackground.setColor(animatedColor);
        });

        animator.setDuration(144);
        animator.start();
    }

    private void pauseRecord() {
        recordedPoints.add(new LatLng(0, 0));
        isRecording = false;
        chronometer.stop();
        elapsedTime = SystemClock.elapsedRealtime() - chronometer.getBase();

        bottomNav.getMenu()
                .findItem(R.id.bottommenu_record)
                .setIcon(R.drawable.baseline_play_circle_24);

        bottomNav.getMenu()
                .findItem(R.id.bottommenu_record)
                .setTitle("Record");
    }

    private void startLocationUpdates() {
        // Set update intervals
        LocationRequest locationRequest = new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, UPDATE_INTERVAL_MAX)
                .setMinUpdateIntervalMillis(UPDATE_INTERVAL_MIN)
                .build();

        // Check for permissions
        if(ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // Default to Budapest
            defaultToBudapest(mapView);

            Toast.makeText(this, "Fine location data is required for accurate tracking!", Toast.LENGTH_SHORT).show();
            ActivityCompat.requestPermissions(this, new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION}, REQUEST_PERMISSIONS_REQUEST_CODE);
            return;
        }
        else if(!isLocationEnabled(this)) {
            // Default to Budapest
            defaultToBudapest(mapView);
        }
        else {
            centerToCurrentLocation();
        }

        // Get current location
        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(@NonNull LocationResult locationResult) {
                Location location = locationResult.getLastLocation();
                if(location != null) {
                    GeoPoint point = new GeoPoint(
                            location.getLatitude(),
                            location.getLongitude()
                    );

                    // Animate marker
                    if(mapView != null && isLocked) {
                        mapView.getController().animateTo(point, mapView.getZoomLevelDouble(), 666L);

                        locationOverlay.setLocation(new GeoPoint(location.getLatitude(), location.getLongitude()));
                        if(location.hasBearing()) {
                            locationOverlay.setBearing(location.getBearing());
                        }
                    }

                    // Record point if needed
                    if(isRecording) {
                        // Get current location
                        double lat = point.getLatitude();
                        double lng = point.getLongitude();
                        LatLng newPoint = new LatLng(lat, lng);

                        // Prevent small changes from occurring in the final polyline
                        if(!recordedPoints.isEmpty()) {
                            double newDistanceLength = SphericalUtil.computeDistanceBetween(recordedPoints.get(recordedPoints.size() - 1), newPoint);
                            if(newDistanceLength < 0.25) {
                                return;
                            }
                        }

                        // Record
                        recordedPoints.add(newPoint);

                        // Add point to the route
                        route.addPoint(point);

                        // Mark first point, record start time
                        if(recordedPoints.size() == 1) {
                            startTime = dateFormat.format(new Date());

                            Drawable icon = ResourcesCompat.getDrawable(getResources(), R.drawable.baseline_circle_24, null);
                            if(icon != null) {
                                icon.setTint(Color.BLUE);
                            }

                            IconOverlay firstPoint = new IconOverlay(point, icon);

                            mapView.getOverlays().add(0, firstPoint);
                        }
                        // Update variables
                        else {
                            double newDistanceLength = SphericalUtil.computeDistanceBetween(recordedPoints.get(recordedPoints.size() - 2), recordedPoints.get(recordedPoints.size() - 1));

                            // Speed
                            currentSpeed = (newDistanceLength / (UPDATE_INTERVAL_MIN / 1000.0)) * 3.6;

                            TextView speedView = findViewById(R.id.record_speed);
                            speedView.setText(getString(R.string.main_speed, currentSpeed));

                            // Distance
                            totalDist += newDistanceLength;

                            TextView distanceView = findViewById(R.id.record_distance);
                            distanceView.setText(getString(R.string.main_distance, totalDist / 1000.0));
                        }
                    }
                }
                else {
                    System.out.println("NULL LOCATION"); // TODO: Bad connection?
                }

                if(mapView != null) {
                    mapView.invalidate();
                }
            }
        };

        // Update location
        fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper()
        );
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if(requestCode == REQUEST_PERMISSIONS_REQUEST_CODE) {
            // Start location updates
            if(grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Marker for current location
                startLocationUpdates();
            }
            // Check if user clicked on "Don't ask again"
            else if(!ActivityCompat.shouldShowRequestPermissionRationale(this, android.Manifest.permission.ACCESS_FINE_LOCATION)) {
                Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                Uri uri = Uri.fromParts("package", getPackageName(), null);
                intent.setData(uri);
                startActivity(intent);
                Toast.makeText(this, "You must allow precise tracking to use this feature!", Toast.LENGTH_LONG).show();
            }
            else {
                Toast.makeText(this, "Precise location permission is required to track your position!", Toast.LENGTH_LONG).show();
            }
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

    // Returns whether location **feature** is enabled
    public boolean isLocationEnabled(Context context) {
        LocationManager lm = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        return lm.isProviderEnabled(LocationManager.GPS_PROVIDER) || lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
    }

    public void centerToCurrentLocation() {
        if(ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.getLastLocation().addOnSuccessListener(this, location -> {
                if(location != null) {
                    GeoPoint point = new GeoPoint(location.getLatitude(), location.getLongitude());
                    mapView.getController().setCenter(point);
                }
            });
        }
    }

    @Override
    protected void onStart() {
        super.onStart();

        // Lock button
        ImageButton lockButton = findViewById(R.id.record_lockbtn);
        lockButton.setOnClickListener(v -> {
            isLocked = !isLocked;

            if(isLocked) {
                animateButton(lockButton, false, R.color.hint);

                Drawable lock = ResourcesCompat.getDrawable(getResources(), R.drawable.baseline_lock_outline_24, null);
                lockButton.setImageDrawable(lock);

                centerToCurrentLocation();
            }
            else {
                animateButton(lockButton, true, R.color.hint);

                Drawable lock = ResourcesCompat.getDrawable(getResources(), R.drawable.baseline_lock_open_24, null);
                lockButton.setImageDrawable(lock);
            }
        });

        // Heatmap button
        ImageButton heatmapButton = findViewById(R.id.record_heatmapbtn);
        heatmapButton.setOnClickListener(v -> {
            isHeatmapOn = !isHeatmapOn;

            Executor executor = Executors.newSingleThreadExecutor();
            if(isHeatmapOn) {
                executor.execute(() -> {
                    // Import points if necessary
                    if(importedPoints == null) {
                        LocalDatabaseDAO dao = new LocalDatabaseDAO(this);
                        importedPoints = dao.getAllPoints();
                        dao.close();
                    }

                    //TODO
                    // For now this is a static overlay that can be created once
                    // Should be able to regenerate the heatmap when zooming in/out or moving
                    if(heatmapOverlay == null) {
                        heatmapOverlay = SightseekGenericUtils.createHeatmapOverlay(mapView, importedPoints);
                    }
                    else {
                        mapView.getOverlays().add(0, heatmapOverlay);
                        mapView.invalidate();
                    }
                });

                animateButton(heatmapButton, true, R.color.orange);
            }
            else {
                // Remove overlay
                executor.execute(() -> {
                    mapView.getOverlays().remove(heatmapOverlay);
                    mapView.invalidate();
                });

                animateButton(heatmapButton, false, R.color.orange);
            }
        });

        // Polyline button
        ImageButton polylineButton = findViewById(R.id.record_polylinebtn);
        polylineButton.setOnClickListener(v -> {
            isPolylinesOverlayOn = !isPolylinesOverlayOn;

            Executor executor = Executors.newSingleThreadExecutor();
            if(isPolylinesOverlayOn) {
                executor.execute(() -> {
                    // Import polylines if necessary
                    if(polylineGroup.getItems().isEmpty()) {
                        LocalDatabaseDAO dao = new LocalDatabaseDAO(this);
                        ArrayList<Polyline> polylines = dao.getAllPolylines(6);
                        dao.close();

                        for(Polyline p : polylines) {
                            setupRouteLine(p, true);
                            polylineGroup.add(p);
                        }

                        mapView.getOverlays().add(0, polylineGroup);
                    }

                    polylineGroup.setEnabled(true);
                    mapView.invalidate();
                });

                animateButton(polylineButton, true, R.color.darker_light_blue);
            }
            else {
                executor.execute(() -> {
                    polylineGroup.setEnabled(false);
                    mapView.invalidate();
                });

                animateButton(polylineButton, false, R.color.darker_light_blue);
            }
        });

        // Attractions button
        ImageButton attractionButton = findViewById(R.id.record_attractionbtn);
        if(FirebaseAuth.getInstance().getCurrentUser() == null) {
            attractionButton.setVisibility(INVISIBLE);
            return;
        }
        else {
            attractionButton.setOnClickListener(v -> {
                areAttractionsOn = !areAttractionsOn;

                if(areAttractionsOn) {
                    animateButton(attractionButton, true, R.color.light_purple);

                    Executors.newSingleThreadExecutor().execute(() -> {
                        // Import if necessary
                        if(attractionsOverlay == null) {
                            LocalDatabaseDAO dao = new LocalDatabaseDAO(this);
                            ArrayList<Attraction> attractions = dao.getSavedAttractions();
                            dao.close();

                            List<IGeoPoint> points = new ArrayList<>();
                            for(Attraction a : attractions) {
                                points.add(new AttractionGeoPoint(a.getLatitude(), a.getLongitude(), a.getName(), a.getId()));
                            }

                            // Cancel when clicking outside
                            MapEventsReceiver mReceive = new MapEventsReceiver() {
                                @Override
                                public boolean singleTapConfirmedHelper(GeoPoint p) {
                                    InfoWindow.closeAllInfoWindowsOn(mapView);
                                    return false;
                                }

                                @Override
                                public boolean longPressHelper(GeoPoint p) {
                                    return false;
                                }
                            };

                            mapView.getOverlays().add(new MapEventsOverlay(mReceive));

                            runOnUiThread(() -> {
                                SimpleFastPointOverlayOptions layoutStyle = SimpleFastPointOverlayOptions.getDefaultStyle()
                                        .setAlgorithm(points.size() < 8000 ? SimpleFastPointOverlayOptions.RenderingAlgorithm.MEDIUM_OPTIMIZATION : SimpleFastPointOverlayOptions.RenderingAlgorithm.MAXIMUM_OPTIMIZATION)
                                        .setRadius(8)
                                        .setIsClickable(true);

                                // Styles
                                Paint pointStyle = new Paint();
                                pointStyle.setColor(Color.parseColor("#DE003B"));
                                layoutStyle.setPointStyle(pointStyle);

                                Paint textStyle = new Paint();
                                textStyle.setColor(Color.RED);
                                textStyle.setTextSize(26);
                                textStyle.setFakeBoldText(true);
                                textStyle.setShadowLayer(1, 1, 1, Color.GRAY);
                                textStyle.setTextAlign(Paint.Align.CENTER);
                                layoutStyle.setTextStyle(textStyle);

                                Paint highlightStyle = new Paint();
                                highlightStyle.setColor(Color.TRANSPARENT);
                                layoutStyle.setSelectedPointStyle(highlightStyle);

                                layoutStyle.setLabelPolicy(SimpleFastPointOverlayOptions.LabelPolicy.ZOOM_THRESHOLD);
                                layoutStyle.setMinZoomShowLabels(10);

                                // Create overlay
                                attractionsOverlay = new SimpleFastPointOverlay(new SimplePointTheme(points, true), layoutStyle);

                                mapView.getOverlays().add(attractionsOverlay);
                                attractionsOverlay.setEnabled(true);
                                mapView.invalidate();

                                // Point listener
                                attractionsOverlay.setOnClickListener((point, i) -> {
                                    if(!areAttractionsOn) {
                                        return;
                                    }

                                    AttractionGeoPoint attractionPoint = (AttractionGeoPoint) point.get(i);

                                    InfoWindow.closeAllInfoWindowsOn(mapView);

                                    AttractionInfoWindow info = new AttractionInfoWindow(R.layout.attraction_popup, mapView, layoutStyle, points, attractionsOverlay, attractionButton);
                                    info.open(attractionPoint, new GeoPoint(attractionPoint.getLatitude(), attractionPoint.getLongitude()), 0, 0);
                                });
                            });
                        }
                        else {
                            attractionsOverlay.setEnabled(true);
                            mapView.invalidate();
                        }
                    });
                }
                else {
                    animateButton(attractionButton, false, R.color.light_purple);
                    runOnUiThread(() -> {
                        if(attractionsOverlay != null) {
                            InfoWindow.closeAllInfoWindowsOn(mapView);
                            attractionsOverlay.setEnabled(false);
                            mapView.invalidate();
                        }
                    });
                }
            });
        }

        // Bottombar listener
        bottomNav = findViewById(R.id.record_bottommenu);
        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();

            // Recording
            if(id == R.id.bottommenu_record) {
                // Begin
                if(!isRecording) {
                    if(!isLocationEnabled(this)) {
                        Toast.makeText(this, "Location is currently disabled!", Toast.LENGTH_LONG).show();
                        return true;
                    }

                    // First press
                    if(recordedPoints.isEmpty()) {
                        // Start service
                        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            NotificationChannel channel = new NotificationChannel(
                                    "channel_recording",
                                    "Location recording",
                                    NotificationManager.IMPORTANCE_LOW
                            );
                            NotificationManager manager = getSystemService(NotificationManager.class);
                            manager.createNotificationChannel(channel);
                        }

                        recordServiceIntent = new Intent(RecordActivity.this, RecordingService.class);
                        ContextCompat.startForegroundService(RecordActivity.this, recordServiceIntent);

                        // Initialize overlay
                        statOverlay.setVisibility(VISIBLE);

                        TextView distanceView = findViewById(R.id.record_distance);
                        distanceView.setText(getString(R.string.main_distance, 0.0));

                        TextView speedView = findViewById(R.id.record_speed);
                        speedView.setText(getString(R.string.main_speed, 0.0));
                    }

                    isRecording = true;

                    chronometer.setBase(SystemClock.elapsedRealtime() - elapsedTime);
                    chronometer.start();

                    bottomNav.getMenu()
                            .findItem(R.id.bottommenu_record)
                            .setIcon(R.drawable.baseline_pause_circle_24);

                    bottomNav.getMenu()
                            .findItem(R.id.bottommenu_record)
                            .setTitle("Pause");

                    bottomNav.getMenu()
                            .findItem(R.id.bottommenu_stop)
                            .setVisible(true);

                    bottomNav.getMenu()
                            .findItem(R.id.bottommenu_timer_container)
                            .setVisible(true);

                    chronometer.setVisibility(VISIBLE);
                }
                // Pause
                else {
                    pauseRecord();
                }

                return true;
            }

            // Stop record
            else if(id == R.id.bottommenu_stop) {
                // Not enough data, prevent stopping
                if(recordedPoints.size() < MINIMUM_REQUIRED_POINTS_PER_ACTIVITY && !didPressStopWhileLowPointCount) {
                    didPressStopWhileLowPointCount = true;
                    Toast.makeText(this, "Your activity is too short. Tap stop again to halt the recording.", Toast.LENGTH_LONG).show();
                }
                else {
                    stopService(recordServiceIntent);

                    isRecording = false;
                    didPressStopWhileLowPointCount = false;
                    chronometer.stop();
                    elapsedTime = SystemClock.elapsedRealtime() - chronometer.getBase();

                    Intent intent = new Intent(this, SaveActivity.class);
                    Bundle bundle = new Bundle();

                    bundle.putString("polyline", PolyUtil.encode(recordedPoints));
                    bundle.putString("starttime", startTime);
                    bundle.putDouble("elapsedtime", Math.floor(elapsedTime / 1000.0));
                    bundle.putDouble("dist", totalDist);
                    bundle.putDouble("type", totalDist);
                    intent.putExtras(bundle);

                    if(recordedPoints.size() >= MINIMUM_REQUIRED_POINTS_PER_ACTIVITY) {
                        startActivity(intent);
                        finish();
                        return true;
                    }

                    recordedPoints.clear();

                    bottomNav.getMenu()
                            .findItem(R.id.bottommenu_record)
                            .setIcon(R.drawable.baseline_play_circle_24);

                    bottomNav.getMenu()
                            .findItem(R.id.bottommenu_record)
                            .setTitle("Record");

                    bottomNav.getMenu()
                            .findItem(R.id.bottommenu_stop)
                            .setVisible(false);

                    bottomNav.getMenu()
                            .findItem(R.id.bottommenu_timer_container)
                            .setVisible(false);

                    chronometer.setVisibility(INVISIBLE);
                    elapsedTime = 0;
                    totalDist = 0;

                    // Clear map
                    for(Overlay i : mapView.getOverlays()) {
                        if(!(i instanceof MyLocationNewOverlay)) {
                            mapView.getOverlays().remove(i);
                        }
                    }
                }

                return true;
            }

            return false;
        });

        // Prevent back button from destroying the view when recording
        OnBackPressedCallback onBackPressedCallback = new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if(isRecording) {
                    Intent intent = new Intent(RecordActivity.this, MainActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                    startActivity(intent);
                }
                else {
                    finish();
                }
            }
        };
        getOnBackPressedDispatcher().addCallback(this, onBackPressedCallback);

    }

    @Override
    protected void onPause() {
        super.onPause();

        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(@NonNull LocationResult locationResult) {
                Location location = locationResult.getLastLocation();
                if(location != null) {
                    GeoPoint point = new GeoPoint(location.getLatitude(), location.getLongitude());
                    if(isLocked) {
                        mapView.getController().setCenter(point);
                    }
                }
            }
        };
    }

    @Override
    protected void onResume() {
        super.onResume();

        locationOverlay.setEnabled(true);
        if(ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            startLocationUpdates();
        }

        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(@NonNull LocationResult locationResult) {
                Location location = locationResult.getLastLocation();
                if (location != null) {
                    GeoPoint point = new GeoPoint(location.getLatitude(), location.getLongitude());
                    if(isLocked && mapView != null) {
                        mapView.getController().setCenter(point);
                    }
                    if(locationOverlay != null) {
                        locationOverlay.setLocation(point);
                        mapView.invalidate();
                    }
                }
            }
        };
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if(locationModeReceiver != null) {
            unregisterReceiver(locationModeReceiver);
        }
    }
}