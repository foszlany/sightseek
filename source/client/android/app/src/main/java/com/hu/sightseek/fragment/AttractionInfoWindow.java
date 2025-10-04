package com.hu.sightseek.fragment;

import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.hu.sightseek.R;
import com.hu.sightseek.db.LocalDatabaseDAO;
import com.hu.sightseek.enums.SavedAttractionStatus;
import com.hu.sightseek.model.AttractionGeoPoint;

import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.infowindow.InfoWindow;

public class AttractionInfoWindow extends InfoWindow {
    public AttractionInfoWindow(int layoutResId, MapView mapView) {
        super(layoutResId, mapView);
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
            LocalDatabaseDAO dao2 = new LocalDatabaseDAO(view.getContext());
            dao2.updateAttractionStatus(attractionPoint.getId(), SavedAttractionStatus.VISITED.getIndex());
            dao2.close();

            close();
        });

        removeButton.setOnClickListener(v -> {
            LocalDatabaseDAO dao2 = new LocalDatabaseDAO(view.getContext());
            dao2.deleteAttraction(attractionPoint.getId());
            dao2.close();

            close();
        });
    }

    @Override
    public void onClose() {}
}