package com.hu.sightseek.adapter;

import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.gms.maps.model.LatLng;
import com.google.maps.android.PolyUtil;
import com.hu.sightseek.Activity;
import com.hu.sightseek.R;

import org.osmdroid.util.BoundingBox;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.CustomZoomButtonsController;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Polyline;
import org.osmdroid.views.overlay.TilesOverlay;

import java.util.List;
import java.util.Locale;

public class ActivityAdapter extends RecyclerView.Adapter<ActivityAdapter.ActivityViewHolder> {
    private List<Activity> activityList;
    private Context context;

    public ActivityAdapter(Context context, List<Activity> activityList) {
        this.context = context;
        this.activityList = activityList;
    }

    @NonNull
    @Override
    public ActivityViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.card_activity, parent, false);
        return new ActivityViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ActivityViewHolder holder, int position) {
        Activity activity = activityList.get(position);

        // Values
        holder.name.setText(activity.getName());

        switch(activity.getCategory()) {
            case LOCOMOTOR:
                holder.category.setText(R.string.travelmethod_loco);
                break;

            case MICROMOBILITY:
                holder.category.setText(R.string.travelmethod_micro);
                break;

            case OTHER:
                holder.category.setText(R.string.travelmethod_other);
                break;
        }

        String startTime = activity.getStarttime().replace("T", ". ").replace("-", ".");
        holder.startTime.setText(startTime);

        holder.distance.setText(String.format(Locale.US, "%.2f km", activity.getDistance() / 1000.0));

        double elapsedTime = activity.getElapsedtime();
        int hours = (int) elapsedTime / 3600;
        int minutes = ((int) elapsedTime % 3600) / 60;
        int seconds = (int) elapsedTime % 60;

        String elapsedTimeStr;
        if(hours > 0) {
            elapsedTimeStr = String.format(Locale.US, "%dh %dm", hours, minutes);
        }
        else {
            elapsedTimeStr = String.format(Locale.US, "%dm %ds", minutes, seconds);
        }

        holder.elapsedTime.setText(elapsedTimeStr);

        // Setup map
        holder.map.setBackgroundColor(Color.TRANSPARENT);
        holder.map.setMultiTouchControls(false);
        holder.map.setUseDataConnection(true);

        TilesOverlay tilesOverlay = holder.map.getOverlayManager().getTilesOverlay();
        tilesOverlay.setLoadingBackgroundColor(Color.TRANSPARENT);
        tilesOverlay.setLoadingLineColor(Color.TRANSPARENT);

        // Setup polyline
        List<LatLng> pointList = PolyUtil.decode(activity.getPolyline());
        Polyline polyline = new Polyline();
        for(LatLng point : pointList) {
            polyline.addPoint(new GeoPoint(point.latitude, point.longitude));
        }

        polyline.getOutlinePaint().setColor(Color.BLUE);
        polyline.getOutlinePaint().setStrokeWidth(7.0f);
        holder.map.getOverlayManager().add(polyline);

        // Calculate bounding box
        double minLat = Double.MAX_VALUE;
        double maxLat = -Double.MAX_VALUE;
        double minLon = Double.MAX_VALUE;
        double maxLon = -Double.MAX_VALUE;

        for(LatLng p : pointList) {
            if(p.latitude < minLat) {
                minLat = p.latitude;
            }
            if(p.latitude > maxLat) {
                maxLat = p.latitude;
            }
            if(p.longitude < minLon) {
                minLon = p.longitude;
            }
            if(p.longitude > maxLon) {
                maxLon = p.longitude;
            }
        }

        BoundingBox box = new BoundingBox(maxLat, maxLon, minLat, minLon);

        // Set zoom based on bounding box
        holder.map.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                holder.map.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                holder.map.zoomToBoundingBox(box.increaseByScale(1.4f), false);
            }
        });

        holder.map.getZoomController().setVisibility(CustomZoomButtonsController.Visibility.NEVER);
        holder.map.setVerticalMapRepetitionEnabled(false);
    }

    @Override
    public int getItemCount() {
        return activityList.size();
    }

    public static class ActivityViewHolder extends RecyclerView.ViewHolder {
        TextView name, category, startTime, distance, elapsedTime;
        MapView map;

        public ActivityViewHolder(@NonNull View itemView) {
            super(itemView);
            name = itemView.findViewById(R.id.card_activity_title);
            category = itemView.findViewById(R.id.card_activity_category);
            startTime = itemView.findViewById(R.id.card_activity_date);
            distance = itemView.findViewById(R.id.card_activity_distancevalue);
            elapsedTime = itemView.findViewById(R.id.card_activity_timevalue);
            map = itemView.findViewById(R.id.card_map);
        }
    }
}
