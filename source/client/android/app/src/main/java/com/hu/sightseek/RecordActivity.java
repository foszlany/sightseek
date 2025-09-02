package com.hu.sightseek;

import static android.view.View.INVISIBLE;
import static android.view.View.VISIBLE;

import android.animation.ValueAnimator;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.location.Location;
import android.location.LocationManager;
import android.net.Uri;
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
import com.google.maps.android.BuildConfig;
import com.google.maps.android.PolyUtil;
import com.google.maps.android.SphericalUtil;

import org.osmdroid.config.Configuration;
import org.osmdroid.util.BoundingBox;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.CustomZoomButtonsController;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.IconOverlay;
import org.osmdroid.views.overlay.Overlay;
import org.osmdroid.views.overlay.Polyline;
import org.osmdroid.views.overlay.TilesOverlay;
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider;
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

public class RecordActivity extends AppCompatActivity {
    private static final int UPDATE_INTERVAL_MAX = 4000;
    private static final int UPDATE_INTERVAL_MIN = 4000;
    private static final int REQUEST_PERMISSIONS_REQUEST_CODE = 1;
    private static final int MINIMUM_REQUIRED_POINTS_PER_ACTIVITY = 4;

    private MapView mapView;
    private FusedLocationProviderClient fusedLocationClient;
    private SimpleDateFormat dateFormat;
    private LocationCallback locationCallback;
    private MyLocationNewOverlay locationOverlay;

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


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_record);
        Configuration.getInstance().load(
                this,
                PreferenceManager.getDefaultSharedPreferences(this)
        );
        Configuration.getInstance().setUserAgentValue(getPackageName());

        // TODO: MOVE THIS TO MAIN ACTIVITY!!!
        // Show banner when launching for the first time
        SharedPreferences prefs = getSharedPreferences("AppPrefs", MODE_PRIVATE);
        boolean isFirstLaunch = prefs.getBoolean("isFirstLauncha", true); // TODO: Remove 'a' once done testing

        if(isFirstLaunch) {
            startActivity(new Intent(this, BannerActivity.class));
            prefs.edit().putBoolean("isFirstLaunch", false).apply();
            finish();
        }

        // Default values
        dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US);
        dateFormat.setTimeZone(TimeZone.getDefault());

        recordedPoints = new ArrayList<>();
        isRecording = false;
        didPressStopWhileLowPointCount = false;
        route = new Polyline();
        elapsedTime = 0;
        totalDist = 0;
        currentSpeed = 0;
        isLocked = true;

        chronometer = findViewById(R.id.bottommenu_chronometer);
        chronometer.setVisibility(INVISIBLE);

        LinearLayout statOverlay = findViewById(R.id.main_statoverlay);
        statOverlay.setVisibility(INVISIBLE);

        // Add Menu
        Toolbar toolbar = findViewById(R.id.menubar_main);
        setSupportActionBar(toolbar);

        // Lock listener
        ImageButton lockButton = findViewById(R.id.main_lock);
        lockButton.setOnClickListener(item -> {
            ValueAnimator animator = ValueAnimator.ofArgb(
                    ContextCompat.getColor(this, R.color.lock_overlay),
                    ContextCompat.getColor(this, R.color.lock_overlay_blink)
            );

            GradientDrawable lockBackground = (GradientDrawable) lockButton.getBackground();
            animator.addUpdateListener(valueAnimator -> lockBackground.setColor((Integer) valueAnimator.getAnimatedValue()));

            animator.setDuration(144);
            animator.setRepeatMode(ValueAnimator.REVERSE);
            animator.setRepeatCount(1);
            animator.start();

            isLocked = !isLocked;

            if(isLocked) {
                Drawable lock = ResourcesCompat.getDrawable(getResources(), R.drawable.baseline_lock_outline_24, null);
                lockButton.setImageDrawable(lock);

                // Attempt to recenter
                centerToCurrentLocation();
            }
            else {
                Drawable lock = ResourcesCompat.getDrawable(getResources(), R.drawable.baseline_lock_open_24, null);
                lockButton.setImageDrawable(lock);
            }
        });

        // Bottombar listener
        BottomNavigationView bottomNav = findViewById(R.id.menubar_bottom);
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

                    // Initialize overlay
                    if(recordedPoints.isEmpty()) {
                        statOverlay.setVisibility(VISIBLE);

                        TextView distanceView = findViewById(R.id.main_distance);
                        distanceView.setText(getString(R.string.main_distance, 0.0));

                        TextView speedView = findViewById(R.id.main_speed);
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
                    isRecording = false;
                    didPressStopWhileLowPointCount = false;
                    chronometer.stop();
                    elapsedTime = SystemClock.elapsedRealtime() - chronometer.getBase();

                    Intent intent = new Intent(this, SaveActivity.class);

                    Bundle bundle = new Bundle();

                    bundle.putString("polyline", PolyUtil.encode(recordedPoints));
                    bundle.putString("starttime", startTime);
                    bundle.putString("endtime", dateFormat.format(new Date()));
                    bundle.putDouble("elapsedtime", Math.floor(elapsedTime / 1000.0));
                    bundle.putDouble("dist", totalDist);
                    bundle.putDouble("type", totalDist);
                    intent.putExtras(bundle);

                    startActivity(intent);

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


        // Initialize MapView
        Configuration.getInstance().setUserAgentValue(BuildConfig.LIBRARY_PACKAGE_NAME);
        Configuration.getInstance().setOsmdroidBasePath(getCacheDir());
        Configuration.getInstance().setOsmdroidTileCache(getCacheDir());
        Configuration.getInstance().setCacheMapTileCount((short) 2000);
        Configuration.getInstance().setCacheMapTileOvershoot((short) 800);

        mapView = findViewById(R.id.map);
        mapView.setBackgroundColor(Color.TRANSPARENT);
        mapView.setMultiTouchControls(true);
        mapView.setUseDataConnection(true);

        TilesOverlay tilesOverlay = mapView.getOverlayManager().getTilesOverlay();
        tilesOverlay.setLoadingBackgroundColor(Color.TRANSPARENT);
        tilesOverlay.setLoadingLineColor(Color.TRANSPARENT);

        mapView.getController().setZoom(14.0);
        mapView.setMinZoomLevel(3.0);
        mapView.setMaxZoomLevel(20.0);
        mapView.getZoomController().setVisibility(CustomZoomButtonsController.Visibility.NEVER);
        mapView.setVerticalMapRepetitionEnabled(false);
        mapView.setScrollableAreaLimitDouble(new BoundingBox(
                85.0,
                180.0,
                -85.0,
                -180.0
        ));

        // Initialize route overlay
        route.getOutlinePaint().setColor(Color.BLUE);
        route.getOutlinePaint().setStrokeWidth(7.0f);
        mapView.getOverlayManager().add(route);

        // Marker for current location
        locationOverlay = new MyLocationNewOverlay(new GpsMyLocationProvider(this), mapView);
        locationOverlay.enableMyLocation();
        locationOverlay.setDirectionIcon(null);
        locationOverlay.setDrawAccuracyEnabled(false);

        mapView.getOverlays().add(locationOverlay);

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        // Ask for permissions
        if(ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION}, REQUEST_PERMISSIONS_REQUEST_CODE);
        }
        else {
            startLocationUpdates();
        }

        // Detects whenever location is enabled and creates a marker
        IntentFilter filter = new IntentFilter(LocationManager.MODE_CHANGED_ACTION);

        BroadcastReceiver locationModeReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                locationOverlay = new MyLocationNewOverlay(new GpsMyLocationProvider(context), mapView);
                locationOverlay.enableMyLocation();
                mapView.getOverlays().add(locationOverlay);
            }
        };

        this.registerReceiver(locationModeReceiver, filter);
    }

    private void startLocationUpdates() {
        // Set update intervals
        LocationRequest locationRequest = LocationRequest.create();
        locationRequest.setInterval(UPDATE_INTERVAL_MAX);
        locationRequest.setFastestInterval(UPDATE_INTERVAL_MIN);
        locationRequest.setPriority(Priority.PRIORITY_HIGH_ACCURACY);

        // Check for permissions
        if(ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // Default to Budapest
            defaultToBudapest();

            Toast.makeText(this, "Fine location data is required for accurate tracking!", Toast.LENGTH_SHORT).show();
            ActivityCompat.requestPermissions(this, new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION}, REQUEST_PERMISSIONS_REQUEST_CODE);
            return;
        }
        else if(!isLocationEnabled(this)) {
            // Default to Budapest
            defaultToBudapest();
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
                        mapView.invalidate();

                        // Mark first point, record start time
                        if(recordedPoints.size() == 1) {
                            startTime = dateFormat.format(new Date());

                            Drawable icon = ResourcesCompat.getDrawable(getResources(), R.drawable.baseline_circle_24, null);
                            if(icon != null) {
                                icon.setTint(Color.BLUE);
                            }

                            IconOverlay firstP = new IconOverlay(point, icon);

                            mapView.getOverlayManager().add(firstP);
                            mapView.invalidate();
                        }
                        // Update variables
                        else {
                            double newDistanceLength = SphericalUtil.computeDistanceBetween(recordedPoints.get(recordedPoints.size() - 2), recordedPoints.get(recordedPoints.size() - 1));

                            // Speed
                            currentSpeed = (newDistanceLength / (UPDATE_INTERVAL_MIN / 1000.0)) * 3.6;

                            TextView speedView = findViewById(R.id.main_speed);
                            speedView.setText(getString(R.string.main_speed, currentSpeed));

                            // Distance
                            totalDist += newDistanceLength;

                            TextView distanceView = findViewById(R.id.main_distance);
                            distanceView.setText(getString(R.string.main_distance, totalDist / 1000.0));
                        }
                    }
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

        locationOverlay.enableMyLocation();
        if(ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            startLocationUpdates();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if(requestCode == REQUEST_PERMISSIONS_REQUEST_CODE) {
            // Start location updates
            if(grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Marker for current location
                locationOverlay = new MyLocationNewOverlay(new GpsMyLocationProvider(this), mapView);
                locationOverlay.enableMyLocation();
                mapView.getOverlays().add(locationOverlay);

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

        // Profile
        if(id == R.id.topmenu_profile) {
            // TODO
            // Intent intent = new Intent(ProfileActivity.this, MainActivity.class);
            // startActivity(intent);
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    // Returns whether location **feature** is enabled
    public boolean isLocationEnabled(Context context) {
        LocationManager locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);

        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
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

    public void defaultToBudapest() {
        GeoPoint point = new GeoPoint(47.499, 19.044);
        mapView.getController().setCenter(point);
    }
}