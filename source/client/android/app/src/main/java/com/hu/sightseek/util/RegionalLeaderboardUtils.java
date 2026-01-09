package com.hu.sightseek.util;

import static com.hu.sightseek.helper.CountryInfo.getContinent;
import static com.hu.sightseek.helper.CountryInfo.getCountry;
import static com.hu.sightseek.helper.RegionalDistanceAggregator.aggregateDistances;
import static com.hu.sightseek.util.FirebaseUtils.updateRegionalLeaderboard;
import static com.hu.sightseek.util.VectorizationUtils.copyShapefileToInternalStorage;

import android.app.Activity;

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
import org.locationtech.jts.geom.util.GeometryFixer;
import org.locationtech.jts.operation.overlayng.OverlayNG;
import org.locationtech.jts.operation.union.UnaryUnionOp;

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
    /** Precision for road comparision */
    private static final double ROAD_PRECISION = 0.00004;
    /** Radius of the Earth in kilometers */
    private static final double R = 6371;

    /** Private constructor */
    private RegionalLeaderboardUtils() {}

    /**
     * Batch version of calculateRegionalDistance()
     * @param activity Activity
     * @param vectorizedDataRecords Data records of the vectorized activities
     * @param countryCodes Country codes of the touched countries
     */
    public static void batchCalculateRegionalDistance(Activity activity, List<VectorizedDataRecord> vectorizedDataRecords, Set<String> countryCodes) {
        if(vectorizedDataRecords == null || vectorizedDataRecords.isEmpty()) {
            return;
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
        MultiLineString allRoads = getAllRoads(activity, geometryFactory, routePolygons);

        // Detect which shp files exist, select smallest (smallregion -> largeregion -> country)
        List<String> shapefiles = getSmallestAvailableRegionFilenames(activity, countryCodes);

        // Get unique roads
        Geometry uniqueRoads = OverlayNG.overlay(mergedNewRoads, allRoads.buffer(ROAD_PRECISION), OverlayNG.DIFFERENCE);

        // Calculate the distance per region along with the containing geometries
        List<RegionalEntry> entries = getDistances(activity, geometryFactory, uniqueRoads, shapefiles);

        // Convert distances to map
        Map<String, Double> distanceMap = aggregateDistances(entries);

        // Update leaderboard
        if(!distanceMap.isEmpty()) {
            updateRegionalLeaderboard(distanceMap);
        }
    }

    /**
     * Calculates unique distances per region and uploads it to the database.
     * @param activity Activity
     * @param newRoads New roads
     * @param routePolygon Buffered polygon of the roads
     * @param countryCodes Country codes of the touched countries
     */
    public static void calculateRegionalDistance(Activity activity, Geometry newRoads, Polygon routePolygon, Set<String> countryCodes) {
        if(newRoads == null || newRoads.isEmpty()) {
            return;
        }

        GeometryFactory geometryFactory = new GeometryFactory();

        // Load all vectors from activities
        MultiLineString allRoads = getAllRoads(activity, geometryFactory, routePolygon);

        // Detect which shp files exist, select smallest (smallregion -> largeregion -> country)
        List<String> shapefiles = getSmallestAvailableRegionFilenames(activity, countryCodes);

        Polygon bufferedAllRoads = (Polygon) allRoads.buffer(ROAD_PRECISION);

        // Get unique roads
        Geometry uniqueRoads = OverlayNG.overlay(newRoads, bufferedAllRoads, OverlayNG.DIFFERENCE);

        // Calculate the distance per region along with the containing geometries
        List<RegionalEntry> entries = getDistances(activity, geometryFactory, uniqueRoads, shapefiles);

        // Convert distances to map
        Map<String, Double> distanceMap = aggregateDistances(entries);

        // Update leaderboard
        if(!distanceMap.isEmpty()) {
            updateRegionalLeaderboard(distanceMap);
        }
    }

    /**
     * Gets all relevant roads from the database based on the buffered polygon.
     * @param activity Activity
     * @param geometryFactory Geometry factory
     * @param routePolygon Buffered polygon of the roads
     * @return Roads as a MultiLineString
     */
    private static MultiLineString getAllRoads(Activity activity, GeometryFactory geometryFactory, Polygon routePolygon) {
        LocalDatabaseDAO dao = new LocalDatabaseDAO(activity);
        List<Geometry> allRoads = dao.getAllVectorizedRoads();
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
     * @param activity Activity
     * @param geometryFactory Geometry factory
     * @param routePolygons List of the buffered polygons of the roads
     * @return Roads as a MultiLineString
     */
    private static MultiLineString getAllRoads(Activity activity, GeometryFactory geometryFactory, List<Polygon> routePolygons) {
        LocalDatabaseDAO dao = new LocalDatabaseDAO(activity);
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
     * @param activity Activity
     * @param countryCodes Country codes of the touched countries
     * @return List of shapefile names.
     */
    private static List<String> getSmallestAvailableRegionFilenames(Activity activity, Set<String> countryCodes) {
        List<String> shapeFiles = new ArrayList<>();

        for(String code : countryCodes) {
            for(String region : regionTypes) {
                String filename = code + "_" + region;

                if(copyShapefileToInternalStorage(activity, filename)) {
                    shapeFiles.add(filename);
                    break;
                }
            }
        }

        return shapeFiles;
    }

    /**
     * Calculates the distance inside the smallest available regions.
     * @param activity Activity
     * @param geometryFactory Geometry factory
     * @param uniqueRoads Unique roads
     * @param shapefiles Shapefiles to open
     * @return List of RegionalEntries holding the distances.
     */
    private static List<RegionalEntry> getDistances(Activity activity, GeometryFactory geometryFactory, Geometry uniqueRoads, List<String> shapefiles) {
        List<RegionalEntry> regionalEntries = new ArrayList<>();

        uniqueRoads = GeometryFixer.fix(uniqueRoads);

        for(String shpFilename : shapefiles) {
            try {
                // Read shapefile
                List<ShpPolygon> shapes = getShpPolygons(activity, shpFilename);

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
     * @param activity Activity
     * @param shpFilename Shapefile name
     * @return List of polygons
     */
    @NonNull
    private static List<ShpPolygon> getShpPolygons(Activity activity, String shpFilename) {
        try {
            ShapeFile shapefile = new ShapeFile(activity.getFilesDir().getAbsolutePath(), shpFilename);
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
