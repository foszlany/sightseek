package com.hu.sightseek.adapter;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.DrawableCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.hu.sightseek.R;
import com.hu.sightseek.model.LeaderboardEntry;

import java.util.ArrayList;

public class LeaderboardCellEntryAdapter extends RecyclerView.Adapter<LeaderboardCellEntryAdapter.LeaderboardCellEntryViewHolder> {
    private final Context context;
    private final ArrayList<LeaderboardEntry> entryList;

    public LeaderboardCellEntryAdapter(Context context, ArrayList<LeaderboardEntry> entries) {
        this.context = context;
        this.entryList = entries;
    }

    @NonNull
    @Override
    public LeaderboardCellEntryViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_leaderboardentry, parent, false);
        return new LeaderboardCellEntryViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull LeaderboardCellEntryViewHolder holder, int position) {
        View v = holder.itemView.findViewById(R.id.leaderboard_myentry);
        Drawable background = v.getBackground();
        Drawable wrapped = DrawableCompat.wrap(background.mutate());

        if(position == 0) {
            DrawableCompat.setTint(wrapped, ContextCompat.getColor(v.getContext(), R.color.gold));
        }
        else if(position == 1) {
            DrawableCompat.setTint(wrapped, ContextCompat.getColor(v.getContext(), R.color.silver));
        }
        else if(position == 2) {
            DrawableCompat.setTint(wrapped, ContextCompat.getColor(v.getContext(), R.color.bronze));
        }
        else {
            DrawableCompat.setTint(wrapped, ContextCompat.getColor(v.getContext(), R.color.dark_gray));
        }

        v.setBackground(wrapped);

        LeaderboardEntry entry = entryList.get(position);

        // Values
        holder.placing.setText(context.getString(R.string.leaderboard_entry_placing, position + 1));
        holder.username.setText(entry.getUsername());
        holder.value.setText(context.getString(R.string.leaderboard_entry_cellvalue, (int) entry.getValue()));
    }

    @Override
    public int getItemCount() {
        return entryList.size();
    }

    public static class LeaderboardCellEntryViewHolder extends RecyclerView.ViewHolder {
        TextView placing, username, value;

        public LeaderboardCellEntryViewHolder(@NonNull View itemView) {
            super(itemView);

            placing = itemView.findViewById(R.id.leaderboard_entry_placing);
            username = itemView.findViewById(R.id.leaderboard_entry_name);
            value = itemView.findViewById(R.id.leaderboard_entry_value);
        }
    }
}