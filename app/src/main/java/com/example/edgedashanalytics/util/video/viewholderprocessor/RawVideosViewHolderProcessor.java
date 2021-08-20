package com.example.edgedashanalytics.util.video.viewholderprocessor;

import android.content.Context;
import android.util.Log;
import android.widget.Toast;

import com.example.edgedashanalytics.data.VideoViewModel;
import com.example.edgedashanalytics.event.AddEvent;
import com.example.edgedashanalytics.event.RemoveEvent;
import com.example.edgedashanalytics.event.Type;
import com.example.edgedashanalytics.model.Video;
import com.example.edgedashanalytics.page.main.VideoRecyclerViewAdapter;

import org.greenrobot.eventbus.EventBus;

public class RawVideosViewHolderProcessor implements VideoViewHolderProcessor {
    private static final String TAG = RawVideosViewHolderProcessor.class.getSimpleName();

    public RawVideosViewHolderProcessor() {
    }

    @Override
    public void process(final Context context, final VideoViewModel vm,
                        final VideoRecyclerViewAdapter.VideoViewHolder viewHolder, final int position) {
        viewHolder.actionButton.setOnClickListener(view -> {
            final Video video = viewHolder.video;
            Log.v(TAG, String.format("User selected %s", video));

            // TODO add processing

            EventBus.getDefault().post(new AddEvent(video, Type.PROCESSING));
            EventBus.getDefault().post(new RemoveEvent(video, Type.RAW));

            Toast.makeText(context, "Add to processing queue", Toast.LENGTH_SHORT).show();

        });
    }
}
