package com.hu.sightseek.provider;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Paint;
import android.widget.ImageButton;

import com.hu.sightseek.R;
import com.hu.sightseek.db.LocalDatabaseDAO;
import com.hu.sightseek.fragment.IdeaInfoWindow;
import com.hu.sightseek.model.Idea;
import com.hu.sightseek.model.IdeaGeoPoint;

import org.osmdroid.api.IGeoPoint;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.infowindow.InfoWindow;
import org.osmdroid.views.overlay.simplefastpoint.SimpleFastPointOverlay;
import org.osmdroid.views.overlay.simplefastpoint.SimpleFastPointOverlayOptions;
import org.osmdroid.views.overlay.simplefastpoint.SimplePointTheme;

import java.util.ArrayList;
import java.util.List;

public class IdeaOverlayProvider {
    private IdeaOverlayProvider() {}

    public static SimpleFastPointOverlay getIdeasOverlay(Context ctx, ImageButton ideaButton, boolean areIdeasOn, MapView mapView) {
        LocalDatabaseDAO dao = new LocalDatabaseDAO(ctx);
        ArrayList<Idea> ideas = dao.getSavedIdeas();
        dao.close();

        List<IGeoPoint> points = new ArrayList<>();
        for(Idea a : ideas) {
            points.add(new IdeaGeoPoint(a.getLatitude(), a.getLongitude(), a.getName(), a.getId()));
        }

        SimpleFastPointOverlayOptions layoutStyle = SimpleFastPointOverlayOptions
                .getDefaultStyle()
                .setAlgorithm(points.size() < 8000 ? SimpleFastPointOverlayOptions.RenderingAlgorithm.MEDIUM_OPTIMIZATION : SimpleFastPointOverlayOptions.RenderingAlgorithm.MAXIMUM_OPTIMIZATION)
                .setRadius(8)
                .setIsClickable(true);

        // Styles
        Paint pointStyle = new Paint();
        pointStyle.setColor(Color.parseColor("#DE003B"));
        layoutStyle.setPointStyle(pointStyle);

        Paint textStyle = new Paint();
        textStyle.setColor(Color.RED);
        textStyle.setTextSize(26);
        textStyle.setFakeBoldText(true);
        textStyle.setShadowLayer(1, 1, 1, Color.GRAY);
        textStyle.setTextAlign(Paint.Align.CENTER);
        layoutStyle.setTextStyle(textStyle);

        Paint highlightStyle = new Paint();
        highlightStyle.setColor(Color.TRANSPARENT);
        layoutStyle.setSelectedPointStyle(highlightStyle);

        layoutStyle.setLabelPolicy(SimpleFastPointOverlayOptions.LabelPolicy.ZOOM_THRESHOLD);
        layoutStyle.setMinZoomShowLabels(10);

        // Create overlay
        SimpleFastPointOverlay ideaOverlay = new SimpleFastPointOverlay(new SimplePointTheme(points, true), layoutStyle);

        // Point listener
        ideaOverlay.setOnClickListener((point, i) -> {
            if(!areIdeasOn) {
                return;
            }

            IdeaGeoPoint ideaPoint = (IdeaGeoPoint) point.get(i);

            InfoWindow.closeAllInfoWindowsOn(mapView);

            IdeaInfoWindow info = new IdeaInfoWindow(R.layout.popup_idea, mapView, layoutStyle, points, ideaOverlay, ideaButton);
            info.open(ideaPoint, new GeoPoint(ideaPoint.getLatitude(), ideaPoint.getLongitude()), 0, 0);
        });

        return ideaOverlay;
    }
}
