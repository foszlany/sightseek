package com.hu.sightseek.adapter;

import static com.hu.sightseek.utils.SightseekGenericUtils.getBoundingBox;
import static com.hu.sightseek.utils.SightseekGenericUtils.setupRouteLine;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.gms.maps.model.LatLng;
import com.google.maps.android.PolyUtil;
import com.hu.sightseek.activity.ActivityActivity;
import com.hu.sightseek.model.Activity;
import com.hu.sightseek.R;

import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.BoundingBox;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Polyline;
import org.osmdroid.views.overlay.TilesOverlay;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class ActivityAdapter extends RecyclerView.Adapter<ActivityAdapter.ActivityViewHolder> implements Filterable {
    private static final int w = 400, h = 400;

    private final ArrayList<Activity> activityListFull;
    private ArrayList<Activity> activityListFilteredByCategory;
    private ArrayList<Activity> activityListFiltered;
    private String searchQuery;

    private final Context context;
    private final Map<Integer, Bitmap> imageCache;
    private final MapView mapView;
    private final Executor executor;

    public ActivityAdapter(Context context, ArrayList<Activity> activityList) {
        this.context = context;
        this.activityListFull = new ArrayList<>(activityList);
        this.activityListFilteredByCategory = new ArrayList<>(activityList);
        this.activityListFiltered = new ArrayList<>(activityList);
        this.searchQuery = "";
        this.imageCache = new HashMap<>();
        this.executor = Executors.newSingleThreadExecutor();

        // Map
        mapView = new MapView(context);
        mapView.setTileSource(TileSourceFactory.MAPNIK);
        mapView.setUseDataConnection(true);
        mapView.setLayoutParams(new ViewGroup.LayoutParams(w, h));

        // Tiles
        TilesOverlay tilesOverlay = new TilesOverlay(mapView.getTileProvider(), context);
        mapView.getOverlayManager().add(0, tilesOverlay);

        // Position layout
        mapView.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY));
        mapView.layout(0, 0, w, h);
    }

    @NonNull
    @Override
    public ActivityViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_activity, parent, false);
        return new ActivityViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ActivityViewHolder holder, int position) {
        Activity activity = activityListFiltered.get(position);

        // Values
        holder.name.setText(activity.getName());
        holder.category.setText(activity.getCategory().toShortString());

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

        // Setup map previews
        holder.map.setImageResource(R.drawable.loading);

        executor.execute(() -> {
            Bitmap cache = imageCache.get(activity.getId());
            if(cache == null) {
                holder.itemView.post(() -> {
                    holder.map.setImageResource(R.drawable.loading);
                });

                List<LatLng> points = PolyUtil.decode(activity.getPolyline());
                cache = renderMapImage(points);
                imageCache.put(activity.getId(), cache);

                Bitmap finalCache = cache;
                holder.itemView.post(() -> {
                    holder.map.setImageBitmap(finalCache);
                });
            }
            else {
                holder.map.setImageBitmap(cache);
            }
        });

        // Intent to the activity's page
        holder.card.setOnClickListener(v -> {
            Intent intent = new Intent(context, ActivityActivity.class);
            Bundle bundle = new Bundle();

            bundle.putInt("id", activity.getId());
            intent.putExtras(bundle);

            context.startActivity(intent);
        });
    }

    @Override
    public int getItemCount() {
        return activityListFiltered.size();
    }

    public static class ActivityViewHolder extends RecyclerView.ViewHolder {
        TextView name, category, startTime, distance, elapsedTime;
        ImageView map;
        View card;

        public ActivityViewHolder(@NonNull View itemView) {
            super(itemView);

            card = itemView.findViewById(R.id.main_activitycard);
            name = itemView.findViewById(R.id.card_activity_title);
            category = itemView.findViewById(R.id.card_activity_category);
            startTime = itemView.findViewById(R.id.card_activity_date);
            distance = itemView.findViewById(R.id.card_activity_distancevalue);
            elapsedTime = itemView.findViewById(R.id.card_activity_timevalue);
            map = itemView.findViewById(R.id.card_map);
        }
    }

    private Bitmap renderMapImage(List<LatLng> points) {
        // Zoom
        BoundingBox box = getBoundingBox(points);
        mapView.zoomToBoundingBox(box.increaseByScale(1.3f), false);

        // Polyline
        Polyline line = new Polyline();
        for(LatLng p : points) {
            line.addPoint(new GeoPoint(p.latitude, p.longitude));
        }

        setupRouteLine(line, false);
        mapView.getOverlayManager().add(line);

        // Continuously update tile states until all tiles load in or timeout
        TilesOverlay tilesOverlay = mapView.getOverlayManager().getTilesOverlay();
        for(int i = 0; i < 10; i++) {
            // Force draw
            Bitmap temp = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888);
            Canvas tempCanvas = new Canvas(temp);
            mapView.draw(tempCanvas);

            if(tilesOverlay.getTileStates().getNotFound() == 0) {
                break;
            }

            try {
                Thread.sleep(5);
            }
            catch(InterruptedException ignored) {}
        }

        // Render
        Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.RGB_565);
        Canvas canvas = new Canvas(bmp);
        mapView.draw(canvas);

        // Clean up
        mapView.getOverlayManager().remove(line);

        return bmp;
    }

    public void updateActivities(List<Activity> newActivities) {
        this.activityListFull.clear();
        this.activityListFull.addAll(newActivities);

        this.activityListFiltered.clear();
        this.activityListFiltered.addAll(newActivities);

        this.activityListFiltered.clear();
        this.activityListFiltered.addAll(newActivities);

        notifyDataSetChanged();
    }

    // Filters
    private final Filter activityFilter = new Filter() {
        @Override
        protected FilterResults performFiltering(CharSequence constraint) {
            ArrayList<Activity> filteredList = new ArrayList<>();
            FilterResults results = new FilterResults();

            String filterPattern = constraint == null ? "" : constraint.toString().toLowerCase().trim();

            ArrayList<Activity> baseActivityList = activityListFilteredByCategory.isEmpty() ? activityListFull : activityListFilteredByCategory;

            if(filterPattern.isEmpty()) {
                filteredList.addAll(baseActivityList);
            }
            else {
                for(Activity activity : baseActivityList) {
                    if(activity.getName() != null && activity.getName().toLowerCase().contains(filterPattern)) {
                        filteredList.add(activity);
                    }
                }
            }

            results.values = filteredList;
            results.count = filteredList.size();
            return results;
        }

        @Override
        protected void publishResults(CharSequence constraint, FilterResults results) {
            searchQuery = constraint != null ? constraint.toString() : "";
            activityListFiltered = (results != null && results.values != null) ? ((ArrayList<Activity>) results.values) : new ArrayList<>();

            notifyDataSetChanged();
        }
    };

    public void applyCategoryFilter(List<Activity> categoryFilteredList) {
        activityListFilteredByCategory.clear();
        activityListFilteredByCategory.addAll(categoryFilteredList);

        if(!searchQuery.isEmpty()) {
            String filterPattern = searchQuery.toLowerCase().trim();
            ArrayList<Activity> searchFiltered = new ArrayList<>();
            for(Activity activity : activityListFilteredByCategory) {
                if(activity.getName() != null && activity.getName().toLowerCase().contains(filterPattern)) {
                    searchFiltered.add(activity);
                }
            }
            activityListFiltered = searchFiltered;
        }
        else {
            activityListFiltered = new ArrayList<>(activityListFilteredByCategory);
        }

        notifyDataSetChanged();
    }

    public String getSearchQuery() {
        return searchQuery;
    }

    @Override
    public Filter getFilter() {
        return activityFilter;
    }
}