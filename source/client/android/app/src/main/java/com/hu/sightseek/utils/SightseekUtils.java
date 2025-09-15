package com.hu.sightseek.utils;

import android.graphics.Color;
import android.graphics.CornerPathEffect;
import android.graphics.Paint;

import androidx.annotation.NonNull;

import com.google.android.gms.maps.model.LatLng;

import org.osmdroid.util.BoundingBox;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Polyline;

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

    public static void defaultToBudapest(MapView mapView) {
        GeoPoint point = new GeoPoint(BUDAPEST_LATITUDE, BUDAPEST_LONGITUDE);
        mapView.getController().setCenter(point);
    }
}