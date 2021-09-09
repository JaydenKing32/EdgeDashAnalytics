package com.example.edgedashanalytics.page.adapter;

import android.util.Log;
import android.widget.Toast;

import com.example.edgedashanalytics.page.main.ActionButton;
import com.example.edgedashanalytics.page.main.VideoFragment;
import com.example.edgedashanalytics.util.video.analysis.AnalysisTools;

public class RawAdapter extends VideoRecyclerViewAdapter {
    private static final String BUTTON_TEXT = ActionButton.ADD.toString();
    private static final String TAG = RawAdapter.class.getSimpleName();

    public RawAdapter(VideoFragment.Listener listener) {
        super(listener);
    }

    @Override
    public void onBindViewHolder(final VideoViewHolder holder, final int position) {
        holder.video = videos.get(position);
        holder.thumbnailView.setImageBitmap(getThumbnail(videos.get(position).getId(), holder.view.getContext()));
        holder.videoFileNameView.setText(videos.get(position).getName());
        holder.actionButton.setText(BUTTON_TEXT);

        holder.actionButton.setOnClickListener(v -> {
            if (listener == null) {
                Log.e(TAG, "Null listener");
                return;
            }

            if (listener.getIsConnected()) {
                listener.getAddVideo(holder.video);
                listener.getNextTransfer();
            } else {
                Log.v(TAG, String.format("User selected %s", holder.video));
                AnalysisTools.processVideo(holder.video, v.getContext());

                Toast.makeText(v.getContext(), "Add to processing queue", Toast.LENGTH_SHORT).show();
            }
        });

        if (tracker.isSelected(getItemId(position))) {
            holder.layout.setBackgroundResource(android.R.color.darker_gray);
        } else {
            holder.layout.setBackgroundResource(android.R.color.transparent);
        }
    }
}
