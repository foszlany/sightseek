package com.hu.sightseek.util;

import static com.hu.sightseek.helper.CountryInfo.getContinent;
import static com.hu.sightseek.helper.CountryInfo.getCountry;
import static com.hu.sightseek.helper.RegionalDistanceAggregator.aggregateDistances;
import static com.hu.sightseek.util.GenericUtils.copyShapefileToInternalStorage;
import static com.hu.sightseek.util.GeometryUtils.TOLERANCE;
import static com.hu.sightseek.util.GeometryUtils.createLineStringFromPolyline;
import static com.hu.sightseek.util.GeometryUtils.createPolygonFromLineString;
import static com.hu.sightseek.util.GeometryUtils.getTouchedCountries;

import android.content.Context;

import androidx.annotation.NonNull;

import com.hu.sightseek.db.LocalDatabaseDAO;
import com.hu.sightseek.model.RegionalEntry;
import com.hu.sightseek.model.VectorizedDataRecord;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.LinearRing;
import org.locationtech.jts.geom.MultiLineString;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.geom.PrecisionModel;
import org.locationtech.jts.geom.util.GeometryFixer;
import org.locationtech.jts.index.strtree.STRtree;
import org.locationtech.jts.operation.distance.DistanceOp;
import org.locationtech.jts.operation.overlayng.OverlayNG;
import org.locationtech.jts.operation.union.UnaryUnionOp;
import org.osmdroid.views.overlay.Polyline;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

import diewald_shapeFile.files.shp.shapeTypes.ShpPolygon;
import diewald_shapeFile.shapeFile.ShapeFile;

public final class RegionalLeaderboardUtils {
    /** File postfixes for region types */
    private static final ArrayList<String> regionTypes = new ArrayList<>(Arrays.asList("smallregion", "largeregion", "country"));
    /** Radius of the Earth in kilometers */
    private static final double R = 6371;

    /** Private constructor */
    private RegionalLeaderboardUtils() {}

    /**
     * Batch version of calculateNewRegionalDistance()
     * @param context Context
     * @param vectorizedDataRecords Data records of the vectorized activities
     * @param countryCodes Merged country codes of the touched countries
     */
    public static Map<String, Double> batchCalculateNewRegionalDistance(Context context, List<VectorizedDataRecord> vectorizedDataRecords, Set<String> countryCodes) {
        if(vectorizedDataRecords == null || vectorizedDataRecords.isEmpty()) {
            return null;
        }

        GeometryFactory geometryFactory = new GeometryFactory();

        // Merge roads
        List<Geometry> newRoads = new ArrayList<>();
        List<Polygon> routePolygons = new ArrayList<>();
        for(VectorizedDataRecord v : vectorizedDataRecords) {
            newRoads.add(v.getVectorizedDataGeometry());
            routePolygons.add(v.getRoutePolygon());
        }

        Geometry mergedNewRoads = UnaryUnionOp.union(newRoads);

        // Load all vectors from activities
        MultiLineString allRoads = getAllRoads(context, geometryFactory, routePolygons);

        // Detect which shp files exist, select smallest (smallregion -> largeregion -> country)
        List<String> shapefiles = getSmallestAvailableRegionFilenames(context, countryCodes);

        // Get unique roads
        Geometry uniqueRoads = getUniqueRoads(mergedNewRoads, allRoads, geometryFactory);

        // Calculate the distance per region along with the containing geometries
        List<RegionalEntry> entries = getDistances(context, geometryFactory, uniqueRoads, shapefiles);

        // Convert distances to map
        return aggregateDistances(entries);
    }

