package com.hu.sightseek.utils;

import androidx.annotation.NonNull;

import com.firebase.geofire.GeoFireUtils;
import com.firebase.geofire.GeoLocation;
import com.google.android.gms.maps.model.LatLng;

import org.osmdroid.util.BoundingBox;
import org.osmdroid.util.GeoPoint;

import java.util.HashMap;
import java.util.List;

public class SightseekSpatialUtils {
    private SightseekSpatialUtils() {}

    public static HashMap<String, Integer> getVisitedCells(List<LatLng> pointList) {
        HashMap<String, Integer> visitedCells = new HashMap<>();

        for(LatLng p : pointList) {
            String hash = GeoFireUtils.getGeoHashForLocation(new GeoLocation(p.latitude, p.longitude), 3);

            Integer count = visitedCells.get(hash);
            if(count == null) {
                count = 0;
            }
            visitedCells.put(hash, count + 1);
        }

        return visitedCells;
    }

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

    // From PolyUtil class rewritten for OSMDroid's GeoPoint
    public static String encode(final List<GeoPoint> path) {
        long lastLat = 0;
        long lastLng = 0;

        final StringBuffer result = new StringBuffer();

        for(final GeoPoint point : path) {
            long lat = Math.round(point.getLatitude() * 1e5);
            long lng = Math.round(point.getLongitude() * 1e5);

            long dLat = lat - lastLat;
            long dLng = lng - lastLng;

            encode(dLat, result);
            encode(dLng, result);

            lastLat = lat;
            lastLng = lng;
        }
        return result.toString();
    }
    private static void encode(long v, StringBuffer result) {
        v = v < 0 ? ~(v << 1) : v << 1;
        while(v >= 0x20) {
            result.append(Character.toChars((int) ((0x20 | (v & 0x1f)) + 63)));
            v >>= 5;
        }
        result.append(Character.toChars((int) (v + 63)));
    }
}
