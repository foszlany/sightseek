package com.hu.sightseek.utils;

import static com.hu.sightseek.utils.SightseekRegionalLeaderboardUtils.calculateRegionalDistance;

import android.app.Activity;
import android.content.Context;

import com.hu.sightseek.interfaces.Logger;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.LinearRing;
import org.locationtech.jts.geom.MultiLineString;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.operation.buffer.BufferOp;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.overlay.Polyline;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import diewald_shapeFile.files.shp.shapeTypes.ShpPolyLine;
import diewald_shapeFile.files.shp.shapeTypes.ShpPolygon;
import diewald_shapeFile.shapeFile.ShapeFile;

public final class SightseekVectorizationUtils {
    private static final double TOLERANCE = 0.0002;

    private SightseekVectorizationUtils() {}

    // TODO: Handle exceptions with log & skipping
    public static ArrayList<String> batchVectorize(Activity activity, ArrayList<Polyline> routes, Logger logger) {
        ArrayList<String> results = new ArrayList<>();
        Set<String> countryCodes = new HashSet<>();
        GeometryFactory geometryFactory = new GeometryFactory();
        ExecutorService executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
        AtomicInteger count = new AtomicInteger();

        // Convert to LineString and detect countries
        List<Future<RouteData>> routeDataFutures = new ArrayList<>();
        for(int i = 0; i < routes.size(); i++) {
            final int position = i;
            final Polyline route = routes.get(i);

            routeDataFutures.add(executor.submit(() -> {
                LineString lineString = createLineStringFromPolyline(route, geometryFactory);

                ShapeFile countryShapefile = new ShapeFile(activity.getFilesDir().getAbsolutePath(), "countries");
                countryShapefile.READ();

                Set<String> routeCountryCodes = (getTouchedCountries(lineString, activity, countryShapefile));
                countryCodes.addAll(routeCountryCodes);

                return new RouteData(position, route, lineString, routeCountryCodes);
            }));
        }

        // Wait for routes
        List<RouteData> routeDataset = new ArrayList<>();
        for(Future<RouteData> future : routeDataFutures) {
            try {
                routeDataset.add(future.get());
            }
            catch(Exception ignored) {}
        }

        // Get roads separately
        Map<String, List<LineString>> roadPolylinesPerCountry = getPerCountryRoadPolylines(activity, geometryFactory, countryCodes);

        // Process
        List<Future<ArrayList<Polyline>>> vectorFutures = new ArrayList<>();
        for(RouteData routeData : routeDataset) {
            vectorFutures.add(executor.submit(() -> {
                // Convert route to polygon
                Polygon routePolygon = createPolygonFromLineString(routeData.lineString, TOLERANCE);

                // Filter segments
                List<LineString> filteredRoads = new ArrayList<>();
                Envelope envelope = routeData.lineString.getEnvelopeInternal();
                envelope.expandBy(TOLERANCE);

                for(String code : routeData.countryCodes) {
                    List<LineString> segments = roadPolylinesPerCountry.get(code);
                    if(segments == null) {
                        continue;
                    }

                    for(LineString segment : segments) {
                        if(segment.getEnvelopeInternal().intersects(envelope)) {
                            filteredRoads.add(segment);
                        }
                    }
                }

                MultiLineString roadPolylines = geometryFactory.createMultiLineString(filteredRoads.toArray(new LineString[0]));

                // Calculate intersection
                Geometry vectorizedData = roadPolylines.intersection(routePolygon);

                ArrayList<Polyline> vectorized = new ArrayList<>();
                if(vectorizedData instanceof LineString) {
                    vectorized.add(convertLineStringToPolyline((LineString) vectorizedData));
                }
                else if(vectorizedData instanceof MultiLineString) {
                    vectorized.addAll(convertMultiLineStringToPolyline((MultiLineString) vectorizedData));
                }
                else { // TODO ?
                    System.out.println(vectorizedData.getClass());
                }

                return vectorized;
            }));
        }

        // Create polyline string
        for(Future<ArrayList<Polyline>> future : vectorFutures) {
            try {
                ArrayList<Polyline> polylines = future.get();

                StringBuilder vectorizedDataString = new StringBuilder();
                for(int i = 0; i < polylines.size(); i++) {
                    List<GeoPoint> geoPoints = polylines.get(i).getActualPoints();

                    vectorizedDataString.append(SightseekSpatialUtils.encode(geoPoints));
                    if(i != polylines.size() - 1) {
                        vectorizedDataString.append(";");
                    }
                }

                if(logger != null) {
                    activity.runOnUiThread(() -> {
                        logger.log("Vectorized [" + (count.incrementAndGet()) + "/" + routes.size() + "]");
                    });
                }

                results.add(vectorizedDataString.toString());
            }
            catch(Exception e) {
                throw new RuntimeException(e);
            }
        }

        executor.shutdown();
        return results;
    }
    private static class RouteData {
        int position;
        Polyline route;
        LineString lineString;
        Set<String> countryCodes;
        Envelope envelope;

