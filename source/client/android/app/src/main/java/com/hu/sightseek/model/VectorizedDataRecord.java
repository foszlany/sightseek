package com.hu.sightseek.model;

import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.Polygon;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.overlay.Polyline;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class VectorizedDataRecord implements Serializable {
    private static final long serialVersionUID = 1L;

    private List<List<GeoPoint>> vectorizedPoints;
    private Geometry vectorizedDataGeometry;
    private Polygon routePolygon;
    private Set<String> countryCodes;

    public VectorizedDataRecord(List<Polyline> vectorizedDataPolylines, Geometry vectorizedDataGeometry, Polygon routePolygon, Set<String> countryCodes) {
        this.vectorizedPoints = new ArrayList<>();

        if(vectorizedDataPolylines != null) {
            for(Polyline polyline : vectorizedDataPolylines) {
                this.vectorizedPoints.add(new ArrayList<>(polyline.getPoints()));
            }
        }

        this.vectorizedDataGeometry = vectorizedDataGeometry;
        this.routePolygon = routePolygon;
        this.countryCodes = countryCodes;
    }

    public VectorizedDataRecord() {}

    public List<Polyline> getVectorizedDataPolylines() {
        List<Polyline> polylines = new ArrayList<>();
        if(vectorizedPoints != null) {
            for(List<GeoPoint> points : vectorizedPoints) {
                Polyline polyline = new Polyline();
                polyline.setPoints(points);
                polylines.add(polyline);
            }
        }
        return polylines;
    }

    public Set<String> getCountryCodes() {
        return countryCodes;
    }

    public Polygon getRoutePolygon() {
        return routePolygon;
    }

    public Geometry getVectorizedDataGeometry() {
        return vectorizedDataGeometry;
    }
}