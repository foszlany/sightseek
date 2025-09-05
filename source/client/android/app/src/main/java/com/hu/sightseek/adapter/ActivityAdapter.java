package com.hu.sightseek.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.hu.sightseek.Activity;
import com.hu.sightseek.R;

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

        holder.nameText.setText(activity.getName());

        switch(activity.getCategory()) {
            case LOCOMOTOR:
                holder.categoryText.setText(R.string.travelmethod_loco);
                break;

            case MICROMOBILITY:
                holder.categoryText.setText(R.string.travelmethod_micro);
                break;

            case OTHER:
                holder.categoryText.setText(R.string.travelmethod_other);
                break;
        }

        String startTime = activity.getStarttime().replace("T", ". ").replace("-", ".");
        holder.startTimeText.setText(startTime);

        holder.distanceText.setText(String.format(Locale.US, "%.2f km", activity.getDistance() / 1000.0));

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

        holder.elapsedTimeText.setText(elapsedTimeStr);
    }

    @Override
    public int getItemCount() {
        return activityList.size();
    }

    public static class ActivityViewHolder extends RecyclerView.ViewHolder {
        TextView nameText, categoryText, startTimeText, distanceText, elapsedTimeText;

        public ActivityViewHolder(@NonNull View itemView) {
            super(itemView);
            nameText = itemView.findViewById(R.id.card_activity_title);
            categoryText = itemView.findViewById(R.id.card_activity_category);
            startTimeText = itemView.findViewById(R.id.card_activity_date);
            distanceText = itemView.findViewById(R.id.card_activity_distancevalue);
            elapsedTimeText = itemView.findViewById(R.id.card_activity_timevalue);
        }
    }
}
