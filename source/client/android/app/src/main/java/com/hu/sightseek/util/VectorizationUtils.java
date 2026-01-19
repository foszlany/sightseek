package com.hu.sightseek.util;

import static com.hu.sightseek.helper.WKConverter.convertLineGeometryToPolyline;

import android.app.Activity;
import android.content.Context;

import com.hu.sightseek.interfaces.Logger;
import com.hu.sightseek.model.VectorizedDataRecord;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryCollection;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.LinearRing;
import org.locationtech.jts.geom.MultiLineString;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.geom.PrecisionModel;
import org.locationtech.jts.geom.prep.PreparedGeometry;
import org.locationtech.jts.geom.prep.PreparedGeometryFactory;
import org.locationtech.jts.operation.buffer.BufferOp;
import org.locationtech.jts.operation.union.UnaryUnionOp;
import org.locationtech.jts.precision.GeometryPrecisionReducer;
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
import java.util.stream.Collectors;

import diewald_shapeFile.files.shp.shapeTypes.ShpPolyLine;
import diewald_shapeFile.files.shp.shapeTypes.ShpPolygon;
import diewald_shapeFile.shapeFile.ShapeFile;

/** Utilities to vectorize (map streets onto) polylines */
public final class VectorizationUtils {
    /** Value used to create an extra buffer around polylines */
    static final double TOLERANCE = 0.0002;

    /** Private constructor */
    private VectorizationUtils() {}

    /** Batch version of vectorize()
     * @param activity Activity
     * @param routes List of Polylines to process
     * @param logger Logger to log progress, can be null
     * @return A list of VectorizedDataRecord objects
     */
    public static List<VectorizedDataRecord> batchVectorize(Activity activity, List<Polyline> routes, Logger logger) {
        List<VectorizedDataRecord> results = new ArrayList<>();
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

                Set<String> routeCountryCodes = getTouchedCountries(activity, lineString, countryShapefile);
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
        Map<String, List<LineString>> roadPolylinesPerCountry = getPerCountryRoadPolylines(activity, countryCodes, geometryFactory);

        // Process
        List<Future<RouteResult>> vectorFutures = new ArrayList<>();
        for(RouteData routeData : routeDataset) {
            vectorFutures.add(executor.submit(() -> {
                // Convert route to polygon
                Polygon routePolygon = createPolygonFromLineString(routeData.lineString);

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

                PrecisionModel precisionModel = new PrecisionModel(1e6);
                Geometry reducedVectorizedData = GeometryPrecisionReducer.reduce(vectorizedData, precisionModel);

                if(reducedVectorizedData instanceof LineString || reducedVectorizedData instanceof MultiLineString) {
                    return new RouteResult(reducedVectorizedData, routePolygon);
                }
                else if(reducedVectorizedData instanceof Polygon || reducedVectorizedData instanceof GeometryCollection) {
                    throw new RuntimeException("Vectorized data has 2 dimensional elements.");
                }
                else {
                    return null;
                }
            }));
        }

        // Create polyline string
        for(Future<RouteResult> future : vectorFutures) {
            try {
                if(logger != null) {
                    activity.runOnUiThread(() -> logger.log("Vectorized [" + (count.incrementAndGet()) + "/" + routes.size() + "]"));
                }

                RouteResult routeResult = future.get();
                results.add(new VectorizedDataRecord(routeResult.vectorizedGeometry, routeResult.routePolygon, countryCodes));
            }
            catch(Exception e) {
                throw new RuntimeException(e);
            }
        }

        executor.shutdown();
        return results;
    }

    /** Holds extra data about a route */
    private static class RouteData {
        /** Position inside the array */
        final int position;
        /** Route as a Polyline */
        final Polyline polyline;
        /** Route as a LineString */
        final LineString lineString;
        /** Country codes */
        final Set<String> countryCodes;
        /** Envelope of the route */
        final Envelope envelope;

        /** Constructor
         * @param position Position inside the array
         * @param polyline Route as a Polyline
         * @param lineString Route as a LineString
         * @param countryCodes Country codes
         */
        RouteData(int position, Polyline polyline, LineString lineString, Set<String> countryCodes) {
            this.position = position;
            this.polyline = polyline;
            this.lineString = lineString;
            this.countryCodes = countryCodes;

            this.envelope = lineString.getEnvelopeInternal();
            this.envelope.expandBy(TOLERANCE);
        }
    }

    /** Holds the result of a vectorization along with a buffered polyline */
    private static class RouteResult {
        /** Vectorized data as a Geometry */
        final Geometry vectorizedGeometry;
        /** Route as a buffered Polygon */
        final Polygon routePolygon;

        /**
         * Constructor
         * @param vectorizedGeometry Vectorized data as a Geometry
         * @param routePolygon Route as a buffered Polygon
         */
        private RouteResult(Geometry vectorizedGeometry, Polygon routePolygon) {
            this.vectorizedGeometry = vectorizedGeometry;
            this.routePolygon = routePolygon;
        }
    }

