package com.hu.sightseek;

import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Location;
import android.os.Bundle;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.maps.android.PolyUtil;

import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.CustomZoomButtonsController;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider;
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay;

import java.util.ArrayList;

public class MainActivity extends AppCompatActivity {
    private static final int UPDATE_INTERVAL_MAX = 6000;
    private static final int UPDATE_INTERVAL_MIN = 4000;
    private static final int REQUEST_PERMISSIONS_REQUEST_CODE = 1;
    private static final int MINIMUM_REQUIRED_POINTS_PER_ACTIVITY = 15;

    private MapView mapView;
    private FusedLocationProviderClient fusedLocationClient;
    private LocationCallback locationCallback;
    private MyLocationNewOverlay locationOverlay;
    private ArrayList<LatLng> recordedPoints;
    private boolean isRecording;
    private boolean didPressStopWhileLowPointCount;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Configuration.getInstance().load(
                getApplicationContext(),
                PreferenceManager.getDefaultSharedPreferences(getApplicationContext())
        );
        Configuration.getInstance().setUserAgentValue(getPackageName());
        setContentView(R.layout.activity_main);

        // Default values
        recordedPoints = new ArrayList<>();
        isRecording = false;
        didPressStopWhileLowPointCount = false;

        // Add Menu
        Toolbar toolbar = findViewById(R.id.menubar_main);
        setSupportActionBar(toolbar);

        // Bottombar listeners
        BottomNavigationView bottomNav = findViewById(R.id.menubar_bottom);

        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();

            // Recording
            if(id == R.id.bottommenu_record) {
                // Begin
                if(!isRecording) {
                    isRecording = true;

                    bottomNav.getMenu()
                            .findItem(R.id.bottommenu_record)
                            .setIcon(R.drawable.baseline_pause_circle_24);

                    bottomNav.getMenu()
                            .findItem(R.id.bottommenu_record)
                            .setTitle("Pause");

                    bottomNav.getMenu()
                            .findItem(R.id.bottommenu_stop)
                            .setVisible(true);
                }
                // Pause
                else {
                    isRecording = false;

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

                    // Encode and store
                    String res = PolyUtil.encode(recordedPoints);
                    // Do something with result...

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
                }

                return true;
            }

            return false;
        });


        // Initialize MapView
        mapView = findViewById(R.id.map);
        mapView.setBackgroundColor(Color.BLACK);
        mapView.getOverlayManager().getTilesOverlay().setLoadingLineColor(Color.TRANSPARENT);
        mapView.setMultiTouchControls(true);
        mapView.setTileSource(TileSourceFactory.MAPNIK);
        mapView.getController().setZoom(15.0);
        mapView.getZoomController().setVisibility(CustomZoomButtonsController.Visibility.NEVER);

        // Marker for current location
        locationOverlay = new MyLocationNewOverlay(
                new GpsMyLocationProvider(this), mapView
        );
        locationOverlay.enableMyLocation();
        mapView.getOverlays().add(locationOverlay);

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        // Ask for permissions
        if(ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION}, REQUEST_PERMISSIONS_REQUEST_CODE);
        }
        else {
            startLocationUpdates();
        }
    }

    private void startLocationUpdates() {
        // Set update intervals
        LocationRequest locationRequest = LocationRequest.create();
        locationRequest.setInterval(UPDATE_INTERVAL_MAX);
        locationRequest.setFastestInterval(UPDATE_INTERVAL_MIN);
        locationRequest.setPriority(Priority.PRIORITY_HIGH_ACCURACY);

        // Check for permissions
        if(ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Fine location data is required for accurate tracking!", Toast.LENGTH_SHORT).show();
            ActivityCompat.requestPermissions(this, new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION}, REQUEST_PERMISSIONS_REQUEST_CODE);
            return;
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
                    if(mapView != null) {
                        mapView.getController().animateTo(point);
                        mapView.getController().setCenter(point);
                    }

                    // Record point if needed
                    if(isRecording) {
                        double lat = point.getLatitude();
                        double lng = point.getLongitude();
                        LatLng latLng = new LatLng(lat, lng);

                        recordedPoints.add(latLng);
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
                    mapView.getController().animateTo(point);
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
            if(grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startLocationUpdates();
            }
            else {
                // Check if user clicked on "Don't ask again"
                // Handled later when buttons exist
                /*
                if(!ActivityCompat.shouldShowRequestPermissionRationale(this, android.Manifest.permission.ACCESS_FINE_LOCATION)) {
                    Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                    Uri uri = Uri.fromParts("package", getPackageName(), null);
                    intent.setData(uri);
                    startActivity(intent);
                    Toast.makeText(this, "You must enable precise location permission to track your precision!", Toast.LENGTH_LONG).show();
                }
                else {
                    Toast.makeText(this, "Precise location permission is required to track your position!", Toast.LENGTH_LONG).show();
                }
                */
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
}