        RouteData(int position, Polyline route, LineString lineString, Set<String> countryCodes) {
            this.position = position;
            this.route = route;
            this.lineString = lineString;
            this.countryCodes = countryCodes;

            this.envelope = lineString.getEnvelopeInternal();
            this.envelope.expandBy(TOLERANCE);
        }
    }

    public static ArrayList<Polyline> vectorize(Activity activity, Polyline route) {
        ArrayList<Polyline> vectorizedPolylines = new ArrayList<>();
        GeometryFactory geometryFactory = new GeometryFactory();

        // LineString
        LineString lineString = createLineStringFromPolyline(route, geometryFactory);

        // Calculate countries
        Set<String> countryCodes = getTouchedCountries(lineString, activity, null);
        if(countryCodes.isEmpty()) {
            System.out.println("No countries have been detected, halting.");
            return vectorizedPolylines;
        }

        // Route polygon
        Polygon routePolygon = createPolygonFromLineString(lineString, TOLERANCE);

        // Filtered roads
        MultiLineString roadPolylines = getRoadPolylines(activity, geometryFactory, countryCodes, routePolygon.getEnvelopeInternal());

        // Calculate intersection
        Geometry vectorizedData = roadPolylines.intersection(routePolygon);

        // Calculate regional leaderboard data
        calculateRegionalDistance(activity, vectorizedData, countryCodes); // TODO CHANGE!!!!

        // Create polyline(s)
        if(vectorizedData instanceof LineString) {
            Polyline polyline = convertLineStringToPolyline((LineString) vectorizedData);

            vectorizedPolylines.add(polyline);
        }
        else if(vectorizedData instanceof MultiLineString) {
            ArrayList<Polyline> polylines = convertMultiLineStringToPolyline((MultiLineString) vectorizedData);
            vectorizedPolylines.addAll(polylines);
        }   

        return vectorizedPolylines; // TODO NEW CLASS WITH COUNTRYCODES
    }

    private static Set<String> getTouchedCountries(LineString route, Activity activity, ShapeFile countryShapefile) {
        Set<String> touchedCountries = new HashSet<>();

        if(countryShapefile == null) {
            copyShapefileToInternalStorage(activity, "countries");
        }

        try {
            if(countryShapefile == null) {
                countryShapefile = new ShapeFile(activity.getFilesDir().getAbsolutePath(), "countries");
                countryShapefile.READ();
            }

            GeometryFactory geometryFactory = new GeometryFactory();

            // Check for each country
            for(int i = 0; i < countryShapefile.getSHP_shapeCount(); i++) {
                ShpPolygon shape = countryShapefile.getSHP_shape(i);
                String isoCode = countryShapefile.getDBF_record(i)[1].trim();

                // Convert to Coordinate
                ArrayList<Coordinate> shapeCoords = new ArrayList<>();
                double[][] shapePoints = shape.getPoints();
                for(int j = 0; j < shape.getNumberOfPoints(); j++) {
                    shapeCoords.add(new Coordinate(shapePoints[j][0], shapePoints[j][1]));
                }

                // Close multipolygons
                if(!shapeCoords.get(0).equals2D(shapeCoords.get(shapeCoords.size() - 1))) {
                    shapeCoords.add(new Coordinate(shapeCoords.get(0)));
                }

                // Create country polygon
                LinearRing shell = geometryFactory.createLinearRing(shapeCoords.toArray(new Coordinate[0]));
                Polygon countryPolygon = geometryFactory.createPolygon(shell);

                if(route.intersects(countryPolygon)) {
                    touchedCountries.add(isoCode);
                }
            }
        }
        catch(Exception e) {
            throw new RuntimeException(e);
        }

        return touchedCountries;
    }

    private static LineString createLineStringFromPolyline(Polyline route, GeometryFactory geometryFactory) {
        List<GeoPoint> points = route.getActualPoints();
        Coordinate[] coordinates = new Coordinate[points.size()];

        for(int i = 0; i < points.size(); i++) {
            coordinates[i] = new Coordinate(points.get(i).getLongitude(), points.get(i).getLatitude());
        }

        return geometryFactory.createLineString(coordinates);
    }

    private static Polygon createPolygonFromLineString(LineString route, double tolerance) {
        Geometry buffered = BufferOp.bufferOp(route, tolerance);
        if(buffered instanceof Polygon) {
            return (Polygon) buffered;
        }
        else {
            throw new IllegalStateException("Geometry is not a polygon.");
        }
    }

