package com.hu.sightseek.utils;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;

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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import diewald_shapeFile.files.shp.shapeTypes.ShpPolyLine;
import diewald_shapeFile.files.shp.shapeTypes.ShpPolygon;
import diewald_shapeFile.shapeFile.ShapeFile;

public final class SightseekVectorizationUtils {
    private SightseekVectorizationUtils() {}

    public static ArrayList<Polyline> vectorize(Activity activity, Polyline route) {
        ArrayList<Polyline> vectorizedPolylines = new ArrayList<>();
        GeometryFactory geometryFactory = new GeometryFactory();

        System.out.println("#### Creating vectorized data ####");

        // LineString
        LineString lineString = createLineStringFromPolyline(route, geometryFactory);
        System.out.println("Polyline was converted to LineString");

        // Calculate countries
        Set<String> countryCodes = getTouchedCountries(lineString, activity);
        System.out.println("Countries have been detected");

        // Route polygon
        Polygon routePolygon = createPolygonFromLineString(lineString, 0.0002);
        System.out.println("Route polygon has been created");

        // Filtered roads
        MultiLineString roadPolylines = getRoadPolylines(activity, geometryFactory, routePolygon.getEnvelopeInternal());
        System.out.println("Road polylines have been created");

        // Calculate intersection
        Geometry vectorizedData = roadPolylines.intersection(routePolygon);

        System.out.println("Intersection calculation done");

        // Create polyline(s)
        if(vectorizedData instanceof LineString) {
            Polyline polyline = convertLineStringToPolyline((LineString) vectorizedData);

            vectorizedPolylines.add(polyline);
        }
        else if(vectorizedData instanceof MultiLineString) {
            ArrayList<Polyline> polylines = convertMultiLineStringToPolyline((MultiLineString) vectorizedData);
            vectorizedPolylines.addAll(polylines);
        }
        else { // TODO ?
            System.out.println(vectorizedData.getClass());
        }

        System.out.println("#### Calculation done ####");

        return vectorizedPolylines;
    }

    private static Set<String> getTouchedCountries(LineString route, Activity activity) {
        Set<String> touchedCountries = new HashSet<>();

        copyShapefileToInternalStorage(activity, "countries");

        try {
            ShapeFile countryShapefile = new ShapeFile(activity.getFilesDir().getAbsolutePath(), "countries");
            countryShapefile.READ();

            GeometryFactory geometryFactory = new GeometryFactory();

            // Check for each country
            for(int i = 0; i < countryShapefile.getSHP_shapeCount(); i++) {
                ShpPolygon shape = countryShapefile.getSHP_shape(i);
                String isoCode = countryShapefile.getDBF_record(i)[0].trim(); // TODO: SWAP COL WITH NAME SOMEHOW?

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

    private static MultiLineString getRoadPolylines(Activity activity, GeometryFactory geometryFactory, Envelope filterEnvelope) {
        copyShapefileToInternalStorage(activity, "hu_roads");

        try {
            ShapeFile roadsShapeFile = new ShapeFile(activity.getFilesDir().getAbsolutePath(), "hu_roads");
            roadsShapeFile.READ();

            ArrayList<LineString> lineStringList = new ArrayList<>();

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

            return new MultiLineString(lineStringList.toArray(new LineString[0]), geometryFactory);
        }
        catch(Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static Polyline convertLineStringToPolyline(LineString lineString) {
        Coordinate[] coords = lineString.getCoordinates();
        ArrayList<GeoPoint> geoPoints = new ArrayList<>();

        for(Coordinate coord : coords) {
            GeoPoint geo = new GeoPoint(coord.x, coord.y);
            geoPoints.add(geo);
        }

        // TODO: TEMPORARY
        Polyline polyline = new Polyline();
        polyline.setPoints(geoPoints);
        polyline.setColor(Color.RED);
        polyline.setWidth(4.0f);

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

            // TODO: TEMPORARY
            Polyline polyline = new Polyline();
            polyline.setPoints(geoPoints);
            polyline.setColor(Color.RED);
            polyline.setWidth(4.0f);

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