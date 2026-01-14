package com.hu.sightseek.helper;

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

/** Helper class for converting WKT/WKB (Well-known text representation of geometry) */
public final class WKConverter {
    /** Private constructor */
    private WKConverter() {}

    /**
     * Converts Geometry to WKB
     * @param geometry Geometry
     * @return WKB
     */
    public static byte[] convertGeometryToWKB(Geometry geometry) {
        WKBWriter writer = new WKBWriter();
        return writer.write(geometry);
    }

    /**
     * Converts WKB to Geometry
     * @param wkb WKB
     * @return Geometry
     * @throws ParseException Thrown when wkb is unreadable
     */
    public static Geometry convertWKBToGeometry(byte[] wkb) throws ParseException {
        WKBReader reader = new WKBReader();
        return reader.read(wkb);
    }

    /**
     * Converts WKB to a list of Polylines
     * @param wkb WKB
     * @return List of Polylines
     * @throws ParseException Thrown when wkb is unreadable
     */
    public static List<Polyline> convertWKBToPolylines(byte[] wkb) throws ParseException {
        Geometry geometry = convertWKBToGeometry(wkb);
        return convertLineGeometryToPolyline(geometry);
    }

    /**
     * Converts LineString and MultiLineStrings into a list of Polylines, other geometries are ignored
     * @param lines Geometry
     * @return List of Polylines
     */
    public static List<Polyline> convertLineGeometryToPolyline(Geometry lines) {
        List<Polyline> convertedPolylines = new ArrayList<>();

        if(lines instanceof LineString) {
            Polyline polyline = convertLineStringToPolyline((LineString) lines);
            convertedPolylines.add(polyline);
        }
        else if(lines instanceof MultiLineString) {
            ArrayList<Polyline> polylines = convertMultiLineStringToPolyline((MultiLineString) lines);
            convertedPolylines.addAll(polylines);
        }

        return convertedPolylines;
    }

    /**
     * Converts a LineString into a Polyline
     * @param lineString LineString
     * @return Polyline
     */
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

    /**
     * Converts a MultiLineString into a list of Polylines
     * @param multiLineString MultiLineString
     * @return Polyline
     */
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