    private static MultiLineString getRoadPolylines(Activity activity, GeometryFactory geometryFactory, Set<String> countryCodes, Envelope filterEnvelope) {
        ArrayList<LineString> lineStringList = new ArrayList<>();

        for(String code : countryCodes) {
            copyShapefileToInternalStorage(activity, code + "_roads");

            try {
                ShapeFile roadsShapeFile = new ShapeFile(activity.getFilesDir().getAbsolutePath(), code + "_roads");
                roadsShapeFile.READ();

                for(int i = 0; i < roadsShapeFile.getSHP_shapeCount(); i++) {
                    ShpPolyLine shape = roadsShapeFile.getSHP_shape(i);
                    double[][] points = shape.getPoints();
                    Coordinate[] coordinates = new Coordinate[points.length];

                    for(int j = 0; j < points.length; j++) {
                        coordinates[j] = new Coordinate(points[j][0], points[j][1]);
                    }

                    LineString segment = geometryFactory.createLineString(coordinates);

                    if(segment.getEnvelopeInternal().intersects(filterEnvelope)) {
                        lineStringList.add(segment);
                    }
                }
            }
            catch(Exception e) {
                throw new RuntimeException("Error reading shapefile for country code: " + code, e);
            }
        }

        return new MultiLineString(lineStringList.toArray(new LineString[0]), geometryFactory);
    }

    private static HashMap<String, List<LineString>> getPerCountryRoadPolylines(Activity activity, GeometryFactory geometryFactory, Set<String> countryCodes) {
        HashMap<String, List<LineString>> roadSegmentsByCountry = new HashMap<>();

        for(String code : countryCodes) {
            copyShapefileToInternalStorage(activity, code + "_roads");

            try {
                ShapeFile roadsShapeFile = new ShapeFile(activity.getFilesDir().getAbsolutePath(), code + "_roads");
                roadsShapeFile.READ();

                List<LineString> segments = new ArrayList<>();
                for(int i = 0; i < roadsShapeFile.getSHP_shapeCount(); i++) {
                    ShpPolyLine shape = roadsShapeFile.getSHP_shape(i);
                    double[][] points = shape.getPoints();

                    Coordinate[] coordinates = new Coordinate[points.length];
                    for(int j = 0; j < points.length; j++) {
                        coordinates[j] = new Coordinate(points[j][0], points[j][1]);
                    }

                    segments.add(geometryFactory.createLineString(coordinates));
                }

                roadSegmentsByCountry.put(code, segments);
            }
            catch(Exception e) {
                return roadSegmentsByCountry;
            }
        }

        return roadSegmentsByCountry;
    }

    private static Polyline convertLineStringToPolyline(LineString lineString) {
        Coordinate[] coords = lineString.getCoordinates();
        ArrayList<GeoPoint> geoPoints = new ArrayList<>();

        for(Coordinate coord : coords) {
            GeoPoint geo = new GeoPoint(coord.x, coord.y);
            geoPoints.add(geo);
        }

        Polyline polyline = new Polyline();
        polyline.setPoints(geoPoints);

        return polyline;
    }

    private static ArrayList<Polyline> convertMultiLineStringToPolyline(MultiLineString multiLineString) {
        ArrayList<Polyline> polylines = new ArrayList<>();

        for(int i = 0; i < multiLineString.getNumGeometries(); i++) {
            LineString line = (LineString) multiLineString.getGeometryN(i);
            Coordinate[] coords = line.getCoordinates();

            ArrayList<GeoPoint> geoPoints = new ArrayList<>();
            for(Coordinate coord : coords) {
                double lat = coord.y;
                double lon = coord.x;
                geoPoints.add(new GeoPoint(lat, lon));
            }

            Polyline polyline = new Polyline();
            polyline.setPoints(geoPoints);

            polylines.add(polyline);
        }

        return polylines;
    }

    public static boolean copyShapefileToInternalStorage(Context context, String fileName) {
        String[] fileExtensions = new String[]{".shp", ".dbf", ".shx"};

        for(String extension : fileExtensions) {
            try {
                File outFile = new File(context.getFilesDir(), fileName + extension);
                //if(outFile.exists()) { TODO remove when done
                //    return true;
                //}

                try(InputStream in = context.getAssets().open("shapefiles/" + fileName + extension);
                    OutputStream out = new FileOutputStream(outFile)) {
                    byte[] buffer = new byte[1024];
                    int read;

                    while((read = in.read(buffer)) != -1) {
                        out.write(buffer, 0, read);
                    }
                }
            }
            catch(IOException ignored) {
                return false;
            }
        }

        return true;
    }
}