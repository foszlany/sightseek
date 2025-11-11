package com.hu.sightseek.helpers;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.MultiLineString;
import org.locationtech.jts.io.ParseException;
import org.locationtech.jts.io.WKBReader;
import org.locationtech.jts.io.WKBWriter;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.overlay.Polyline;

import java.util.ArrayList;
import java.util.List;

public final class WKConverter {
    private WKConverter() {}

    public static byte[] convertGeometryToWKB(Geometry geometry) {
        WKBWriter writer = new WKBWriter();
        return writer.write(geometry);
    }

    public static Geometry convertWKBToGeometry(byte[] wkb) throws ParseException {
        WKBReader reader = new WKBReader();
        return reader.read(wkb);
    }

    public static List<Polyline> convertWKBToPolylines(byte[] wkb) throws ParseException {
        Geometry geometry = convertWKBToGeometry(wkb);
        return convertLineGeometryToPolyline(geometry);
    }

    public static List<Polyline> convertLineGeometryToPolyline(Geometry vectorizedDataGeometry) {
        List<Polyline> vectorizedDataPolylines = new ArrayList<>();

        if(vectorizedDataGeometry instanceof LineString) {
            Polyline polyline = convertLineStringToPolyline((LineString) vectorizedDataGeometry);
            vectorizedDataPolylines.add(polyline);
        }
        else if(vectorizedDataGeometry instanceof MultiLineString) {
            ArrayList<Polyline> polylines = convertMultiLineStringToPolyline((MultiLineString) vectorizedDataGeometry);
            vectorizedDataPolylines.addAll(polylines);
        }

        return vectorizedDataPolylines;
    }

    public static Polyline convertLineStringToPolyline(LineString lineString) {
        Coordinate[] coords = lineString.getCoordinates();
        ArrayList<GeoPoint> geoPoints = new ArrayList<>();

        for(Coordinate coord : coords) {
            GeoPoint geo = new GeoPoint(coord.y, coord.x);
            geoPoints.add(geo);
        }

        Polyline polyline = new Polyline();
        polyline.setPoints(geoPoints);

        return polyline;
    }

    public static ArrayList<Polyline> convertMultiLineStringToPolyline(MultiLineString multiLineString) {
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
}