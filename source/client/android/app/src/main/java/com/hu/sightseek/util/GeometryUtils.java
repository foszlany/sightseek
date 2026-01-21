package com.hu.sightseek.util;

import static com.hu.sightseek.util.GenericUtils.copyShapefileToInternalStorage;

import android.content.Context;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.LinearRing;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.operation.buffer.BufferOp;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.overlay.Polyline;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import diewald_shapeFile.files.shp.shapeTypes.ShpPolygon;
import diewald_shapeFile.shapeFile.ShapeFile;

public final class GeometryUtils {
    /** Value used to create an extra buffer around polylines */
    public static final double TOLERANCE = 0.0002;

    /** Default sublist size for partitioning */
    public static final int DEFAULT_PARTITION_SIZE = 200;

    /** Private constructor */
    private GeometryUtils() {}

    /**
     * Converts a Polyline to a LineString
     * @param route Route as a Polyline
     * @param geometryFactory Geometry factory
     * @return LineString
     */
    public static LineString createLineStringFromPolyline(Polyline route, GeometryFactory geometryFactory) {
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
     * @param tolerance Value to buffer the LineString with
     * @return Polygon
     */
    public static Polygon createPolygonFromLineString(LineString route, double tolerance) {
        Geometry buffered = BufferOp.bufferOp(route, tolerance);

        if(buffered instanceof Polygon) {
            return (Polygon) buffered;
        }
        else {
            throw new IllegalStateException("Geometry is not a polygon.");
        }
    }

    /**
     * Gets the countries touched by a polyline (a polyline touches a country when it has at least one point inside it)
     * @param context Context
     * @param route Route as a LineString
     * @return Set of countries a polyline touches.
     */
    public static Set<String> getTouchedCountries(Context context, LineString route) {
        return getTouchedCountries(context, route, null);
    }

    /**
     * Gets the countries touched by a polyline (a polyline touches a country when it has at least one point inside it)
     * @param context Context
     * @param route Route as a LineString
     * @param countryShapefile Countries shapefile. If null, it will be opened.
     * @return Set of countries a polyline touches.
     */
    public static Set<String> getTouchedCountries(Context context, LineString route, ShapeFile countryShapefile) {
        Set<String> touchedCountries = new HashSet<>();

        try {
            if(countryShapefile == null) {
                copyShapefileToInternalStorage(context, "countries");
                countryShapefile = new ShapeFile(context.getFilesDir().getAbsolutePath(), "countries");
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
}
