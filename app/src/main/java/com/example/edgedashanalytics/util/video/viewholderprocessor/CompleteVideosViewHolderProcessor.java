package com.example.edgedashanalytics.util.video.viewholderprocessor;

import android.content.Context;
import android.widget.Toast;

import com.example.edgedashanalytics.data.VideoViewModel;
import com.example.edgedashanalytics.model.Video;
import com.example.edgedashanalytics.page.main.VideoRecyclerViewAdapter;

public class CompleteVideosViewHolderProcessor implements VideoViewHolderProcessor {
    @Override
    public void process(Context context, VideoViewModel vm, VideoRecyclerViewAdapter.VideoViewHolder holder, int pos) {
        holder.actionButton.setOnClickListener(view -> {
            // TODO add action
            final Video video = holder.video;
            Toast.makeText(context, String.format("Completed analysis of %s", video.getName()), Toast.LENGTH_SHORT).show();
        });
    }
}
