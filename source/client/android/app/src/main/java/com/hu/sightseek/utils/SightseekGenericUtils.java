package com.hu.sightseek.utils;

import static android.view.View.INVISIBLE;
import static android.view.View.VISIBLE;

import android.app.Activity;
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
import android.view.inputmethod.InputMethodManager;
import android.widget.Toast;

import androidx.core.content.ContextCompat;

import com.google.android.gms.maps.model.LatLng;

import org.osmdroid.util.BoundingBox;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.CustomZoomButtonsController;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.GroundOverlay;
import org.osmdroid.views.overlay.Polyline;

import java.io.IOException;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public final class SightseekGenericUtils {
    public static final String STRAVA_CLIENT_ID = "180650";
    public static final double BUDAPEST_LATITUDE = 47.499;
    public static final double BUDAPEST_LONGITUDE = 19.044;

    private SightseekGenericUtils() {}

    public static void setupRouteLine(Polyline route, boolean isLight) {
        if(isLight) {
            route.getOutlinePaint().setColor(Color.parseColor("#40A7FF"));
            route.getOutlinePaint().setStrokeWidth(7.0f);
            route.setGeodesic(false);

            Paint paint = route.getOutlinePaint();
            paint.setAntiAlias(false);
        }
        else {
            route.getOutlinePaint().setColor(Color.BLUE);
            route.getOutlinePaint().setStrokeWidth(9.0f);

            // Smoothen
            Paint paint = route.getOutlinePaint();
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeJoin(Paint.Join.ROUND);
            paint.setPathEffect(new CornerPathEffect(30f));
        }
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

    public static GroundOverlay createHeatmapOverlay(MapView mapView, ArrayList<LatLng> points) {
        int gridSize = 512;
        int[][] density = new int[gridSize][gridSize];
        BoundingBox box = mapView.getBoundingBox();

        // Gaussian blur
        int radius = 6;
        double sigma = 3.0;
        double twoSigmaSquared = 2 * sigma * sigma;

        for(LatLng p : points) {
            int cx = (int) (((p.longitude - box.getLonWest()) / box.getLongitudeSpanWithDateLine()) * gridSize);
            int cy = (int) (((box.getLatNorth() - p.latitude) / box.getLatitudeSpan()) * gridSize);

            // Neighbors
            for(int dy = -radius; dy <= radius; dy++) {
                for(int dx = -radius; dx <= radius; dx++) {
                    int x = cx + dx;
                    int y = cy + dy;

                    if(x >= 0 && x < gridSize && y >= 0 && y < gridSize) {
                        double distSquared = dx * dx + dy * dy;
                        double weight = Math.exp(-distSquared / twoSigmaSquared);

                        density[y][x] += (int) (weight * 100);
                    }
                }
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
                if(val == 0) {
                    bmp.setPixel(x, y, Color.TRANSPARENT);
                }
                else {
                    float intensity = (float) val / maxDensity;
                    bmp.setPixel(x, y, getHeatmapColor(intensity));
                }
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
        int alpha = (int)(Math.min(1f, intensity * 1.2f) * 255);
        return Color.HSVToColor(alpha, new float[]{hue, 1f, 1f});
    }

    public static void hideKeyboard(Activity activity) {
        View view = activity.getCurrentFocus();
        if(view != null) {
            InputMethodManager manager = (InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);
            manager.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
    }
}