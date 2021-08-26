package com.example.edgedashanalytics.util.video.viewholderprocessor;

import android.content.Context;

import com.example.edgedashanalytics.data.video.VideoViewModel;
import com.example.edgedashanalytics.page.main.VideoRecyclerViewAdapter;

public interface VideoViewHolderProcessor {
    void process(Context context, VideoViewModel vm, VideoRecyclerViewAdapter.VideoViewHolder viewHolder, int position);
}
