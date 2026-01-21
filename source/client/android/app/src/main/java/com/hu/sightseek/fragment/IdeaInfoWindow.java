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
import com.hu.sightseek.enums.SavedIdeaStatus;
import com.hu.sightseek.model.IdeaGeoPoint;

import org.osmdroid.api.IGeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.infowindow.InfoWindow;
import org.osmdroid.views.overlay.simplefastpoint.SimpleFastPointOverlay;
import org.osmdroid.views.overlay.simplefastpoint.SimpleFastPointOverlayOptions;
import org.osmdroid.views.overlay.simplefastpoint.SimplePointTheme;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class IdeaInfoWindow extends InfoWindow {
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    SimpleFastPointOverlayOptions layoutStyle;
    List<IGeoPoint> points;
    ImageButton ideaButton;

    public IdeaInfoWindow(int layoutResId, MapView mapView, SimpleFastPointOverlayOptions layoutStyle, List<IGeoPoint> points, ImageButton ideaButton) {
        super(layoutResId, mapView);

        this.layoutStyle = layoutStyle;
        this.points = points;
        this.ideaButton = ideaButton;
    }

    @Override
    public void onOpen(Object obj) {
        View view = mView;

        TextView placeTextView = view.findViewById(R.id.ideapopup_placename);
        Button visitedButton = view.findViewById(R.id.ideapopup_visitedbtn);
        Button removeButton = view.findViewById(R.id.ideapopup_removebtn);

        IdeaGeoPoint ideaPoint = (IdeaGeoPoint) obj;
        placeTextView.setText(ideaPoint.getLabel());

        visitedButton.setOnClickListener(v -> {
            executor.execute(() -> {
                LocalDatabaseDAO dao2 = new LocalDatabaseDAO(view.getContext());
                dao2.updateIdeaStatus(ideaPoint.getId(), SavedIdeaStatus.VISITED.getIndex());
                dao2.close();

                if(points.size() >= 2000) {
                    swapIcon(true);
                }

                points.remove(ideaPoint);
                mMapView.postInvalidate();

                if(points.size() >= 2000) {
                    new Handler(Looper.getMainLooper()).postDelayed(() -> swapIcon(false), points.size() / 3);
                }
            });

            close();
        });

        removeButton.setOnClickListener(v -> {
            executor.execute(() -> {
                LocalDatabaseDAO dao2 = new LocalDatabaseDAO(view.getContext());
                dao2.deleteIdea(ideaPoint.getId());
                dao2.close();

                if(points.size() >= 2000) {
                    swapIcon(true);
                }

                points.remove(ideaPoint);
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
        if(ideaButton == null) {
            return;
        }

        ((RecordActivity) mView.getContext()).runOnUiThread(() -> {
            Drawable icon;

            if(loading) {
                icon = ResourcesCompat.getDrawable(mView.getResources(), R.drawable.baseline_change_circle_24, null);

                Animation rotate = AnimationUtils.loadAnimation(mView.getContext(), R.anim.looping_rotation);
                ideaButton.startAnimation(rotate);
            }
            else {
                icon = ResourcesCompat.getDrawable(mView.getResources(), R.drawable.baseline_attractions_24, null);
                ideaButton.clearAnimation();
            }

            ideaButton.setImageDrawable(icon);
        });
    }
}