package com.hu.sightseek.utils;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.MultiLineString;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.operation.buffer.BufferOp;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Polyline;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

import diewald_shapeFile.files.shp.shapeTypes.ShpPolyLine;
import diewald_shapeFile.shapeFile.ShapeFile;

public final class SightseekSpatialUtils {
    private SightseekSpatialUtils() {}

    public static ArrayList<Polyline> vectorize(Activity activity, Polyline route, MapView mapView) {
        ArrayList<Polyline> vectorizedPolylines = new ArrayList<>();
        GeometryFactory geometryFactory = new GeometryFactory();

        System.out.println("\nCreating vectorized data...");

        // TODO: DETECT COUNTRIES


        // Route polygon
        Polygon routePolygon = createPolygonFromPolyline(route, geometryFactory, 0.0002);
        System.out.println("Route polygon has been created.");

        // Filtered roads
        MultiLineString roadPolylines = getRoadPolylines(activity, geometryFactory, routePolygon.getEnvelopeInternal());
        System.out.println("Road polylines have been created.");

        // Calculate intersection
        Geometry vectorizedData = roadPolylines.intersection(routePolygon);

        System.out.println("Intersection calculation done.");

        // Create polylines
        if(vectorizedData instanceof LineString) {
            Polyline polyline = convertLineStringToPolyline((LineString) vectorizedData, mapView);
            mapView.getOverlays().add(polyline);
            mapView.invalidate();

            vectorizedPolylines.add(polyline);
        }
        else if(vectorizedData instanceof MultiLineString) {
            ArrayList<Polyline> polylines = convertMultiLineStringToPolyline((MultiLineString) vectorizedData, mapView);
            for(Polyline p : polylines) {
                mapView.getOverlays().add(p);
            }
            mapView.invalidate();

            vectorizedPolylines.addAll(polylines);
        }
        else { // TODO ?
            System.out.println(vectorizedData.getClass());
        }

        System.out.println("Calculation done.\n");

        return vectorizedPolylines;
    }

    private static Polygon createPolygonFromPolyline(Polyline route, GeometryFactory geometryFactory, double tolerance) {
        List<GeoPoint> points = route.getActualPoints();
        Coordinate[] coordinates = new Coordinate[points.size()];

        for(int i = 0; i < points.size(); i++) {
            coordinates[i] = new Coordinate(points.get(i).getLongitude(), points.get(i).getLatitude());
        }

        LineString line = geometryFactory.createLineString(coordinates);

        Geometry buffered = BufferOp.bufferOp(line, tolerance);
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
        catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static Polyline convertLineStringToPolyline(LineString lineString, MapView mapView) {
        Coordinate[] coords = lineString.getCoordinates();
        ArrayList<GeoPoint> geoPoints = new ArrayList<>();

        for(Coordinate coord : coords) {
            GeoPoint geo = new GeoPoint(coord.x, coord.y);
            geoPoints.add(geo);
        }

        // TODO: TEMPORARY
        Polyline polyline = new Polyline(mapView);
        polyline.setPoints(geoPoints);
        polyline.setColor(Color.RED);
        polyline.setWidth(4.0f);

        return polyline;
    }

    private static ArrayList<Polyline> convertMultiLineStringToPolyline(MultiLineString multiLineString, MapView mapView) {
        ArrayList<Polyline> polylines = new ArrayList<>();

        for(int i = 0; i < multiLineString.getNumGeometries(); i++) {
            LineString line = (LineString) multiLineString.getGeometryN(i);
            Coordinate[] coords = line.getCoordinates();

            ArrayList<GeoPoint> geoPoints = new ArrayList<>();
            for (Coordinate coord : coords) {
                double lat = coord.y;
                double lon = coord.x;
                geoPoints.add(new GeoPoint(lat, lon));
            }

            // TODO: TEMPORARY
            Polyline polyline = new Polyline(mapView);
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