    /**
     * Maps street lines to a polyline
     * @param activity Activity
     * @param route Polyline to process
     * @return VectorizedDataRecord object
     */
    public static VectorizedDataRecord vectorize(Activity activity, Polyline route) {
        GeometryFactory geometryFactory = new GeometryFactory();

        // LineString
        LineString lineString = createLineStringFromPolyline(route, geometryFactory);

        // Calculate countries
        Set<String> countryCodes = getTouchedCountries(activity, lineString, null);
        if(countryCodes.isEmpty()) {
            return new VectorizedDataRecord();
        }

        // Route polygon
        Polygon routePolygon = createPolygonFromLineString(lineString);

        // Filtered roads
        List<LineString> roadPolylines = getRoadPolylines(activity, routePolygon, countryCodes, geometryFactory);

        // Calculate intersection
        PreparedGeometry preparedRoutePolygon = PreparedGeometryFactory.prepare(routePolygon);
        List<Geometry> intersectionLines = roadPolylines.parallelStream()
                            .map(ls -> preparedRoutePolygon.intersects(ls) ? ls.intersection(routePolygon) : null)
                            .filter(g -> (g != null && !g.isEmpty()))
                            .collect(Collectors.toList());

        Geometry vectorizedDataGeometry = UnaryUnionOp.union(intersectionLines);

        if(vectorizedDataGeometry == null || vectorizedDataGeometry.isEmpty()) {
            return new VectorizedDataRecord(vectorizedDataGeometry, routePolygon, countryCodes);
        }

        // Reduce
        PrecisionModel precisionModel = new PrecisionModel(1e6);
        Geometry reducedVectorizedDataGeometry = GeometryPrecisionReducer.reduce(vectorizedDataGeometry, precisionModel);

        // Create polyline(s)
        List<Polyline> vectorizedDataPolylines = convertLineGeometryToPolyline(reducedVectorizedDataGeometry);

        return new VectorizedDataRecord(reducedVectorizedDataGeometry, routePolygon, countryCodes, vectorizedDataPolylines);
    }

    /**
     * Gets the countries touched by a polyline (a polyline touches a country when it has at least one point inside it)
     * @param activity         Activity
     * @param route            Route as a LineString
     * @param countryShapefile Countries shapefile. If null, it will be opened.
     * @return Set of countries a polyline touches.
     */
    private static Set<String> getTouchedCountries(Activity activity, LineString route, ShapeFile countryShapefile) {
        Set<String> touchedCountries = new HashSet<>();

        try {
            if(countryShapefile == null) {
                copyShapefileToInternalStorage(activity, "countries");
                countryShapefile = new ShapeFile(activity.getFilesDir().getAbsolutePath(), "countries");
                countryShapefile.READ();
            }

            GeometryFactory geometryFactory = new GeometryFactory();

            // Check for each country
            for(int i = 0; i < countryShapefile.getSHP_shapeCount(); i++) {
                ShpPolygon shape = countryShapefile.getSHP_shape(i);
                String isoCode = countryShapefile.getDBF_record(i)[1].trim();

                // Convert to Coordinate
                List<Coordinate> shapeCoords = new ArrayList<>();
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
            throw new RuntimeException("Shapefile exception: (" + countryShapefile + "): " + e);
        }

        return touchedCountries;
    }

    /**
     * Converts a Polyline to a LineString
     * @param route Route as a Polyline
     * @param geometryFactory Geometry factory
     * @return LineString
     */
    private static LineString createLineStringFromPolyline(Polyline route, GeometryFactory geometryFactory) {
        List<GeoPoint> points = route.getActualPoints();
        Coordinate[] coordinates = new Coordinate[points.size()];

        for(int i = 0; i < points.size(); i++) {
            coordinates[i] = new Coordinate(points.get(i).getLongitude(), points.get(i).getLatitude());
        }

        return geometryFactory.createLineString(coordinates);
    }

    /**
     * Converts a LineString to a buffered Polygon
     * @param route Route as a LineString
     * @return Polygon
     */
    private static Polygon createPolygonFromLineString(LineString route) {
        Geometry buffered = BufferOp.bufferOp(route, TOLERANCE);
        if(buffered instanceof Polygon) {
            return (Polygon) buffered;
        }
        else {
            throw new IllegalStateException("Geometry is not a polygon.");
        }
    }

    /**
     * Reads and filters the needed road polylines
     *
     * @param activity        Activity
     * @param routePolygon    Route as a buffered polygon
     * @param countryCodes    Country codes
     * @param geometryFactory Geometry factory
     * @return List of LineStrings holding the roads
     */
    private static List<LineString> getRoadPolylines(Activity activity, Polygon routePolygon, Set<String> countryCodes, GeometryFactory geometryFactory) {
        List<LineString> lineStringList = new ArrayList<>();

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

                    if(segment.intersects(routePolygon)) {
                        lineStringList.add(segment);
                    }
                }
            }
            catch(Exception e) {
                throw new RuntimeException("Shapefile exception: (" + code + "): " + e);
            }
        }

        return lineStringList;
    }

    /**
     * Reads and filters the needed road polylines and matches them with a country code.
     * @param activity Activity
     * @param countryCodes Country codes
     * @param geometryFactory Geometry factory
     * @return Map of country codes and their respective roads as a List of LineStrings
     */
    private static HashMap<String, List<LineString>> getPerCountryRoadPolylines(Activity activity, Set<String> countryCodes, GeometryFactory geometryFactory) {
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

    /**
     * Copies a shapefile to the internal storage if needed
     * @param context Context
     * @param fileName File name without extension
     * @return True if the operation succeeded (or file already existed), false if not
     */
    public static boolean copyShapefileToInternalStorage(Context context, String fileName) {
        String[] fileExtensions = new String[]{".shp", ".dbf", ".shx"};

        for(String extension : fileExtensions) {
            try {
                File outFile = new File(context.getFilesDir(), fileName + extension);
                if(outFile.exists()) {
                    return true;
                }

                try(InputStream in = context.getAssets().open("shapefiles/" + fileName + extension);
                    OutputStream out = new FileOutputStream(outFile)) {
                    byte[] buffer = new byte[1024];
                    int read;

                    while((read = in.read(buffer)) != -1) {
                        out.write(buffer, 0, read);
                    }
                }
            }
            catch(IOException e) {
                return false;
            }
        }

        return true;
    }
}