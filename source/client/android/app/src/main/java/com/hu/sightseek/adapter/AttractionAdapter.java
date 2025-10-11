package com.hu.sightseek.adapter;

import android.app.AlertDialog;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.hu.sightseek.R;
import com.hu.sightseek.db.LocalDatabaseDAO;
import com.hu.sightseek.enums.SavedAttractionStatus;
import com.hu.sightseek.model.Attraction;

import java.util.ArrayList;
import java.util.List;

public class AttractionAdapter extends RecyclerView.Adapter<AttractionAdapter.IdeaViewHolder> implements Filterable {
    private final ArrayList<Attraction> attractionListFull;
    private ArrayList<Attraction> attractionListFilteredByCategory;
    private ArrayList<Attraction> attractionListFiltered;
    private String searchQuery;

    private final Context context;

    public AttractionAdapter(Context context, ArrayList<Attraction> attractionList) {
        this.context = context;
        this.attractionListFull = new ArrayList<>(attractionList);
        this.attractionListFilteredByCategory = new ArrayList<>(attractionList);
        this.attractionListFiltered = new ArrayList<>(attractionList);
        this.searchQuery = "";
    }

    @NonNull
    @Override
    public IdeaViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.card_idea, parent, false);
        return new IdeaViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull IdeaViewHolder holder, int position) {
        Attraction attraction = attractionListFiltered.get(position);

        // Values
        holder.name.setText(attraction.getName());
        holder.status.setText(attraction.getStatus().toString());

        // Status change button
        ImageButton statusChange1Button = holder.itemView.findViewById(R.id.ideamanager_statuschangebtn);
        statusChange1Button.setOnClickListener(v -> {
            View popupView = LayoutInflater.from(v.getContext()).inflate(R.layout.attractionstatuschange_popup, null);
            AlertDialog popupDialog = new AlertDialog.Builder(v.getContext())
                    .setView(popupView)
                    .create();

            popupDialog.show();

            Button savedButton = popupView.findViewById(R.id.ideamanager_popup_savedbtn);
            Button visitedButton = popupView.findViewById(R.id.ideamanager_popup_visitedbtn);
            Button ignoreButton = popupView.findViewById(R.id.ideamanager_popup_ignorebtn);

            savedButton.setOnClickListener(w -> {
                changeStatus(attraction, SavedAttractionStatus.SAVED, holder, position);
                popupDialog.dismiss();
            });

            visitedButton.setOnClickListener(w -> {
                changeStatus(attraction, SavedAttractionStatus.VISITED, holder, position);
                popupDialog.dismiss();
            });

            ignoreButton.setOnClickListener(w -> {
                changeStatus(attraction, SavedAttractionStatus.IGNORED, holder, position);
                popupDialog.dismiss();
            });
        });


        // Delete button
        ImageButton deleteButton = holder.itemView.findViewById(R.id.ideamanager_removebtn);
        deleteButton.setOnClickListener(v -> {
            LocalDatabaseDAO dao = new LocalDatabaseDAO(holder.itemView.getContext());
            dao.deleteAttraction(attraction.getId());
            dao.close();

            notifyItemRemoved(position);
        });
    }

    private void changeStatus(Attraction attraction, SavedAttractionStatus status, @NonNull IdeaViewHolder holder, int position) {
        if(attraction.getStatus() != status) {
            LocalDatabaseDAO dao = new LocalDatabaseDAO(holder.itemView.getContext());
            dao.updateAttractionStatus(attraction.getId(), status.getIndex());
            dao.close();

            attraction.setStatus(status);

            notifyItemChanged(position);
        }
    }

    @Override
    public int getItemCount() {
        return attractionListFiltered.size();
    }

    public static class IdeaViewHolder extends RecyclerView.ViewHolder {
        TextView name, status;

        public IdeaViewHolder(@NonNull View itemView) {
            super(itemView);

            name = itemView.findViewById(R.id.ideamanager_name);
            status = itemView.findViewById(R.id.ideamanager_status);
        }
    }

    public void updateActivities(List<Attraction> newActivities) {
        this.attractionListFull.clear();
        this.attractionListFull.addAll(newActivities);

        this.attractionListFiltered.clear();
        this.attractionListFiltered.addAll(newActivities);

        this.attractionListFiltered.clear();
        this.attractionListFiltered.addAll(newActivities);

        notifyDataSetChanged();
    }

    // Filters
    private final Filter attractionFilter = new Filter() {
        @Override
        protected FilterResults performFiltering(CharSequence constraint) {
            ArrayList<Attraction> filteredList = new ArrayList<>();
            FilterResults results = new FilterResults();

            String filterPattern = constraint == null ? "" : constraint.toString().toLowerCase().trim();

            ArrayList<Attraction> baseAttractionList = attractionListFilteredByCategory.isEmpty() ? attractionListFull : attractionListFilteredByCategory;

            if(filterPattern.isEmpty()) {
                filteredList.addAll(baseAttractionList);
            }
            else {
                for(Attraction attraction : baseAttractionList) {
                    if(attraction.getName() != null && attraction.getName().toLowerCase().contains(filterPattern)) {
                        filteredList.add(attraction);
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
            attractionListFiltered = (results != null && results.values != null) ? ((ArrayList<Attraction>) results.values) : new ArrayList<>();

            notifyDataSetChanged();
        }
    };

    public void applyCategoryFilter(List<Attraction> categoryFilteredList) {
        attractionListFilteredByCategory.clear();
        attractionListFilteredByCategory.addAll(categoryFilteredList);

        if(!searchQuery.isEmpty()) {
            String filterPattern = searchQuery.toLowerCase().trim();
            ArrayList<Attraction> searchFiltered = new ArrayList<>();
            for(Attraction attraction : attractionListFilteredByCategory) {
                if(attraction.getName() != null && attraction.getName().toLowerCase().contains(filterPattern)) {
                    searchFiltered.add(attraction);
                }
            }
            attractionListFiltered = searchFiltered;
        }
        else {
            attractionListFiltered = new ArrayList<>(attractionListFilteredByCategory);
        }

        notifyDataSetChanged();
    }

    public String getSearchQuery() {
        return searchQuery;
    }

    @Override
    public Filter getFilter() {
        return attractionFilter;
    }
}