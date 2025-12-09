package com.hu.sightseek.provider;

import android.graphics.Bitmap;
import android.graphics.Color;

import com.google.android.gms.maps.model.LatLng;

import org.osmdroid.util.BoundingBox;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.GroundOverlay;

import java.util.List;

public class HeatmapOverlayProvider {
    private static final int RADIUS = 6;
    private static final double SIGMA = 3.0;
    private static final int minGridHeight = 250;
    private static final int maxGridHeight = 1000;

    private static double[][] kernel;

    private HeatmapOverlayProvider() {}

    public static GroundOverlay createHeatmapOverlay(MapView mapView, List<LatLng> points, boolean isStrong) {
        double zoomLevel = mapView.getZoomLevelDouble();
        double minZoomLevel = mapView.getMinZoomLevel();
        double maxZoomLevel = mapView.getMaxZoomLevel();

        // Calculate grid height based on zoom
        double t = (zoomLevel - minZoomLevel) / (maxZoomLevel - minZoomLevel);
        double exp = 0.5;

        int gridHeight = (int)(maxGridHeight - Math.pow(t, exp) * (maxGridHeight - minGridHeight));

        if(isStrong) {
            gridHeight = (int)(gridHeight * 1.2);
        }

        // Get dimensions
        BoundingBox box = mapView.getBoundingBox();

        double lonSpan = box.getLongitudeSpanWithDateLine();
        double latSpan = box.getLatitudeSpan();
        double midLat = (box.getLatNorth() + box.getLatSouth()) / 2.0;
        double lonSpanMeters = lonSpan * Math.cos(Math.toRadians(midLat));
        double aspect = lonSpanMeters / latSpan;

        int gridWidth = (int)(gridHeight * aspect);
        int[][] density = new int[gridHeight][gridWidth];

        // Get kernel
        if(kernel == null) {
            setKernel();
        }

        // Create density grid
        for(LatLng p : points) {
            int cx = (int) (((p.longitude - box.getLonWest()) / box.getLongitudeSpanWithDateLine()) * gridWidth);
            int cy = (int) (((box.getLatNorth() - p.latitude) / box.getLatitudeSpan()) * gridHeight);

            for(int dy = -RADIUS; dy <= RADIUS; dy++) {
                for(int dx = -RADIUS; dx <= RADIUS; dx++) {
                    int x = cx + dx;
                    int y = cy + dy;

                    if(x >= 0 && x < gridWidth && y >= 0 && y < gridHeight) {
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
        int[] pixels = new int[gridWidth * gridHeight];
        for(int y = 0; y < gridHeight; y++) {
            for(int x = 0; x < gridWidth; x++) {
                int val = density[y][x];

                if(val == 0) {
                    pixels[y * gridWidth + x] = Color.TRANSPARENT;
                }
                else {
                    float intensity = (float) val / maxDensity;
                    pixels[y * gridWidth + x] = getHeatmapColor(intensity, isStrong);
                }
            }
        }

        // Create bitmap
        Bitmap bmp = Bitmap.createBitmap(gridWidth, gridHeight, Bitmap.Config.ARGB_8888);
        bmp.setPixels(pixels, 0, gridWidth, 0, 0, gridWidth, gridHeight);

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
        for(int dy = -RADIUS; dy <= RADIUS; dy++) {
            for(int dx = -RADIUS; dx <= RADIUS; dx++) {
                double distSquared = dx * dx + dy * dy;
                kernel[dy + RADIUS][dx + RADIUS] = Math.exp(-distSquared / twoSigmaSquared) * 100;
            }
        }
    }

    private static int getHeatmapColor(float intensity, boolean isStrong) {
        if(isStrong) {
            float k = 12;
            intensity = (float) Math.max(intensity, 0.01);
            intensity = (float)(Math.log(1 + intensity * k) / Math.log(1 + k));
        }

        float hue = (1f - intensity) * 240f;
        int alpha = (int)(Math.min(1f, intensity * (isStrong ? 2f : 1.2f)) * 255);

        return Color.HSVToColor(alpha, new float[]{hue, 1f, 1f});
    }

}