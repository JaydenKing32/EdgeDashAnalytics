package com.example.edgedashanalytics.page.main;

import static com.example.edgedashanalytics.util.file.FileManager.getResultDirPath;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.provider.MediaStore;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.selection.ItemDetailsLookup;
import androidx.recyclerview.selection.Selection;
import androidx.recyclerview.selection.SelectionTracker;
import androidx.recyclerview.widget.RecyclerView;

import com.example.edgedashanalytics.R;
import com.example.edgedashanalytics.data.video.VideoViewModel;
import com.example.edgedashanalytics.event.video.AddEvent;
import com.example.edgedashanalytics.event.video.RemoveEvent;
import com.example.edgedashanalytics.event.video.Type;
import com.example.edgedashanalytics.model.Video;
import com.example.edgedashanalytics.util.file.FileManager;
import com.example.edgedashanalytics.util.video.analysis.VideoAnalysisIntentService;
import com.example.edgedashanalytics.util.video.viewholderprocessor.VideoViewHolderProcessor;

import org.greenrobot.eventbus.EventBus;

import java.util.List;

/**
 * {@link RecyclerView.Adapter} that can display a {@link Video} and makes a call to the
 * specified {@link VideoFragment.Listener}.
 */
public class VideoRecyclerViewAdapter extends RecyclerView.Adapter<VideoRecyclerViewAdapter.VideoViewHolder> {
    private static final String TAG = VideoRecyclerViewAdapter.class.getSimpleName();
    private final VideoFragment.Listener listener;
    private final String BUTTON_ACTION_TEXT;

    private final Context context;
    private List<Video> videos;
    private SelectionTracker<Long> tracker;
    private final VideoViewHolderProcessor holderProcessor;
    private final VideoViewModel viewModel;

    VideoRecyclerViewAdapter(VideoFragment.Listener listener, Context context, String buttonText,
                             VideoViewHolderProcessor holderProcessor, VideoViewModel videoViewModel) {
        this.listener = listener;
        this.context = context;
        this.BUTTON_ACTION_TEXT = buttonText;
        this.holderProcessor = holderProcessor;
        this.viewModel = videoViewModel;
        setHasStableIds(true);
    }

    void setTracker(SelectionTracker<Long> tracker) {
        this.tracker = tracker;
    }

    void processSelected(Selection<Long> positions) {
        for (Long pos : positions) {
            Video video = videos.get(pos.intValue());
            final String output = String.format("%s/%s", getResultDirPath(),
                    FileManager.getResultNameFromVideoName(video.getName()));

            Intent analyseIntent = new Intent(context, VideoAnalysisIntentService.class);
            analyseIntent.putExtra(VideoAnalysisIntentService.VIDEO_KEY, video);
            analyseIntent.putExtra(VideoAnalysisIntentService.OUTPUT_KEY, output);
            context.startService(analyseIntent);

            EventBus.getDefault().post(new AddEvent(video, Type.PROCESSING));
            EventBus.getDefault().post(new RemoveEvent(video, Type.RAW));
        }
    }

    @NonNull
    @Override
    public VideoViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.video_list_item, parent, false);
        return new VideoViewHolder(view);
    }

    @Override
    public void onBindViewHolder(final VideoViewHolder holder, final int position) {
        holder.video = videos.get(position);
        holder.thumbnailView.setImageBitmap(getThumbnail(videos.get(position).getId()));
        holder.videoFileNameView.setText(videos.get(position).getName());
        holder.actionButton.setText(BUTTON_ACTION_TEXT);

        holderProcessor.process(context, viewModel, holder, position);

        holder.view.setOnClickListener(v -> {
            if (null != listener) {
                listener.onListFragmentInteraction(holder.video);
            }
        });

        if (tracker.isSelected(getItemId(position))) {
            holder.layout.setBackgroundResource(android.R.color.darker_gray);
        } else {
            holder.layout.setBackgroundResource(android.R.color.transparent);
        }
    }

    @Override
    public int getItemCount() {
        return videos.size();
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    private Bitmap getThumbnail(String id) {
        return MediaStore.Video.Thumbnails.getThumbnail(
                this.context.getContentResolver(),
                Integer.parseInt(id),
                MediaStore.Video.Thumbnails.MICRO_KIND,
                null);
    }

    void setVideos(List<Video> videos) {
        this.videos = videos;
        notifyDataSetChanged();
    }


    public static class VideoViewHolder extends RecyclerView.ViewHolder {
        public final View view;
        private final ImageView thumbnailView;
        private final TextView videoFileNameView;
        public final Button actionButton;
        public Video video;
        private final LinearLayout layout;

        private VideoViewHolder(View view) {
            super(view);
            this.view = view;
            thumbnailView = view.findViewById(R.id.thumbnail);
            videoFileNameView = view.findViewById(R.id.video_name);
            actionButton = view.findViewById(R.id.video_action_button);
            layout = itemView.findViewById(R.id.video_row);
        }

        public ItemDetailsLookup.ItemDetails<Long> getItemDetails() {
            return new ItemDetailsLookup.ItemDetails<Long>() {
                @Override
                public int getPosition() {
                    return getAbsoluteAdapterPosition();
                }

                @NonNull
                @Override
                public Long getSelectionKey() {
                    return getItemId();
                }
            };
        }

        @NonNull
        @Override
        public String toString() {
            return super.toString() + " '" + videoFileNameView.getText() + "'";
        }
    }
}
