package com.hu.sightseek.providers;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;

import com.google.android.gms.maps.model.LatLng;

import org.osmdroid.util.BoundingBox;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.GroundOverlay;

import java.util.ArrayList;

public class HeatmapProvider {
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
}