package com.hu.sightseek.providers;

import android.graphics.Bitmap;
import android.graphics.Color;

import com.google.android.gms.maps.model.LatLng;

import org.osmdroid.util.BoundingBox;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.GroundOverlay;

import java.util.List;

public class HeatmapProvider {
    private static final int RADIUS = 6;
    private static final double SIGMA = 3.0;
    private static final int GRIDHEIGHT = 300;

    private static double[][] kernel;

    private HeatmapProvider() {}

    public static GroundOverlay createHeatmapOverlay(MapView mapView, List<LatLng> points) {
        // Get dimensions
        BoundingBox box = mapView.getBoundingBox();

        double lonSpan = box.getLongitudeSpanWithDateLine();
        double latSpan = box.getLatitudeSpan();
        double midLat = (box.getLatNorth() + box.getLatSouth()) / 2.0;
        double lonSpanMeters = lonSpan * Math.cos(Math.toRadians(midLat));
        double aspect = lonSpanMeters / latSpan;

        int gridWidth = (int)(GRIDHEIGHT * aspect);
        int[][] density = new int[GRIDHEIGHT][gridWidth];

        // Get kernel
        if(kernel == null) {
            setKernel();
        }

        // Create density grid
        for(LatLng p : points) {
            int cx = (int) (((p.longitude - box.getLonWest()) / box.getLongitudeSpanWithDateLine()) * gridWidth);
            int cy = (int) (((box.getLatNorth() - p.latitude) / box.getLatitudeSpan()) * GRIDHEIGHT);

            for(int dy = -RADIUS; dy <= RADIUS; dy++) {
                for(int dx = -RADIUS; dx <= RADIUS; dx++) {
                    int x = cx + dx;
                    int y = cy + dy;

                    if(x >= 0 && x < gridWidth && y >= 0 && y < GRIDHEIGHT) {
                        density[y][x] += (int) kernel[dy + RADIUS][dx + RADIUS];
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
        int[] pixels = new int[gridWidth * GRIDHEIGHT];
        for(int y = 0; y < GRIDHEIGHT; y++) {
            for(int x = 0; x < gridWidth; x++) {
                int val = density[y][x];

                if(val == 0) {
                    pixels[y * gridWidth + x] = Color.TRANSPARENT;
                }
                else {
                    float intensity = (float) val / maxDensity;
                    pixels[y * gridWidth + x] = getHeatmapColor(intensity);
                }
            }
        }

        // Create bitmap
        Bitmap bmp = Bitmap.createBitmap(gridWidth, GRIDHEIGHT, Bitmap.Config.ARGB_8888);
        bmp.setPixels(pixels, 0, gridWidth, 0, 0, gridWidth, GRIDHEIGHT);

        // Create overlay
        GroundOverlay overlay = new GroundOverlay();
        overlay.setImage(bmp);

        GeoPoint topLeft = new GeoPoint(box.getLatNorth(), box.getLonWest());
        GeoPoint bottomRight = new GeoPoint(box.getLatSouth(), box.getLonEast());
        overlay.setPosition(topLeft, bottomRight);

        return overlay;
    }

    private static void setKernel() {
        double twoSigmaSquared = 2 * SIGMA * SIGMA;

        kernel = new double[2 * RADIUS + 1][2 * RADIUS + 1];
        for (int dy = -RADIUS; dy <= RADIUS; dy++) {
            for (int dx = -RADIUS; dx <= RADIUS; dx++) {
                double distSquared = dx * dx + dy * dy;
                kernel[dy + RADIUS][dx + RADIUS] = Math.exp(-distSquared / twoSigmaSquared) * 100;
            }
        }
    }

    private static int getHeatmapColor(float intensity) {
        float hue = (1f - intensity) * 240f;
        int alpha = (int)(Math.min(1f, intensity * 1.2f) * 255);
        return Color.HSVToColor(alpha, new float[]{hue, 1f, 1f});
    }
}