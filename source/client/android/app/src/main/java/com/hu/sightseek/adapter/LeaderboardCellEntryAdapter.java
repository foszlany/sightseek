package com.hu.sightseek.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.hu.sightseek.R;
import com.hu.sightseek.model.LeaderboardEntry;

import java.util.ArrayList;

public class LeaderboardCellEntryAdapter extends RecyclerView.Adapter<LeaderboardCellEntryAdapter.LeaderboardCellEntryViewHolder> {
    private final ArrayList<LeaderboardEntry> entryList;
    private final Context context;

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
        if(position == 0) {
            View v = holder.itemView.findViewById(R.id.leaderboard_entry);
            v.setBackgroundColor(holder.itemView.getResources().getColor(R.color.gold, null));
        }
        else if(position == 1) {
            View v = holder.itemView.findViewById(R.id.leaderboard_entry);
            v.setBackgroundColor(holder.itemView.getResources().getColor(R.color.silver, null));
        }
        else if(position == 2) {
            View v = holder.itemView.findViewById(R.id.leaderboard_entry);
            v.setBackgroundColor(holder.itemView.getResources().getColor(R.color.bronze, null));
        }
        else if(position % 2 == 0) {
            View v = holder.itemView.findViewById(R.id.leaderboard_entry);
            v.setBackgroundColor(holder.itemView.getResources().getColor(R.color.dark_gray, null));
        }

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