package com.hu.sightseek.utils;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.CornerPathEffect;
import android.graphics.Paint;
import android.graphics.drawable.BitmapDrawable;

import androidx.annotation.NonNull;

import com.google.android.gms.maps.model.LatLng;

import org.osmdroid.util.BoundingBox;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.CustomZoomButtonsController;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.GroundOverlay;
import org.osmdroid.views.overlay.Polyline;

import java.util.ArrayList;
import java.util.List;

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

    public static void setupRouteLine(Polyline route) {
        route.getOutlinePaint().setColor(Color.BLUE);
        route.getOutlinePaint().setStrokeWidth(9.0f);

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

    public static void defaultToBudapest(MapView mapView) {
        GeoPoint point = new GeoPoint(BUDAPEST_LATITUDE, BUDAPEST_LONGITUDE);
        mapView.getController().setCenter(point);
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