package com.hu.sightseek.fragment;

import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.hu.sightseek.R;
import com.hu.sightseek.db.LocalDatabaseDAO;
import com.hu.sightseek.enums.SavedAttractionStatus;
import com.hu.sightseek.model.AttractionGeoPoint;

import org.osmdroid.api.IGeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.infowindow.InfoWindow;
import org.osmdroid.views.overlay.simplefastpoint.SimpleFastPointOverlay;
import org.osmdroid.views.overlay.simplefastpoint.SimpleFastPointOverlayOptions;
import org.osmdroid.views.overlay.simplefastpoint.SimplePointTheme;

import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class AttractionInfoWindow extends InfoWindow {
    SimpleFastPointOverlayOptions layoutStyle;
    List<IGeoPoint> points;
    SimpleFastPointOverlay attractionsOverlay;

    public AttractionInfoWindow(int layoutResId, MapView mapView, SimpleFastPointOverlayOptions layoutStyle, List<IGeoPoint> points, SimpleFastPointOverlay attractionsOverlay) {
        super(layoutResId, mapView);

        this.layoutStyle = layoutStyle;
        this.points = points;
        this.attractionsOverlay = attractionsOverlay;
    }

    @Override
    public void onOpen(Object obj) {
        View view = mView;

        TextView placeTextView = view.findViewById(R.id.attractionpopup_placename);
        Button visitedButton = view.findViewById(R.id.attractionpopup_visitedbtn);
        Button removeButton = view.findViewById(R.id.attractionpopup_removebtn);

        AttractionGeoPoint attractionPoint = (AttractionGeoPoint) obj;
        placeTextView.setText(attractionPoint.getLabel());

        visitedButton.setOnClickListener(v -> {
            Executors.newSingleThreadExecutor().execute(() -> {
                LocalDatabaseDAO dao2 = new LocalDatabaseDAO(view.getContext());
                dao2.updateAttractionStatus(attractionPoint.getId(), SavedAttractionStatus.VISITED.getIndex());
                dao2.close();
            });

            Executors.newSingleThreadExecutor().execute(() -> {
                points.remove(attractionPoint);
                attractionsOverlay = new SimpleFastPointOverlay(new SimplePointTheme(points, true), layoutStyle);
                mMapView.invalidate();
            });

            close();
        });

        removeButton.setOnClickListener(v -> {
            Executors.newSingleThreadExecutor().execute(() -> {
                LocalDatabaseDAO dao2 = new LocalDatabaseDAO(view.getContext());
                dao2.deleteAttraction(attractionPoint.getId());
                dao2.close();
            });

            Executors.newSingleThreadExecutor().execute(() -> {
                points.remove(attractionPoint);
                attractionsOverlay = new SimpleFastPointOverlay(new SimplePointTheme(points, true), layoutStyle);
                mMapView.invalidate();
            });

            close();
        });
    }

    @Override
    public void onClose() {}
}