    /**
     * Calculates unique distances per region for a new activity
     * @param context Context
     * @param vectorizedDataRecord Data records of the vectorized activity
     * @param ignoredActivity ID of the activity the regional distance is being calculated for, set to a negative number if not needed
     * @return Map containing regions with their unique distances or null if the vectorized road dataset is null
     */
    private static Map<String, Double> calculateNewRegionalDistance(Context context, VectorizedDataRecord vectorizedDataRecord, int ignoredActivity) {
        Geometry newRoads = vectorizedDataRecord.getVectorizedDataGeometry();
        Polygon routePolygon = vectorizedDataRecord.getRoutePolygon();
        Set<String> countryCodes = vectorizedDataRecord.getCountryCodes();

        if(vectorizedDataRecord.getVectorizedDataGeometry() == null || newRoads.isEmpty()) {
            return null;
        }

        GeometryFactory geometryFactory = new GeometryFactory(new PrecisionModel(1e4));

        // Load all vectors from activities
        MultiLineString allRoads = getAllRoads(context, geometryFactory, routePolygon, ignoredActivity);

        // Detect which shp files exist, select smallest (smallregion -> largeregion -> country)
        List<String> shapefiles = getSmallestAvailableRegionFilenames(context, countryCodes);

        // Get unique roads
        Geometry uniqueRoads = getUniqueRoads(newRoads, allRoads, geometryFactory);

        // Calculate the distance per region along with the containing geometries
        List<RegionalEntry> entries = getDistances(context, geometryFactory, uniqueRoads, shapefiles);

        // Convert distances to map
        return aggregateDistances(entries);
    }

    /**
     * Calculates unique distances per region for a new activity
     * @param context Context
     * @param vectorizedDataRecord Data records of the vectorized activity
     * @return Map containing regions with their unique distances or null if the vectorized road dataset is null
     */
    public static Map<String, Double> calculateNewRegionalDistance(Context context, VectorizedDataRecord vectorizedDataRecord) {
        return calculateNewRegionalDistance(context, vectorizedDataRecord, -1);
    }

    /** Calculates unique distances per region for an already existing activity
     * @param context Context
     * @param vectorizedRoads Vectorized roads
     * @param route Route polyline
     * @param activityId ID of the activity
     * @return Map containing regions with their unique distances or null if vectorizedRoads is null
     */
    public static Map<String, Double> calculateCurrentRegionalDistance(Context context, Geometry vectorizedRoads, Polyline route, int activityId) {
        if(vectorizedRoads == null || vectorizedRoads.isEmpty()) {
            return null;
        }

        // Convert route to LineString
        GeometryFactory geometryFactory = new GeometryFactory();
        LineString routeLineString = createLineStringFromPolyline(route, geometryFactory);

        // Buffer route to Polygon
        Polygon routePolygon = createPolygonFromLineString(routeLineString, TOLERANCE);

        // Get country codes
        Set<String> countryCodes = getTouchedCountries(context, routeLineString);

        // Calculate regional distance
        VectorizedDataRecord vectorizedDataRecord = new VectorizedDataRecord(vectorizedRoads, routePolygon, countryCodes);
        return calculateNewRegionalDistance(context, vectorizedDataRecord, activityId);
    }

    /**
     * Gets all relevant roads from the database based on the buffered polygon.
     * @param context Context
     * @param geometryFactory Geometry factory
     * @param routePolygon Buffered polygon of the roads
     * @return Roads as a MultiLineString
     */
    private static MultiLineString getAllRoads(Context context, GeometryFactory geometryFactory, Polygon routePolygon, int ignoredActivity) {
        LocalDatabaseDAO dao = new LocalDatabaseDAO(context);
        List<Geometry> allRoads = dao.getAllVectorizedRoads(ignoredActivity);
        dao.close();

        List<LineString> usableLines = new ArrayList<>();

        for(Geometry g : allRoads) {
            if(routePolygon.intersects(g)) {
                if(g instanceof LineString) {
                    usableLines.add((LineString) g);
                }
                else if(g instanceof MultiLineString) {
                    MultiLineString mls = (MultiLineString) g;
                    for(int i = 0; i < mls.getNumGeometries(); i++) {
                        usableLines.add((LineString) mls.getGeometryN(i));
                    }
                }
                else {
                    throw new IllegalArgumentException("Unexpected geometry: " + g.getGeometryType());
                }
            }
        }

        return geometryFactory.createMultiLineString(usableLines.toArray(new LineString[0]));
    }

