package com.hu.sightseek.provider;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Point;

import com.google.android.gms.maps.model.LatLng;

import org.osmdroid.util.BoundingBox;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.Projection;
import org.osmdroid.views.overlay.GroundOverlay;

import java.util.List;

public class HeatmapOverlayProvider {
    private static final int RADIUS = 6;
    private static final double SIGMA = 3.0;
    private static final int minGridHeight = 250;
    private static final int maxGridHeight = 850;

    private static double[][] kernel;

    private HeatmapOverlayProvider() {}

    public static GroundOverlay createHeatmapOverlay(MapView mapView, List<LatLng> points, boolean isStrong) {
        if(kernel == null) {
            setKernel();
        }

        // Setup projection
        Projection projection = mapView.getProjection();
        BoundingBox box = mapView.getBoundingBox();

        Point topLeftPixel = new Point();
        Point bottomRightPixel = new Point();

        GeoPoint topLeftGeo = new GeoPoint(box.getLatNorth(), box.getLonWest());
        GeoPoint bottomRightGeo = new GeoPoint(box.getLatSouth(), box.getLonEast());

        projection.toPixels(topLeftGeo, topLeftPixel);
        projection.toPixels(bottomRightGeo, bottomRightPixel);

        // Grid height
        double zoomLevel = mapView.getZoomLevelDouble();
        double minZoomLevel = mapView.getMinZoomLevel();
        double maxZoomLevel = mapView.getMaxZoomLevel();
        double t = (zoomLevel - minZoomLevel) / (maxZoomLevel - minZoomLevel);

        int gridHeight = (int) (maxGridHeight - Math.pow(t, 0.5) * (maxGridHeight - minGridHeight));
        if(isStrong) {
            gridHeight = (int) (gridHeight * 1.2);
        }

        // Pixel dimensions
        int pixelHeight = bottomRightPixel.y - topLeftPixel.y;
        int pixelWidth = bottomRightPixel.x - topLeftPixel.x;

        // Grid width
        int gridWidth = (int)(gridHeight * ((double) pixelWidth / pixelHeight));

        // Create density grid
        int[][] density = new int[gridHeight][gridWidth];
        for(LatLng p : points) {
            GeoPoint geoPoint = new GeoPoint(p.latitude, p.longitude);
            if(!box.contains(geoPoint)) {
                continue;
            }

            Point pixelPoint = new Point();
            projection.toPixels(geoPoint, pixelPoint);

            double xRatio = ((double) (pixelPoint.x - topLeftPixel.x) / pixelWidth);
            double yRatio = ((double) (pixelPoint.y - topLeftPixel.y) / pixelHeight);
            xRatio = Math.max(0, Math.min(1, xRatio));
            yRatio = Math.max(0, Math.min(1, yRatio));

            int cx = (int) (xRatio * gridWidth);
            int cy = (int) (yRatio * gridHeight);
            cx = Math.max(0, Math.min(cx, gridWidth - 1));
            cy = Math.max(0, Math.min(cy, gridHeight - 1));

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

        // Get max/local density value(s)
        int[][] localMax = null;
        int maxDensity = 0;

        if(isStrong) {
            double zoomFactor = (zoomLevel - minZoomLevel) / (maxZoomLevel - minZoomLevel);
            zoomFactor = Math.max(0, Math.min(1, zoomFactor));
            int radius = (int)(10 + zoomFactor * (40 - 10));

            localMax = computeLocalMax(density, radius);
        }
        else {
            for(int[] row : density) {
                for(int val : row) {
                    if(val > maxDensity) {
                        maxDensity = val;
                    }
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
                    if(isStrong) {
                        int lm = localMax[y][x];
                        float intensity = (float) val / Math.max(lm, 1);
                        pixels[y * gridWidth + x] = getHeatmapColor(intensity, true);
                    }
                    else {
                        float intensity = (float) val / Math.max(maxDensity, 1);
                        pixels[y * gridWidth + x] = getHeatmapColor(intensity, false);
                    }
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

    private static int[][] maxFilterHorizontal(int[][] src, int radius) {
        int h = src.length;
        int w = src[0].length;
        int[][] out = new int[h][w];

        int window = radius * 2 + 1;

        for(int y = 0; y < h; y++) {
            int[] row = src[y];
            int[] left = new int[w];
            int[] right = new int[w];

            // Left max
            for(int i = 0; i < w; i++) {
                if(i % window == 0) {
                    left[i] = row[i];
                }
                else {
                    left[i] = Math.max(left[i - 1], row[i]);
                }
            }

            // Right max
            for(int i = w - 1; i >= 0; i--) {
                if(i == w - 1 || (i + 1) % window == 0) {
                    right[i] = row[i];
                }
                else {
                    right[i] = Math.max(right[i + 1], row[i]);
                }
            }

            // Combine
            for(int i = 0; i < w; i++) {
                int r = Math.min(i + radius, w - 1);
                int l = Math.max(i - radius, 0);
                out[y][i] = Math.max(right[l], left[r]);
            }
        }

        return out;
    }

    private static int[][] maxFilterVertical(int[][] src, int radius) {
        int h = src.length;
        int w = src[0].length;
        int[][] out = new int[h][w];

        int window = radius * 2 + 1;

        for(int x = 0; x < w; x++) {
            int[] col = new int[h];
            for(int y = 0; y < h; y++) {
                col[y] = src[y][x];
            }

            int[] left = new int[h];
            int[] right = new int[h];

            // Top-down max
            for(int i = 0; i < h; i++) {
                if(i % window == 0) {
                    left[i] = col[i];
                }
                else {
                    left[i] = Math.max(left[i - 1], col[i]);
                }
            }

            // Bottom-up max
            for(int i = h - 1; i >= 0; i--) {
                if(i == h - 1 || (i + 1) % window == 0) {
                    right[i] = col[i];
                }
                else {
                    right[i] = Math.max(right[i + 1], col[i]);
                }
            }

            // Combine
            for(int i = 0; i < h; i++) {
                int r = Math.min(i + radius, h - 1);
                int l = Math.max(i - radius, 0);
                out[i][x] = Math.max(right[l], left[r]);
            }
        }

        return out;
    }

    private static int[][] computeLocalMax(int[][] density, int radius) {
        int[][] h = maxFilterHorizontal(density, radius);
        return maxFilterVertical(h, radius);
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
            intensity = (float) (Math.log(1 + intensity * k) / Math.log(1 + k));
        }

        float hue = (1f - intensity) * 240f;
        int alpha = (int)(Math.min(1f, intensity * 1.2f) * 255);

        return Color.HSVToColor(alpha, new float[]{hue, 1f, 1f});
    }
}