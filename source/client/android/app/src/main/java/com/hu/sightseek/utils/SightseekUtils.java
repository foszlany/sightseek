package com.hu.sightseek.utils;

import static android.view.View.INVISIBLE;
import static android.view.View.VISIBLE;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.CornerPathEffect;
import android.graphics.Paint;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.location.Address;
import android.location.Geocoder;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import com.firebase.geofire.GeoFireUtils;
import com.firebase.geofire.core.GeoHashQuery;
import com.google.android.gms.maps.model.LatLng;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Source;
import com.hu.sightseek.db.LocalDatabaseDAO;

import org.osmdroid.util.BoundingBox;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.CustomZoomButtonsController;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.GroundOverlay;
import org.osmdroid.views.overlay.Polyline;

import java.io.IOException;
import java.io.OutputStream;
import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Future;

public final class SightseekUtils {
    public static final double BUDAPEST_LATITUDE = 47.499;
    public static final double BUDAPEST_LONGITUDE = 19.044;

    private SightseekUtils() {}

    @NonNull
    public static BoundingBox getBoundingBox(List<LatLng> pointList) {
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

        return new BoundingBox(maxLat, maxLon, minLat, minLon);
    }

    public static void setupRouteLine(Polyline route, boolean isFaint) {
        if(isFaint) {
            route.getOutlinePaint().setColor(Color.parseColor("#40A7FF"));
            route.getOutlinePaint().setStrokeWidth(7.0f);
        }
        else {
            route.getOutlinePaint().setColor(Color.BLUE);
            route.getOutlinePaint().setStrokeWidth(9.0f);
        }

        // Smoothen
        Paint paint = route.getOutlinePaint();
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
        paint.setPathEffect(new CornerPathEffect(30f));
    }

    public static void setupZoomSettings(MapView mapView, double zoom) {
        mapView.getController().setZoom(zoom);
        mapView.setMinZoomLevel(3.0);
        mapView.setMaxZoomLevel(20.0);
        mapView.setMultiTouchControls(true);
        mapView.getZoomController().setVisibility(CustomZoomButtonsController.Visibility.NEVER);
        mapView.setVerticalMapRepetitionEnabled(false);
        mapView.setScrollableAreaLimitDouble(new BoundingBox(
                85.0,
                180.0,
                -85.0,
                -180.0
        ));
    }

    public static String getLocationString(Context ctx, double latitude, double longitude) {
        String locationString = "";

        Geocoder geocoder = new Geocoder(ctx, Locale.getDefault());
        try {
            List<Address> addresses = geocoder.getFromLocation(latitude, longitude, 1);
            if(addresses != null && !addresses.isEmpty()) {
                locationString = addresses.get(0).getLocality();

                if(locationString == null) {
                    locationString = addresses.get(0).getAdminArea();

                    if(locationString == null) {
                        locationString = addresses.get(0).getCountryName();

                        if(locationString == null || "null".equals(locationString)) {
                            locationString = "Unknown location";
                        }
                    }
                }
            }
        }
        catch(IOException ignored) {}

        return locationString;
    }

    public static HashMap<String, Serializable> getDetailedGenericStatistics(Context ctx) {
        HashMap<String, Serializable> res = new HashMap<>();

        if(FirebaseAuth.getInstance().getCurrentUser() != null) {
            FirebaseFirestore fireStoreDb = FirebaseFirestore.getInstance();
            String uid = FirebaseAuth.getInstance().getCurrentUser().getUid();

            fireStoreDb.collection("users")
                    .document(uid)
                    .get(Source.SERVER)
                    .addOnSuccessListener(documentSnapshot -> {
                        if(documentSnapshot.exists()) {
                            Map<String, Object> data = documentSnapshot.getData();

                            if(data == null) {
                                res.put("visited_cells", 0.0);
                            }
                            else {
                                res.put("visited_cells", (double) data.size());
                            }
                        }
                        else {
                            res.put("visited_cells", 0.0);
                        }
                    })
                    .addOnFailureListener(e -> {
                        res.put("visited_cells", 0.0);
                    });
        }

        LocalDatabaseDAO dao = new LocalDatabaseDAO(ctx);
        ArrayList<LatLng> allPoints = dao.getAllPoints();
        dao.close();

        res.put("total_points", (double) allPoints.size());

        LatLng medianPoint = getMedianPoint(allPoints);
        res.put("median_lat", medianPoint.latitude);
        res.put("median_lon", medianPoint.longitude);

        // TODO
        res.put("isolated_lat", 0.0);
        res.put("isolated_lon", 0.0);

        return res;
    }

    public static LatLng getMedianPoint(ArrayList<LatLng> allPoints) {
        int n = allPoints.size();
        double[] lats = new double[n];
        double[] lons = new double[n];

        for(int i = 0; i < n; i++) {
            LatLng p = allPoints.get(i);
            lats[i] = p.latitude;
            lons[i] = p.longitude;
        }

        Arrays.sort(lats);
        Arrays.sort(lons);

        return new LatLng(lats[n / 2], lons[n / 2]);
    }

