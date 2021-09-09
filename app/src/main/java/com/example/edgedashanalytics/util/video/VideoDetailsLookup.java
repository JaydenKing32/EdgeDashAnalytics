package com.example.edgedashanalytics.util.video;

import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.selection.ItemDetailsLookup;
import androidx.recyclerview.widget.RecyclerView;

import com.example.edgedashanalytics.page.adapter.VideoRecyclerViewAdapter;

public class VideoDetailsLookup extends ItemDetailsLookup<Long> {
    private final RecyclerView recyclerView;

    public VideoDetailsLookup(RecyclerView recyclerView) {
        this.recyclerView = recyclerView;
    }

    @Nullable
    @Override
    public ItemDetails<Long> getItemDetails(@NonNull MotionEvent e) {
        View view = recyclerView.findChildViewUnder(e.getX(), e.getY());
        if (view != null) {
            RecyclerView.ViewHolder holder = recyclerView.getChildViewHolder(view);
            if (holder instanceof VideoRecyclerViewAdapter.VideoViewHolder) {
                return ((VideoRecyclerViewAdapter.VideoViewHolder) holder).getItemDetails();
            }
        }
        return null;
    }
}

