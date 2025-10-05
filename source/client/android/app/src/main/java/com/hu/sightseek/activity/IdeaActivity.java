package com.hu.sightseek.activity;

import static com.hu.sightseek.utils.SightseekUtils.getLocationString;
import static com.hu.sightseek.utils.SightseekUtils.getMedianPoint;
import static com.hu.sightseek.utils.SightseekUtils.setupZoomSettings;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.location.Address;
import android.location.Geocoder;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.bumptech.glide.Glide;
import com.google.android.gms.maps.model.LatLng;
import com.google.firebase.auth.FirebaseAuth;
import com.google.maps.android.PolyUtil;
import com.hu.sightseek.R;
import com.hu.sightseek.enums.SavedAttractionStatus;
import com.hu.sightseek.fragment.SelectLocationFragment;
import com.hu.sightseek.db.LocalDatabaseDAO;
import com.hu.sightseek.model.Activity;
import com.hu.sightseek.model.Attraction;
import com.hu.sightseek.utils.SightseekUtils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.osmdroid.config.Configuration;
import org.osmdroid.util.BoundingBox;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.TilesOverlay;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.concurrent.Executors;

import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;

public class IdeaActivity extends AppCompatActivity {
    private static final String overpassUrl = "https://overpass-api.de/api/interpreter";

    private Attraction currentAttraction;
    private HashSet<Long> ignoredIds;

    private JSONArray data;
    private MapView mapView;
    private Marker marker;
    private String locationString;
    private String type;
    private int radius;

    private ArrayList<Activity> activities;

    private TextView nameTextView;
    private TextView typeTextView;
    private TextView radiusTextView;
    private ImageView imageView;
    private RadioGroup radioGroup;

    private boolean isQuerying;

    private LatLng referencePoint;
    private LatLng locationPoint;
    private LatLng medianPoint;
    private LatLng boundingBoxPoint;
    private int referenceIndex;