    public static void createScreenshot(Context ctx, View view, String name, View excludedView) {
        Bitmap bitmap = Bitmap.createBitmap(view.getWidth(), view.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);

        if(excludedView != null) {
            excludedView.setVisibility(INVISIBLE);
            view.draw(canvas);
            excludedView.setVisibility(VISIBLE);
        }
        else {
            view.draw(canvas);
        }

        String timeStamp = new SimpleDateFormat("yyyy-MM-dd_HH:mm:ss", Locale.US).format(new Date());
        String fileName = name + "_" + timeStamp + ".jpg";

        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) { // ANDROID 10.0+
            ContentValues values = new ContentValues();
            ContentResolver resolver = ctx.getContentResolver();

            values.put(MediaStore.Images.Media.DISPLAY_NAME, fileName);
            values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
            values.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Sightseek");
            values.put(MediaStore.Images.Media.IS_PENDING, 1);

            Uri collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);
            Uri imageUri = resolver.insert(collection, values);

            if(imageUri != null) {
                try(OutputStream out = resolver.openOutputStream(imageUri)) {
                    if(out == null) {
                        Toast.makeText(ctx, "An error has occurred while trying to save the screenshot: Bad URI", Toast.LENGTH_LONG).show();
                        return;
                    }

                    bitmap.compress(Bitmap.CompressFormat.JPEG, 100, out);
                }
                catch(IOException e) {
                    Toast.makeText(ctx, "An error has occurred while trying to save the screenshot: Failed creation", Toast.LENGTH_LONG).show();
                    return;
                }

                values.clear();
                values.put(MediaStore.Images.Media.IS_PENDING, 0);
                resolver.update(imageUri, values, null, null);

                Toast.makeText(ctx, "Saved: " + fileName, Toast.LENGTH_LONG).show();
            }

        }
        else { // < ANDROID 10.0 TODO
            Toast.makeText(ctx, "how will i even test this", Toast.LENGTH_LONG).show();
        }
    }

    public static void defaultToBudapest(MapView mapView) {
        GeoPoint point = new GeoPoint(BUDAPEST_LATITUDE, BUDAPEST_LONGITUDE);
        mapView.getController().setCenter(point);
    }

    public static Bitmap getBitmapFromVectorDrawable(Context context, int drawableId) {
        Drawable drawable = ContextCompat.getDrawable(context, drawableId);
        if(drawable == null) {
            throw new NullPointerException("Drawable does not exist!");
        }

        drawable.setBounds(0, 0, drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight());

        Bitmap bitmap = Bitmap.createBitmap(drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight(), Bitmap.Config.ARGB_8888);

        Canvas canvas = new Canvas(bitmap);
        drawable.draw(canvas);

        return bitmap;
    }

    //TODO: just rewrite it with vectors, this is rather dumb
    // Should also move it to RecordActivty
    public static GroundOverlay createHeatmapOverlay(MapView mapView, ArrayList<LatLng> points) {
        int gridSize = 256;
        int[][] density = new int[gridSize][gridSize];
        BoundingBox box = mapView.getBoundingBox();

        // Calculate densities
        for(LatLng p : points) {
            int x = (int) (((p.longitude - box.getLonWest()) / box.getLongitudeSpanWithDateLine()) * gridSize);
            int y = (int) (((box.getLatNorth() - p.latitude) / box.getLatitudeSpan()) * gridSize);
            if(x >= 0 && x < gridSize && y >= 0 && y < gridSize) {
                density[y][x]++;
            }
        }

        // Get highest density
        int maxDensity = 0;
        for(int[] row : density) {
            for(int val : row) {
                if(val > maxDensity) {
                    maxDensity = val;
                }
            }
        }

        // Generate overlay image
        Bitmap bmp = Bitmap.createBitmap(gridSize, gridSize, Bitmap.Config.ARGB_8888);
        for(int y = 0; y < gridSize; y++) {
            for(int x = 0; x < gridSize; x++) {
                int val = density[y][x];
                int color = (val == 0) ? Color.TRANSPARENT : getHeatmapColor((float) val / maxDensity);
                bmp.setPixel(x, y, color);
            }
        }

        // Create and Add overlay
        BitmapDrawable drawable = new BitmapDrawable(mapView.getContext().getResources(), bmp);
        GroundOverlay overlay = new org.osmdroid.views.overlay.GroundOverlay();

        overlay.setImage(drawable.getBitmap());

        GeoPoint topLeft = new GeoPoint(box.getLatNorth(), box.getLonWest());
        GeoPoint bottomRight = new GeoPoint(box.getLatSouth(), box.getLonEast());
        overlay.setPosition(topLeft, bottomRight);

        mapView.getOverlays().add(0, overlay);
        mapView.invalidate();

        return overlay;
    }
    private static int getHeatmapColor(float intensity) {
        float hue = (1f - intensity) * 240f;
        return Color.HSVToColor(new float[]{hue, 1f, 1f});
    }
}