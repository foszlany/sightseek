package com.hu.sightseek.utils;

import static com.hu.sightseek.helpers.CountryInfo.getContinent;
import static com.hu.sightseek.helpers.CountryInfo.getCountry;
import static com.hu.sightseek.helpers.RegionalDistanceAggregator.aggregateDistances;
import static com.hu.sightseek.utils.SightseekFirebaseUtils.updateRegionalLeaderboard;
import static com.hu.sightseek.utils.SightseekVectorizationUtils.TOLERANCE;
import static com.hu.sightseek.utils.SightseekVectorizationUtils.copyShapefileToInternalStorage;

import android.app.Activity;

import androidx.annotation.NonNull;

import com.hu.sightseek.db.LocalDatabaseDAO;
import com.hu.sightseek.model.RegionalEntry;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.LinearRing;
import org.locationtech.jts.geom.MultiLineString;
import org.locationtech.jts.geom.util.GeometryFixer;
import org.locationtech.jts.operation.overlayng.OverlayNG;
import org.locationtech.jts.operation.overlayng.OverlayNGRobust;
import org.osmdroid.util.GeoPoint;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

import diewald_shapeFile.files.shp.shapeTypes.ShpPolygon;
import diewald_shapeFile.shapeFile.ShapeFile;

public final class SightseekRegionalLeaderboardUtils {
    private static final ArrayList<String> regionTypes = new ArrayList<>(Arrays.asList("smallregion", "largeregion", "country"));

    private SightseekRegionalLeaderboardUtils() {}

    public static void calculateRegionalDistance(Activity activity, Geometry newRoads, Set<String> countryCodes) {
        GeometryFactory geometryFactory = new GeometryFactory();

        // Load all vectors from activities
        MultiLineString allRoads = getAllRoads(activity, geometryFactory, newRoads);

        // Detect which shp files exist, select smallest (smallregion -> largeregion -> country)
        List<String> shapefiles = getSmallestAvailableRegionFilenames(activity, countryCodes);

        // Reduce roads for improved difference calculation
        /*
        PrecisionModel precisionModel = new PrecisionModel(1e6);
        Geometry reducedNewRoads = GeometryPrecisionReducer.reduce(newRoads, precisionModel);
        Geometry reducedAllRoads = GeometryPrecisionReducer.reduce(allRoads, precisionModel); */

        // Get unique roads
        Geometry uniqueRoads = OverlayNG.overlay(newRoads, allRoads, OverlayNG.DIFFERENCE);

        // Calculate the distance per region along with the containing geometries
        List<RegionalEntry> entries = getDistances(activity, geometryFactory, uniqueRoads, shapefiles);

        // Convert distances to map
        Map<String, Double> distanceMap = aggregateDistances(entries);

        // Update leaderboard
        updateRegionalLeaderboard(distanceMap);
    }

    private static MultiLineString getAllRoads(Activity activity, GeometryFactory geometryFactory, Geometry newRoads) {
        LocalDatabaseDAO dao = new LocalDatabaseDAO(activity);
        ArrayList<String> allRoads = dao.getAllVectorizedRoads();
        dao.close();

        ArrayList<LineString> lineStrings = new ArrayList<>();

        Envelope newRoadsEnvelope = new Envelope(newRoads.getEnvelopeInternal());
        newRoadsEnvelope.expandBy(TOLERANCE);

        for(String roadVectors : allRoads) {
            String[] polylines = roadVectors.split(";");

            for(String segment : polylines) {
                List<GeoPoint> coords = SightseekSpatialUtils.decode(segment);
                if(coords.size() < 2) {
                    continue;
                }

                Coordinate[] coordinates = new Coordinate[coords.size()];
                for(int i = 0; i < coords.size(); i++) {
                    GeoPoint point = coords.get(i);
                    double lat = point.getLatitude();
                    double lon = point.getLongitude();

                    coordinates[i] = new Coordinate(lon, lat);
                }

                LineString line = geometryFactory.createLineString(coordinates);

                if(newRoadsEnvelope.intersects(line.getEnvelopeInternal())) {
                    lineStrings.add(line);
                }
            }
        }

        return new MultiLineString(lineStrings.toArray(new LineString[0]), geometryFactory);
    }


    private static ArrayList<String> getSmallestAvailableRegionFilenames(Activity activity, Set<String> countryCodes) {
        ArrayList<String> shapeFiles = new ArrayList<>();

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

    private static ArrayList<RegionalEntry> getDistances(Activity activity, GeometryFactory geometryFactory, Geometry uniqueRoads, List<String> shapefiles) {
        ArrayList<RegionalEntry> regionalEntries = new ArrayList<>();

        for(String shpFilename : shapefiles) {
            try {
                // Read shapefile
                ArrayList<ShpPolygon> shapes = getShpPolygons(activity, shpFilename);

                // Detect necessary regions using route with contains operation
                for(ShpPolygon shp : shapes) {
                    // Convert to Coordinate
                    ArrayList<Coordinate> shapeCoords = new ArrayList<>();
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

                        if(!uniqueRoads.isValid()) {
                            uniqueRoads = GeometryFixer.fix(uniqueRoads);
                        }
                        if(!regionPolygon.isValid()) {
                            regionPolygon = GeometryFixer.fix(regionPolygon);
                        }

                        Geometry clippedRoads = OverlayNGRobust.overlay(uniqueRoads, regionPolygon, OverlayNG.INTERSECTION);
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

    @NonNull
    private static ArrayList<ShpPolygon> getShpPolygons(Activity activity, String shpFilename) {
        try {
            ShapeFile shapefile = new ShapeFile(activity.getFilesDir().getAbsolutePath(), shpFilename);
            shapefile.READ();

            ArrayList<ShpPolygon> shapes = new ArrayList<>();
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

    private static double haversine(double lat1, double lon1, double lat2, double lon2) {
        final double R = 6371;

        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);

        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(dLon / 2) * Math.sin(dLon / 2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c;
    }
}