    /**
     * Gets all relevant roads from the database based on a list of buffered polygons.
     * @param context Context
     * @param geometryFactory Geometry factory
     * @param routePolygons List of the buffered polygons of the roads
     * @return Roads as a MultiLineString
     */
    private static MultiLineString getAllRoads(Context context, GeometryFactory geometryFactory, List<Polygon> routePolygons) {
        LocalDatabaseDAO dao = new LocalDatabaseDAO(context);
        List<Geometry> allRoads = dao.getAllVectorizedRoads();
        dao.close();

        return geometryFactory.createMultiLineString(
                allRoads.parallelStream().flatMap(g -> {
                    List<LineString> lines = new ArrayList<>();

                    for(Polygon routePolygon : routePolygons) {
                        if(routePolygon.intersects(g)) {
                            if(g instanceof LineString) {
                                lines.add((LineString) g);
                            }
                            else if(g instanceof MultiLineString) {
                                MultiLineString mls = (MultiLineString) g;
                                for(int i = 0; i < mls.getNumGeometries(); i++) {
                                    lines.add((LineString) mls.getGeometryN(i));
                                }
                            }
                            else {
                                throw new IllegalArgumentException("Unexpected geometry: " + g.getGeometryType());
                            }
                            break;
                        }
                    }

                    return lines.stream();
                }).toArray(LineString[]::new));
    }

    /**
     * Gets the list of shapefiles to be opened based on available region divisions.
     * @param context Context
     * @param countryCodes Country codes of the touched countries
     * @return List of shapefile names.
     */
    private static List<String> getSmallestAvailableRegionFilenames(Context context, Set<String> countryCodes) {
        List<String> shapeFiles = new ArrayList<>();

        for(String code : countryCodes) {
            for(String region : regionTypes) {
                String filename = code + "_" + region;

                if(copyShapefileToInternalStorage(context, filename)) {
                    shapeFiles.add(filename);
                    break;
                }
            }
        }

        return shapeFiles;
    }

    /**
     * Calculates the distance inside the smallest available regions.
     * @param context Context
     * @param geometryFactory Geometry factory
     * @param uniqueRoads Unique roads
     * @param shapefiles Shapefiles to open
     * @return List of RegionalEntries holding the distances.
     */
    private static List<RegionalEntry> getDistances(Context context, GeometryFactory geometryFactory, Geometry uniqueRoads, List<String> shapefiles) {
        List<RegionalEntry> regionalEntries = new ArrayList<>();

        uniqueRoads = GeometryFixer.fix(uniqueRoads);

        for(String shpFilename : shapefiles) {
            try {
                // Read shapefile
                List<ShpPolygon> shapes = getShpPolygons(context, shpFilename);

                // Detect necessary regions using route with contains operation
                for(ShpPolygon shp : shapes) {
                    // Convert to Coordinate
                    List<Coordinate> shapeCoords = new ArrayList<>();
                    double[][] shapePoints = shp.getPoints();
                    for(int j = 0; j < shp.getNumberOfPoints(); j++) {
                        shapeCoords.add(new Coordinate(shapePoints[j][0], shapePoints[j][1]));
                    }

                    if(shapeCoords.isEmpty()) {
                        continue;
                    }

                    // Close multipolygons
                    if(!shapeCoords.get(0).equals2D(shapeCoords.get(shapeCoords.size() - 1))) {
                        shapeCoords.add(new Coordinate(shapeCoords.get(0)));
                    }

                    // Create region polygon
                    LinearRing shell = geometryFactory.createLinearRing(shapeCoords.toArray(new Coordinate[0]));
                    Geometry regionPolygon = geometryFactory.createPolygon(shell);

                    // Setup entry data if match is found
                    if(regionPolygon.intersects(uniqueRoads)) {
                        RegionalEntry entry = new RegionalEntry();

                        entry.setContinent(getContinent(shp.getCountryCode()));
                        entry.setCountry(getCountry(shp.getCountryCode()));

                        if(shp.getLargeRegion() != null) {
                            entry.setLargeRegion(shp.getLargeRegion());
                        }
                        if(shp.getSmallRegion() != null) {
                            entry.setSmallRegion(shp.getSmallRegion());
                        }

                        if(!regionPolygon.isValid()) {
                            regionPolygon = GeometryFixer.fix(regionPolygon);
                        }

                        Geometry newRoadsCleaned = GeometryFixer.fix(uniqueRoads);
                        Geometry clippedRoads = OverlayNG.overlay(newRoadsCleaned, regionPolygon, OverlayNG.INTERSECTION);
                        entry.setDistance(getGeodesicLength(clippedRoads));

                        System.out.println("New entry: " + entry);
                        regionalEntries.add(entry);
                    }
                }
            }
            catch(Exception e) {
                throw new RuntimeException(e);
            }
        }

        return regionalEntries;
    }

