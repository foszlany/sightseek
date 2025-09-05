package com.hu.sightseek.adapter;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
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
import com.hu.sightseek.Activity;
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

public class ActivityAdapter extends RecyclerView.Adapter<ActivityAdapter.ActivityViewHolder> implements Filterable {
    private final ArrayList<Activity> activityListFull;
    private ArrayList<Activity> activityListFiltered;
    private final Context context;
    private final Map<Integer, Bitmap> imageCache;

    public ActivityAdapter(Context context, ArrayList<Activity> activityList) {
        this.context = context;
        this.activityListFull = activityList;
        this.activityListFiltered = activityList;
        this.imageCache = new HashMap<>();
    }

    @NonNull
    @Override
    public ActivityViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.card_activity, parent, false);
        return new ActivityViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ActivityViewHolder holder, int position) {
        Activity activity = activityListFiltered.get(position);

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

        // Setup map previews
        Bitmap cache = imageCache.get(activity.getId());
        if(cache == null) {
            List<LatLng> points = PolyUtil.decode(activity.getPolyline());
            try {
                cache = renderMapImage(points, holder.itemView.getContext());
            }
            catch (InterruptedException e) {
                throw new RuntimeException(e);
            }

            imageCache.put(activity.getId(), cache);
        }
        holder.map.setImageBitmap(cache);
    }

    @Override
    public int getItemCount() {
        return activityListFiltered.size();
    }

    public static class ActivityViewHolder extends RecyclerView.ViewHolder {
        TextView name, category, startTime, distance, elapsedTime;
        ImageView map;

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

    private Bitmap renderMapImage(List<LatLng> points, Context context) throws InterruptedException {
        // Basic map settings
        MapView mapView = new MapView(context);
        mapView.setTileSource(TileSourceFactory.MAPNIK);
        mapView.setUseDataConnection(true);
        mapView.setLayoutParams(new ViewGroup.LayoutParams(800, 800));

        // Tiles
        TilesOverlay tilesOverlay = new TilesOverlay(mapView.getTileProvider(), context);
        mapView.getOverlayManager().add(0, tilesOverlay);

        // Polyline
        Polyline line = new Polyline();
        for(LatLng p : points) {
            line.addPoint(new GeoPoint(p.latitude, p.longitude));
        }
        line.getOutlinePaint().setColor(Color.BLUE);
        line.getOutlinePaint().setStrokeWidth(7f);
        mapView.getOverlayManager().add(line);

        // Position layout
        int w = 800, h = 800;
        mapView.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY));
        mapView.layout(0, 0, w, h);

        // Bounding box
        double minLat = Double.MAX_VALUE;
        double maxLat = -Double.MAX_VALUE;
        double minLon = Double.MAX_VALUE;
        double maxLon = -Double.MAX_VALUE;

        for(LatLng p : points) {
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

        // Zoom
        BoundingBox box = new BoundingBox(maxLat, maxLon, minLat, minLon);
        mapView.zoomToBoundingBox(box.increaseByScale(1.4f), false);

        // Render
        Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bmp);
        mapView.draw(canvas);

        // Clean up
        mapView.onPause();

        return bmp;
    }


    // Filters
    private final Filter activityFilter = new Filter() {
        @Override
        protected FilterResults performFiltering(CharSequence constraint) {
            ArrayList<Activity> filtered_list = new ArrayList<>();
            FilterResults res = new FilterResults();

            if(constraint == null || constraint.length() == 0) {
                res.count = activityListFull.size();
                res.values = activityListFull;
            }
            else {
                String filter_pattern = constraint.toString().toLowerCase().trim();

                for(Activity i : activityListFull) {
                    if(i.getName().toLowerCase().contains(filter_pattern)) {
                        filtered_list.add(i);
                    }
                }

                res.count = filtered_list.size();
                res.values = filtered_list;
            }

            return res;
        }

        @Override
        protected void publishResults(CharSequence constraint, FilterResults results) {
            activityListFiltered = (ArrayList<Activity>) results.values;
            notifyDataSetChanged(); // TODO: Fix this
        }
    };

    @Override
    public Filter getFilter() {
        return activityFilter;
    }
}