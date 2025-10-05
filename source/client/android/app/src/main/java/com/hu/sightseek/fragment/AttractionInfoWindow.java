package com.hu.sightseek.fragment;

import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.core.content.res.ResourcesCompat;

import com.hu.sightseek.R;
import com.hu.sightseek.activity.RecordActivity;
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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AttractionInfoWindow extends InfoWindow {
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    SimpleFastPointOverlayOptions layoutStyle;
    List<IGeoPoint> points;
    SimpleFastPointOverlay attractionsOverlay;
    ImageButton attractionButton;

    public AttractionInfoWindow(int layoutResId, MapView mapView, SimpleFastPointOverlayOptions layoutStyle, List<IGeoPoint> points, SimpleFastPointOverlay attractionsOverlay, ImageButton attractionButton) {
        super(layoutResId, mapView);

        this.layoutStyle = layoutStyle;
        this.points = points;
        this.attractionsOverlay = attractionsOverlay;
        this.attractionButton = attractionButton;
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
            executor.execute(() -> {
                LocalDatabaseDAO dao2 = new LocalDatabaseDAO(view.getContext());
                dao2.updateAttractionStatus(attractionPoint.getId(), SavedAttractionStatus.VISITED.getIndex());
                dao2.close();

                if(points.size() >= 2000) {
                    swapIcon(true);
                }

                points.remove(attractionPoint);
                attractionsOverlay = new SimpleFastPointOverlay(new SimplePointTheme(points, true), layoutStyle);
                mMapView.postInvalidate();

                if(points.size() >= 2000) {
                    // TODO this is terrible
                    new Handler(Looper.getMainLooper()).postDelayed(() -> swapIcon(false), points.size() / 3);
                }
            });

            close();
        });

        removeButton.setOnClickListener(v -> {
            executor.execute(() -> {
                LocalDatabaseDAO dao2 = new LocalDatabaseDAO(view.getContext());
                dao2.deleteAttraction(attractionPoint.getId());
                dao2.close();

                if(points.size() >= 2000) {
                    swapIcon(true);
                }

                points.remove(attractionPoint);
                attractionsOverlay = new SimpleFastPointOverlay(new SimplePointTheme(points, true), layoutStyle);
                mMapView.postInvalidate();

                if(points.size() >= 2000) {
                    new Handler(Looper.getMainLooper()).postDelayed(() -> swapIcon(false), points.size() / 3);
                }
            });

            close();
        });
    }

    @Override
    public void onClose() {}

    private void swapIcon(boolean loading) {
        ((RecordActivity) mView.getContext()).runOnUiThread(() -> {
            Drawable icon;

            if(loading) {
                icon = ResourcesCompat.getDrawable(mView.getResources(), R.drawable.baseline_change_circle_24, null);

                Animation rotate = AnimationUtils.loadAnimation(mView.getContext(), R.anim.looping_rotation);
                attractionButton.startAnimation(rotate);
            }
            else {
                icon = ResourcesCompat.getDrawable(mView.getResources(), R.drawable.baseline_attractions_24, null);
                attractionButton.clearAnimation();
            }

            attractionButton.setImageDrawable(icon);
        });
    }
}