    /**
     * Gets the shapefile polygons from a shapefile.
     * @param context Context
     * @param shpFilename Shapefile name
     * @return List of polygons
     */
    @NonNull
    private static List<ShpPolygon> getShpPolygons(Context context, String shpFilename) {
        try {
            ShapeFile shapefile = new ShapeFile(context.getFilesDir().getAbsolutePath(), shpFilename);
            shapefile.READ();

            List<ShpPolygon> shapes = new ArrayList<>();
            for(int i = 0; i < shapefile.getSHP_shapeCount(); i++) {
                ShpPolygon poly = shapefile.getSHP_shape(i);
                poly.setCountryCode(shpFilename.substring(0, 2));

                // Get region info
                if(shpFilename.contains("smallregion")) {
                    poly.setSmallRegion(shapefile.getDBF_record(i)[0].trim());
                    poly.setLargeRegion(shapefile.getDBF_record(i)[1].trim());
                }
                else if(shpFilename.contains("largeregion")) {
                    poly.setLargeRegion(shapefile.getDBF_record(i)[0].trim());
                }

                shapes.add(poly);
            }
            return shapes;
        }
        catch(Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Calculates unique roads inside one geometry compared to another
     * @param newRoads Roads to checked for uniqueness
     * @param allRoads Roads to check uniqueness against
     * @param geometryFactory Geometry factory
     * @return Unique roads as a single Geometry
     */
    private static Geometry getUniqueRoads(Geometry newRoads, MultiLineString allRoads, GeometryFactory geometryFactory) {
        List<Geometry> uniqueSegments = new ArrayList<>();

        // Create spatial index for existing roads
        STRtree index = new STRtree();
        for(int i = 0; i < allRoads.getNumGeometries(); i++) {
            LineString existing = (LineString) allRoads.getGeometryN(i);
            index.insert(existing.getEnvelopeInternal(), existing);
        }
        index.build();

        // Check each new road segment
        if(newRoads instanceof MultiLineString) {
            MultiLineString newRoadsMultiLineString = (MultiLineString) newRoads;

            for(int i = 0; i < newRoadsMultiLineString.getNumGeometries(); i++) {
                LineString newSegment = (LineString) newRoadsMultiLineString.getGeometryN(i);

                // Find nearby roads
                List<LineString> nearbyRoads = index.query(newSegment.getEnvelopeInternal());

                // Check for uniqueness
                boolean isUnique = true;
                for(LineString road : nearbyRoads) {
                    if(DistanceOp.distance(newSegment, road) <= TOLERANCE) {
                        isUnique = false;
                        break;
                    }
                }

                if(isUnique) {
                    uniqueSegments.add(newSegment);
                }
            }
        }

        return geometryFactory.buildGeometry(uniqueSegments);
    }

    /**
     * Gets the geodesic length of all LineStrings within a Geometry.
     * @param geometry Geometry
     * @return Length
     */
    private static double getGeodesicLength(Geometry geometry) {
        double totalLength = 0.0;

        for(int i = 0; i < geometry.getNumGeometries(); i++) {
            Geometry geo = geometry.getGeometryN(i);

            if(geo instanceof LineString) {
                Coordinate[] coords = geo.getCoordinates();

                for(int j = 1; j < coords.length; j++) {
                    totalLength += haversine(
                            coords[j - 1].y, coords[j - 1].x,
                            coords[j].y, coords[j].x
                    );
                }
            }
        }

        return totalLength;
    }

    /** Calculates the spherical distance between two points
     * @param lat1 Latitude of the first point
     * @param lon1 Longitude of the first point
     * @param lat2 Latitude of the second point
     * @param lon2 Longitude of the second point
     * @return Distance
     */
    private static double haversine(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);

        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(dLon / 2) * Math.sin(dLon / 2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c;
    }
}
