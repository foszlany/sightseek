package com.hu.sightseek.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.hu.sightseek.R;

import java.util.ArrayList;

public class ConsoleAdapter extends RecyclerView.Adapter<ConsoleAdapter.ConsoleViewHolder> {
    private final ArrayList<String> logs = new ArrayList<>();

    public void addLog(String log) {
        logs.add(log);
        notifyItemInserted(logs.size() - 1);
    }

    public void clearLogs() {
        logs.clear();
        notifyDataSetChanged();
    }

    public static class ConsoleViewHolder extends RecyclerView.ViewHolder {
        TextView logTextView;

        ConsoleViewHolder(View itemView) {
            super(itemView);
            logTextView = itemView.findViewById(R.id.logTextView);
        }
    }

    @NonNull
    @Override
    public ConsoleViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_log, parent, false);
        return new ConsoleViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ConsoleViewHolder holder, int position) {
        holder.logTextView.setText(logs.get(position));
    }

    @Override
    public int getItemCount() {
        return logs.size();
    }
}

