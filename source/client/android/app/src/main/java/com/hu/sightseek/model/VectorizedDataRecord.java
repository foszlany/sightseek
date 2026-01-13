package com.hu.sightseek.model;

import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.Polygon;
import org.osmdroid.views.overlay.Polyline;

import java.io.Serializable;
import java.util.List;
import java.util.Set;

/** The class VectorizedDataRecord is used to pass the results of the vectorization. */
public class VectorizedDataRecord implements Serializable {
    /** Vectorized data as polylines */
    private List<Polyline> vectorizedDataPolylines;
    /** Vectorized data as geometry */
    private Geometry vectorizedDataGeometry;
    /** Buffered polygon of the roads */
    private Polygon routePolygon;
    /** Set of country codes*/
    private Set<String> countryCodes;

    /**
     * Constructor for when polylines are not needed.
     * @param vectorizedDataGeometry Vectorized data as geometry
     * @param routePolygon Buffered polygon of the roads
     * @param countryCodes Set of country codes
     */
    public VectorizedDataRecord(Geometry vectorizedDataGeometry, Polygon routePolygon, Set<String> countryCodes) {
        this.vectorizedDataGeometry = vectorizedDataGeometry;
        this.routePolygon = routePolygon;
        this.countryCodes = countryCodes;
    }

    /**
     * Constructor for when polylines are needed.
     * @param vectorizedDataGeometry Vectorized data as geometry
     * @param routePolygon Buffered polygon of the roads
     * @param countryCodes Set of country codes
     * @param vectorizedDataPolylines Vectorized data as a list of polylines
     */
    public VectorizedDataRecord(Geometry vectorizedDataGeometry, Polygon routePolygon, Set<String> countryCodes, List<Polyline> vectorizedDataPolylines) {
        this.vectorizedDataGeometry = vectorizedDataGeometry;
        this.routePolygon = routePolygon;
        this.countryCodes = countryCodes;
        this.vectorizedDataPolylines = vectorizedDataPolylines;
    }

    /** Default constructor */
    public VectorizedDataRecord() {}

    public List<Polyline> getVectorizedDataPolylines() {
        return vectorizedDataPolylines;
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