    @SuppressLint("ClickableViewAccessibility")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_idea);
        Configuration.getInstance().load(
                getApplicationContext(),
                PreferenceManager.getDefaultSharedPreferences(getApplicationContext())
        );
        Configuration.getInstance().setUserAgentValue(getPackageName());

        // Check if user is logged in
        FirebaseAuth mAuth = FirebaseAuth.getInstance();
        if(mAuth.getCurrentUser() == null) {
            startActivity(new Intent(this, BannerActivity.class));
            finish();
            return;
        }

        // Variables
        currentAttraction = null;
        ignoredIds = new HashSet<>();
        Executors.newSingleThreadExecutor().execute(() -> {
            LocalDatabaseDAO dao = new LocalDatabaseDAO(this);

            activities = dao.getAllActivities();
            ignoredIds.addAll(dao.getAttractionIds());

            dao.close();
        });

        referenceIndex = -1;
        referencePoint = null;
        locationPoint = null;
        medianPoint = null;
        boundingBoxPoint = null;

        isQuerying = false;

        nameTextView = findViewById(R.id.idea_name);
        typeTextView = findViewById(R.id.idea_type);
        radiusTextView = findViewById(R.id.idea_radiusvalue);
        imageView = findViewById(R.id.idea_img);

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

        // Add
        Button addButton = findViewById(R.id.idea_addbtn);
        addButton.setOnClickListener(v -> {
            if(currentAttraction == null) {
                return;
            }

            LocalDatabaseDAO dao = new LocalDatabaseDAO(this);
            dao.addAttraction(currentAttraction.getId(), currentAttraction.getName(), currentAttraction.getPlace(), currentAttraction.getLatitude(), currentAttraction.getLongitude(), SavedAttractionStatus.SAVED.getIndex());
            dao.printAllAttractions();
            dao.close();

            ignoredIds.add(currentAttraction.getId());
            currentAttraction = null;

            findReferencePoint();
        });

        // Ignore
        Button ignoreButton = findViewById(R.id.idea_ignorebtn);
        ignoreButton.setOnClickListener(v -> {
            if(currentAttraction == null) {
                return;
            }

            LocalDatabaseDAO dao = new LocalDatabaseDAO(this);
            dao.addAttraction(currentAttraction.getId(), currentAttraction.getName(), currentAttraction.getPlace(), currentAttraction.getLatitude(), currentAttraction.getLongitude(), SavedAttractionStatus.IGNORED.getIndex());
            dao.printAllAttractions();
            dao.close();

            ignoredIds.add(currentAttraction.getId());
            currentAttraction = null;

            findReferencePoint();
        });

        // Next
        Button nextButton = findViewById(R.id.idea_nextbtn);
        nextButton.setOnClickListener(v -> {
            Glide.with(imageView)
                    .load(R.drawable.loading)
                    .into(imageView);

            currentAttraction = null;
            findReferencePoint();
        });

        // Location button
        radioGroup = findViewById(R.id.idea_radiogroup);
        radioGroup.setOnCheckedChangeListener((group, checkedId) -> {
            if(checkedId == R.id.idea_radio_locationbtn) {
                initLocationFragment();
            }
        });

        // Allow multiple clicks
        RadioButton locationButton = findViewById(R.id.idea_radio_locationbtn);
        locationButton.setOnTouchListener((v, event) -> {
            if(event.getAction() == MotionEvent.ACTION_UP && locationButton.isChecked()) {
                initLocationFragment();
            }
            return false;
        });

        // Radius bar
        radiusTextView.setText(getString(R.string.idea_radiuskm, 13.0));
        SeekBar radiusBar = findViewById(R.id.idea_radiusbar);
        radius = radiusBar.getProgress();
        radiusBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                radiusTextView.setText(getString(R.string.idea_radiuskm, progress + 1.0));
                moveText(seekBar, progress);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                radius = seekBar.getProgress();
                data = null;
            }

            public void moveText(SeekBar seekBar, int progress) {
                int thumbOffset = seekBar.getThumbOffset();
                int seekBarWidth = seekBar.getWidth() - seekBar.getPaddingLeft() - seekBar.getPaddingRight();
                float thumbPos = seekBar.getPaddingLeft() + ((seekBarWidth * progress) / (float)seekBar.getMax());

                radiusTextView.setX(thumbPos - (radiusTextView.getWidth() / 2f) + thumbOffset);
            }
        });

        // Check whether there are activities stored
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
        }
        else {
            // Setup map
            mapView = findViewById(R.id.idea_map);
            mapView.setBackgroundColor(Color.TRANSPARENT);
            mapView.setUseDataConnection(true);

            TilesOverlay tilesOverlay = mapView.getOverlayManager().getTilesOverlay();
            tilesOverlay.setLoadingBackgroundColor(Color.TRANSPARENT);
            tilesOverlay.setLoadingLineColor(Color.TRANSPARENT);

            setupZoomSettings(mapView, 14.0);

            findReferencePoint();
        }
    }

    private void initLocationFragment() {
        Bundle bundle = new Bundle();
        if(locationPoint != null) {
            bundle.putParcelable("referencePoint", new GeoPoint(locationPoint.latitude, locationPoint.longitude));
        }

        SelectLocationFragment dialog = new SelectLocationFragment();
        dialog.setArguments(bundle);
        dialog.show(getSupportFragmentManager(), "selectLocationPopup");
    }

    public void findReferencePoint() {
        if(isQuerying) {
            return;
        }

        referencePoint = new LatLng(0, 0);

        // Get values
        radioGroup = findViewById(R.id.idea_radiogroup);
        int checkedId = radioGroup.getCheckedRadioButtonId();

        // Current location
        if(checkedId == R.id.idea_radio_locationbtn) {
            if(referenceIndex != R.id.idea_radio_locationbtn) {
                data = null;
                referenceIndex = R.id.idea_radio_locationbtn;
            }

            referencePoint = locationPoint;

            findAttraction();
        }

        // Median point
        else if(checkedId == R.id.idea_radio_medianbtn) {
            if(medianPoint == null || referenceIndex != R.id.idea_radio_medianbtn) {
                data = null;

                ArrayList<LatLng> points = new ArrayList<>();

                for(int i = 0; i < activities.size(); i++) {
                    points.addAll(PolyUtil.decode(activities.get(i).getPolyline()));
                }

                medianPoint = getMedianPoint(points);

                referenceIndex = R.id.idea_radio_medianbtn;
            }

            referencePoint = medianPoint;
            findAttraction();
        }

        // Bounding box
        else if(checkedId == R.id.idea_radio_boundingboxbtn) {
            if(boundingBoxPoint == null || referenceIndex != R.id.idea_radio_boundingboxbtn) {
                data = null;

                ArrayList<LatLng> allPoints = new ArrayList<>();

                for(int i = 0; i < activities.size(); i++) {
                    ArrayList<LatLng> points = new ArrayList<>(PolyUtil.decode(activities.get(i).getPolyline()));

                    allPoints.addAll(points);
                }

                BoundingBox boundingBox = SightseekUtils.getBoundingBox(allPoints);
                boundingBoxPoint = new LatLng(boundingBox.getCenterLatitude(), boundingBox.getCenterLongitude());

                referenceIndex = R.id.idea_radio_boundingboxbtn;
            }

            referencePoint = boundingBoxPoint;
            findAttraction();
        }
    }

    // Triggers when a location is picked on the Fragment
    public void onNewLocationSelected(GeoPoint point) {
        locationPoint = new LatLng(point.getLatitude(), point.getLongitude());
        referenceIndex = R.id.idea_radio_medianbtn; // Reset for a new query
    }

    public void findAttraction() {
        if(data != null && data.length() != 0) {
            retrieveAndSetupElementFromJson();
            return;
        }

        isQuerying = true;

        nameTextView.setText(R.string.idea_loading);
        typeTextView.setText(R.string.idea_wait);

        // Query
        try {
            String query = "[out:json][timeout:15];"
                    + "node[\"tourism\"][\"name\"]"

                    /* Exclude accommodations */
                    + "[\"tourism\"!=\"apartment\"]"
                    + "[\"tourism\"!=\"guest_house\"]"
                    + "[\"tourism\"!=\"hotel\"]"
                    + "[\"tourism\"!=\"hostel\"]"
                    + "[\"tourism\"!=\"motel\"]"
                    + "[\"tourism\"!=\"alpine_hut\"]"
                    + "[\"tourism\"!=\"camp_site\"]"
                    + "[\"tourism\"!=\"caravan_site\"]"
                    + "[\"tourism\"!=\"chalet\"]"
                    + "[\"tourism\"!=\"resort\"]"
                    + "[\"tourism\"!=\"wilderness_hut\"]"
                    + "[\"tourism\"!=\"lodging\"]"
                    + "[\"tourism\"!=\"information\"]"

                    + "(around:" + (1000 + (radius * 1000)) + ","
                    + referencePoint.latitude + ","
                    + referencePoint.longitude + ");"
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
                    if(!response.isSuccessful() || response.body() == null) {
                        initErrorViews("Could not fetch data. Try again later");
                    }
                    else {
                        try {
                            String json = response.body().string();
                            JSONObject root = new JSONObject(json);
                            data = root.getJSONArray("elements");

                            if(data.length() == 0) {
                                if(root.has("remark")) {
                                    initErrorViews("Request timed out. Please try again later.");
                                }
                                else {
                                    initErrorViews("Nothing was found. Try increasing the radius.");
                                    data = null;
                                }
                            }
                            else {
                                retrieveAndSetupElementFromJson();
                            }

                        }
                        catch(JSONException e) {
                            initErrorViews("Nothing was found or list was exhausted. Try increasing the radius.");
                        }
                    }

                    isQuerying = false;
                }

                @Override
                public void onFailure(@NonNull okhttp3.Call call, @NonNull IOException e) {
                    runOnUiThread(() ->
                        Toast.makeText(IdeaActivity.this, "Failed to reach server. Please try again later.", Toast.LENGTH_LONG).show()
                    );
                    isQuerying = false;
                }
            });
        }
        catch(UnsupportedEncodingException e) {
            runOnUiThread(() ->
                Toast.makeText(IdeaActivity.this, "Unsupported encode exception, terminating query.", Toast.LENGTH_LONG).show()
            );
            isQuerying = false;
        }
    }

    public void retrieveAndSetupElementFromJson() {
        if(data.length() == 0) {
            initErrorViews("Nothing else was found. Try increasing the radius.");
            return;
        }

        try {
            int randomIndex = new Random().nextInt(data.length());
            JSONObject randomElement = data.getJSONObject(randomIndex);
            JSONObject tags = randomElement.optJSONObject("tags");

            // Check for ignored ids
            long id = tags != null ? randomElement.optLong("id", 0) : 0;
            if(ignoredIds.contains(id)) {
                data.remove(randomIndex);
                retrieveAndSetupElementFromJson();
                return;
            }

            // Grab attributes
            String name = tags != null ? tags.optString("name", "") : "";

            type = tags != null ? tags.optString("tourism", "") : "";
            type = type.substring(0,1).toUpperCase() + type.substring(1).toLowerCase();
            type = type.replace("_", " ");
            if("Yes".equals(type)) {
                type = "Attraction";
            }

            double latitude = randomElement.optDouble("lat", 0);
            double longitude = randomElement.optDouble("lon", 0);
            GeoPoint point = new GeoPoint(latitude, longitude);

            locationString = getLocationString(this, latitude, longitude);

            if(id != 0) {
                currentAttraction = new Attraction(id, name, locationString, latitude, longitude, SavedAttractionStatus.INVALID);
            }

            String fallbackUrl = "https://www.google.com/search?q=" + locationString + " " + name.replace(" ", "%20");
            String url = tags != null ? tags.optString("url", fallbackUrl) : fallbackUrl;

            // Pray that there's an image
            getAndSetImage(tags, name, locationString);

            // Set views
            runOnUiThread(() -> {
                if(isActivityDead()) return;

                nameTextView.setText(name);
                typeTextView.setText(getString(R.string.idea_typelocation, type, locationString));

                ImageButton linkButton = findViewById(R.id.idea_google);
                linkButton.setOnClickListener(v -> {
                    Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                    startActivity(intent);
                });

                if(marker != null) {
                    mapView.getOverlays().remove(marker);
                }
                marker = new Marker(mapView);
                marker.setPosition(point);
                marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);

                mapView.getOverlays().add(marker);
                mapView.getController().setCenter(point);
                mapView.invalidate();
            });

            data.remove(randomIndex);
        }
        catch(JSONException e) {
            initErrorViews("Nothing was found. Try increasing the radius.");
        }
    }

    // Attempt to find an image using image key or Wikimedia Commons
    public void getAndSetImage(JSONObject tags, String name, String locationString) {
        String imageURL;

        // image key
        imageURL = tags != null ? tags.optString("image", "") : "";
        if(!imageURL.isBlank()) {
            runOnUiThread(() -> {
                if(isActivityDead()) return;
                Glide.with(imageView)
                        .load(imageURL)
                        .placeholder(R.drawable.loading)
                        .error(R.drawable.loading)
                        .into(imageView);
            });

            return;
        }

        // Wikimedia Commons
        String wikimediaReference = tags != null ? tags.optString("wikimedia_commons", "") : "";
        String apiUrl;
        try {
            if(!wikimediaReference.isBlank() && wikimediaReference.startsWith("File:")) {
                apiUrl = "https://commons.wikimedia.org/w/api.php?action=query&titles=File:"
                        + URLEncoder.encode( wikimediaReference.substring(5), "UTF-8")
                        + "&prop=imageinfo&iiprop=url&format=json";
            }
            else {
                apiUrl = "https://commons.wikimedia.org/w/api.php?action=query"
                        + "&generator=search"
                        + "&gsrsearch=" + URLEncoder.encode(locationString + " " + name, "UTF-8")
                        + "&gsrlimit=5"
                        + "&gsrnamespace=6"
                        + "&prop=imageinfo&iiprop=url|mime"
                        + "&format=json";
            }

            // Query
            OkHttpClient client = new OkHttpClient();
            Request request = new Request.Builder()
                    .url(apiUrl)
                    .header("User-Agent", "com.hu.sightseek (nineforget42@gmail.com")
                    .build();

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onResponse(@NonNull okhttp3.Call call, @NonNull okhttp3.Response response) throws IOException {
                    if(response.isSuccessful()) {
                        try {
                            if(response.body() == null) {
                                return;
                            }

                            String json = response.body().string();
                            JSONObject root = new JSONObject(json);
                            JSONObject query = root.getJSONObject("query");
                            JSONObject pages = query.getJSONObject("pages");

                            Iterator<String> keys = pages.keys();
                            while(keys.hasNext()) {
                                String key = keys.next();
                                JSONObject page = pages.getJSONObject(key);

                                JSONArray imageInfo = page.getJSONArray("imageinfo");

                                if(imageInfo.length() > 0) {
                                    JSONObject info = imageInfo.getJSONObject(0);
                                    String mime = info.optString("mime", "");
                                    String imageUrl = info.optString("url", "");

                                    if(mime.startsWith("image/")) {
                                        runOnUiThread(() -> {
                                            if(isActivityDead()) return;
                                            Glide.with(imageView)
                                                    .load(imageUrl)
                                                    .placeholder(R.drawable.loading)
                                                    .error(R.drawable.placeholder)
                                                    .into(imageView);
                                        });
                                        return;
                                    }
                                }
                            }

                            runOnUiThread(() -> {
                                if(isActivityDead()) return;
                                Glide.with(imageView)
                                        .load(R.drawable.placeholder)
                                        .into(imageView);
                            });
                        }
                        catch(JSONException ex) {
                            runOnUiThread(() -> {
                                if(isActivityDead()) return;
                                Glide.with(imageView)
                                        .load(R.drawable.placeholder)
                                        .into(imageView);
                            });
                        }
                    }
                }

                @Override
                public void onFailure(@NonNull okhttp3.Call call, @NonNull IOException e) {
                    runOnUiThread(() -> {
                        if(isActivityDead()) return;
                        Glide.with(imageView)
                                .load(R.drawable.placeholder)
                                .into(imageView);
                    });
                }
            });
        }
        catch(UnsupportedEncodingException ignored) {}
    }

    public void initErrorViews(String msg) {
        runOnUiThread(() -> {
            if(isActivityDead()) return;

            Toast.makeText(IdeaActivity.this, msg, Toast.LENGTH_LONG).show();
            Glide.with(imageView)
                    .load(R.drawable.placeholder)
                    .into(imageView);

            nameTextView.setText(R.string.idea_nothingwasfound);
            typeTextView.setText(R.string.idea_increaseradius);
        });
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
    public void onDestroy() {
        super.onDestroy();
    }

    @Override
    public void onResume() {
        super.onResume();
        if(mapView != null) {
            mapView.onResume();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if(mapView != null) {
            mapView.onResume();
        }
    }

    public boolean isActivityDead() {
        return (isDestroyed() || isFinishing());
    }
}