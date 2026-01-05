package com.hu.sightseek.util;

import androidx.annotation.NonNull;

import com.firebase.geofire.GeoFireUtils;
import com.firebase.geofire.GeoLocation;

import org.osmdroid.util.BoundingBox;
import org.osmdroid.util.GeoPoint;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class SpatialUtils {
    private SpatialUtils() {}

    public static HashMap<String, Integer> getVisitedCells(List<GeoPoint> pointList) {
        HashMap<String, Integer> visitedCells = new HashMap<>();

        for(GeoPoint p : pointList) {
            String hash = GeoFireUtils.getGeoHashForLocation(new GeoLocation(p.getLatitude(), p.getLongitude()), 3);

            Integer count = visitedCells.get(hash);
            if(count == null) {
                count = 0;
            }
            visitedCells.put(hash, count + 1);
        }

        return visitedCells;
    }

    @NonNull
    public static BoundingBox getBoundingBox(List<GeoPoint> pointList) {
        double minLat = Double.MAX_VALUE;
        double maxLat = -Double.MAX_VALUE;
        double minLon = Double.MAX_VALUE;
        double maxLon = -Double.MAX_VALUE;

        for(GeoPoint p : pointList) {
            if(p.getLatitude() < minLat) {
                minLat = p.getLatitude();
            }
            if(p.getLatitude() > maxLat) {
                maxLat = p.getLatitude();
            }
            if(p.getLongitude() < minLon) {
                minLon = p.getLongitude();
            }
            if(p.getLongitude() > maxLon) {
                maxLon = p.getLongitude();
            }
        }

        return new BoundingBox(maxLat, maxLon, minLat, minLon);
    }

    // From Google Maps API Library's PolyUtil class modified for OSMDroid's GeoPoint
    public static List<GeoPoint> decode(final String encodedPath) {
        int len = encodedPath.length();

        // For speed we preallocate to an upper bound on the final length, then
        // truncate the array before returning.
        final List<GeoPoint> path = new ArrayList<>();
        int index = 0;
        int lat = 0;
        int lng = 0;

        while (index < len) {
            int result = 1;
            int shift = 0;
            int b;
            do {
                b = encodedPath.charAt(index++) - 63 - 1;
                result += b << shift;
                shift += 5;
            } while (b >= 0x1f);
            lat += (result & 1) != 0 ? ~(result >> 1) : (result >> 1);

            result = 1;
            shift = 0;
            do {
                b = encodedPath.charAt(index++) - 63 - 1;
                result += b << shift;
                shift += 5;
            } while (b >= 0x1f);
            lng += (result & 1) != 0 ? ~(result >> 1) : (result >> 1);

            path.add(new GeoPoint(lat * 1e-5, lng * 1e-5));
        }

        return path;
    }
}
