package com.hu.sightseek.util;

import static com.hu.sightseek.helper.WKConverter.convertLineGeometryToPolyline;
import static com.hu.sightseek.util.GenericUtils.copyShapefileToInternalStorage;
import static com.hu.sightseek.util.GeometryUtils.BUFFER_TOLERANCE;
import static com.hu.sightseek.util.GeometryUtils.createLineStringFromPolyline;
import static com.hu.sightseek.util.GeometryUtils.createPolygonFromLineString;
import static com.hu.sightseek.util.GeometryUtils.getTouchedCountries;

import android.app.Activity;

import com.hu.sightseek.interfaces.Logger;
import com.hu.sightseek.model.VectorizedDataRecord;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryCollection;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.MultiLineString;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.geom.PrecisionModel;
import org.locationtech.jts.geom.prep.PreparedGeometry;
import org.locationtech.jts.geom.prep.PreparedGeometryFactory;
import org.locationtech.jts.operation.union.UnaryUnionOp;
import org.locationtech.jts.precision.GeometryPrecisionReducer;
import org.osmdroid.views.overlay.Polyline;

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
import diewald_shapeFile.shapeFile.ShapeFile;

/** Utilities to vectorize (map streets onto) polylines */
public final class VectorizationUtils {
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
                Polygon routePolygon = createPolygonFromLineString(routeData.lineString, BUFFER_TOLERANCE);

                // Filter segments
                List<LineString> filteredRoads = new ArrayList<>();
                Envelope envelope = routeData.lineString.getEnvelopeInternal();
                envelope.expandBy(BUFFER_TOLERANCE);

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
            this.envelope.expandBy(BUFFER_TOLERANCE);
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
        Set<String> countryCodes = getTouchedCountries(activity, lineString);
        if(countryCodes.isEmpty()) {
            return new VectorizedDataRecord();
        }

        // Route polygon
        Polygon routePolygon = createPolygonFromLineString(lineString, BUFFER_TOLERANCE);

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
     * Reads and filters the needed road polylines
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
}