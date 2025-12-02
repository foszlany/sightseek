package com.hu.sightseek.adapter;

import static android.view.View.GONE;

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
import com.hu.sightseek.broadcast.IdeaBroadcaster;
import com.hu.sightseek.db.LocalDatabaseDAO;
import com.hu.sightseek.enums.SavedIdeaStatus;
import com.hu.sightseek.model.Idea;

import java.util.ArrayList;
import java.util.List;

public class IdeaAdapter extends RecyclerView.Adapter<IdeaAdapter.IdeaViewHolder> implements Filterable {
    private final ArrayList<Idea> ideaListFull;
    private ArrayList<Idea> ideaListFilteredByCategory;
    private ArrayList<Idea> ideaListFiltered;
    private String searchQuery;

    private final Context context;

    public IdeaAdapter(Context context, ArrayList<Idea> ideaList) {
        this.context = context;
        this.ideaListFull = new ArrayList<>(ideaList);
        this.ideaListFilteredByCategory = new ArrayList<>(ideaList);
        this.ideaListFiltered = new ArrayList<>(ideaList);
        this.searchQuery = "";
    }

    @NonNull
    @Override
    public IdeaViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_idea, parent, false);
        return new IdeaViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull IdeaViewHolder holder, int position) {
        Idea idea = ideaListFiltered.get(position);

        // Values
        holder.name.setText(idea.getName());
        holder.place.setText(idea.getPlace());
        holder.status.setText(idea.getStatus().toString());

        // Status change button
        ImageButton statusChange1Button = holder.itemView.findViewById(R.id.ideamanager_statuschangebtn);
        statusChange1Button.setOnClickListener(v -> {
            View popupView = LayoutInflater.from(v.getContext()).inflate(R.layout.ideastatuschange_popup, null);
            AlertDialog popupDialog = new AlertDialog.Builder(v.getContext())
                    .setView(popupView)
                    .create();

            Button savedButton = popupView.findViewById(R.id.ideamanager_popup_savedbtn);
            Button ignoreButton = popupView.findViewById(R.id.ideamanager_popup_ignorebtn);
            Button visitedButton = popupView.findViewById(R.id.ideamanager_popup_visitedbtn);

            if(idea.getStatus() == SavedIdeaStatus.SAVED) {
                savedButton.setVisibility(GONE);
            }
            else if(idea.getStatus() == SavedIdeaStatus.IGNORED) {
                ignoreButton.setVisibility(GONE);
            }
            else if(idea.getStatus() == SavedIdeaStatus.VISITED) {
                visitedButton.setVisibility(GONE);
            }

            popupDialog.show();

            savedButton.setOnClickListener(w -> {
                changeStatus(idea, SavedIdeaStatus.SAVED, holder, position);
                popupDialog.dismiss();
            });

            ignoreButton.setOnClickListener(w -> {
                changeStatus(idea, SavedIdeaStatus.IGNORED, holder, position);
                popupDialog.dismiss();
            });

            visitedButton.setOnClickListener(w -> {
                changeStatus(idea, SavedIdeaStatus.VISITED, holder, position);
                popupDialog.dismiss();
            });
        });

        // Delete button
        ImageButton deleteButton = holder.itemView.findViewById(R.id.ideamanager_removebtn);
        deleteButton.setOnClickListener(v -> {
            LocalDatabaseDAO dao = new LocalDatabaseDAO(holder.itemView.getContext());
            dao.deleteIdea(idea.getId());
            dao.close();

            notifyItemRemoved(position);

            if(idea.getStatus() == SavedIdeaStatus.SAVED) {
                IdeaBroadcaster.sendUpdate(context);
            }
        });
    }

    private void changeStatus(Idea idea, SavedIdeaStatus status, @NonNull IdeaViewHolder holder, int position) {
        if(idea.getStatus() != status) {
            LocalDatabaseDAO dao = new LocalDatabaseDAO(holder.itemView.getContext());
            dao.updateIdeaStatus(idea.getId(), status.getIndex());
            dao.close();

            idea.setStatus(status);

            notifyItemChanged(position);

            if(idea.getStatus() == SavedIdeaStatus.SAVED) {
                IdeaBroadcaster.sendUpdate(context);
            }
        }
    }

    @Override
    public int getItemCount() {
        return ideaListFiltered.size();
    }

    public static class IdeaViewHolder extends RecyclerView.ViewHolder {
        TextView name, place, status;

        public IdeaViewHolder(@NonNull View itemView) {
            super(itemView);

            name = itemView.findViewById(R.id.ideamanager_name);
            place = itemView.findViewById(R.id.ideamanager_place);
            status = itemView.findViewById(R.id.ideamanager_status);
        }
    }

    // Filters
    private final Filter ideaFilter = new Filter() {
        @Override
        protected FilterResults performFiltering(CharSequence constraint) {
            ArrayList<Idea> filteredList = new ArrayList<>();
            FilterResults results = new FilterResults();

            String filterPattern = constraint == null ? "" : constraint.toString().toLowerCase().trim();

            ArrayList<Idea> baseIdeaList = ideaListFilteredByCategory.isEmpty() ? ideaListFull : ideaListFilteredByCategory;

            if(filterPattern.isEmpty()) {
                filteredList.addAll(baseIdeaList);
            }
            else {
                for(Idea idea : baseIdeaList) {
                    if(idea.getName() != null && idea.getName().toLowerCase().contains(filterPattern)) {
                        filteredList.add(idea);
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
            ideaListFiltered = (results != null && results.values != null) ? ((ArrayList<Idea>) results.values) : new ArrayList<>();

            notifyDataSetChanged();
        }
    };

    public void applyCategoryFilter(List<Idea> categoryFilteredList) {
        ideaListFilteredByCategory.clear();
        ideaListFilteredByCategory.addAll(categoryFilteredList);

        if(!searchQuery.isEmpty()) {
            String filterPattern = searchQuery.toLowerCase().trim();
            ArrayList<Idea> searchFiltered = new ArrayList<>();
            for(Idea idea : ideaListFilteredByCategory) {
                if(idea.getName() != null && idea.getName().toLowerCase().contains(filterPattern)) {
                    searchFiltered.add(idea);
                }
            }
            ideaListFiltered = searchFiltered;
        }
        else {
            ideaListFiltered = new ArrayList<>(ideaListFilteredByCategory);
        }

        notifyDataSetChanged();
    }

    public String getSearchQuery() {
        return searchQuery;
    }

    @Override
    public Filter getFilter() {
        return ideaFilter;
    }
}