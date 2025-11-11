package com.hu.sightseek.model;

import org.locationtech.jts.geom.Geometry;
import org.osmdroid.views.overlay.Polyline;

import java.util.List;
import java.util.Set;

public class VectorizedDataRecord {
    List<Polyline> vectorizedDataPolylines;
    Geometry vectorizedDataGeometry;
    Set<String> countryCodes;

    public VectorizedDataRecord(List<Polyline> vectorizedDataPolylines, Geometry vectorizedDataGeometry, Set<String> countryCodes) {
        this.vectorizedDataPolylines = vectorizedDataPolylines;
        this.vectorizedDataGeometry = vectorizedDataGeometry;
        this.countryCodes = countryCodes;
    }

    public VectorizedDataRecord() {}

    public List<Polyline> getVectorizedDataPolylines() {
        return vectorizedDataPolylines;
    }

    public Set<String> getCountryCodes() {
        return countryCodes;
    }

    public Geometry getVectorizedDataGeometry() {
        return vectorizedDataGeometry;
    }
}
