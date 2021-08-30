package com.example.edgedashanalytics.page.main;

import android.util.Log;
import android.widget.Toast;

import com.example.edgedashanalytics.event.video.AddEvent;
import com.example.edgedashanalytics.event.video.RemoveEvent;
import com.example.edgedashanalytics.event.video.Type;
import com.example.edgedashanalytics.model.Video;

import org.greenrobot.eventbus.EventBus;

public class ProcessingAdapter extends VideoRecyclerViewAdapter {
    private static final String BUTTON_TEXT = ActionButton.REMOVE.toString();
    private static final String TAG = ProcessingAdapter.class.getSimpleName();
    private VideoFragment.Listener listener;

    ProcessingAdapter(VideoFragment.Listener listener) {
        this.listener = listener;
        setHasStableIds(true);
    }

    @Override
    public void onBindViewHolder(final VideoViewHolder holder, final int position) {
        holder.video = videos.get(position);
        holder.thumbnailView.setImageBitmap(getThumbnail(videos.get(position).getId(), holder.view.getContext()));
        holder.videoFileNameView.setText(videos.get(position).getName());
        holder.actionButton.setText(BUTTON_TEXT);

        holder.actionButton.setOnClickListener(v -> {
//            if (null != listener) {
//                listener.onListFragmentInteraction(holder.video);
//            }
            final Video video = holder.video;
            Log.v(TAG, String.format("Removed %s from processing queue", video));

            EventBus.getDefault().post(new RemoveEvent(video, Type.PROCESSING));
            EventBus.getDefault().post(new AddEvent(video, Type.RAW));

            Toast.makeText(v.getContext(), "Remove from processing queue", Toast.LENGTH_SHORT).show();
        });

        if (position == 0) {
            holder.layout.setBackgroundResource(android.R.color.holo_green_light);
            holder.actionButton.setEnabled(false);
        } else {
            holder.layout.setBackgroundResource(android.R.color.transparent);
        }
    }
}
