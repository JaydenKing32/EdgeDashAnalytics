package com.example.edgedashanalytics.util.video.viewholderprocessor;

import android.content.Context;
import android.graphics.Color;

import com.example.edgedashanalytics.data.video.VideoViewModel;
import com.example.edgedashanalytics.page.main.VideoRecyclerViewAdapter;

public class ProcessingVideosViewHolderProcessor implements VideoViewHolderProcessor {
    @Override
    public void process(Context context, final VideoViewModel vm,  VideoRecyclerViewAdapter.VideoViewHolder viewHolder, int position) {
        disableFirstRowButton(viewHolder, position);

    }

    private void disableFirstRowButton(VideoRecyclerViewAdapter.VideoViewHolder viewHolder, int position) {
        if (position == 0) {
            disable(viewHolder);
        }
    }

    private void disable(VideoRecyclerViewAdapter.VideoViewHolder viewHolder) {
        viewHolder.view.setBackgroundColor(Color.parseColor("#e7eecc"));
        viewHolder.actionButton.setEnabled(false);
    }
}
