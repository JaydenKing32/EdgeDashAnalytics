package com.example.edgedashanalytics.page.adapter;

import static com.example.edgedashanalytics.page.main.MainActivity.I_TAG;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.selection.ItemDetailsLookup;
import androidx.recyclerview.selection.Selection;
import androidx.recyclerview.selection.SelectionTracker;
import androidx.recyclerview.widget.RecyclerView;

import com.example.edgedashanalytics.R;
import com.example.edgedashanalytics.model.Video;
import com.example.edgedashanalytics.page.main.VideoFragment;
import com.example.edgedashanalytics.util.TimeManager;
import com.example.edgedashanalytics.util.dashcam.DashCam;
import com.example.edgedashanalytics.util.video.analysis.AnalysisTools;

import org.apache.commons.lang3.time.DurationFormatUtils;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * {@link RecyclerView.Adapter} that can display a {@link Video} and makes a call to the
 * specified {@link VideoFragment.Listener}.
 */
public abstract class VideoRecyclerViewAdapter extends RecyclerView.Adapter<VideoRecyclerViewAdapter.VideoViewHolder> {
    private static final String TAG = VideoRecyclerViewAdapter.class.getSimpleName();

    List<Video> videos;
    SelectionTracker<Long> tracker;
    final VideoFragment.Listener listener;

    VideoRecyclerViewAdapter(VideoFragment.Listener listener) {
        this.listener = listener;
        setHasStableIds(true);
    }

    public void setTracker(SelectionTracker<Long> tracker) {
        this.tracker = tracker;
    }

    public void processSelected(Selection<Long> positions, Context c) {
        SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(c);
        boolean simDownload = pref.getBoolean(c.getString(R.string.enable_download_simulation_key), false);
        String defaultDelay = "1000";
        int downloadDelay = Integer.parseInt(pref.getString(c.getString(R.string.simulation_delay_key), defaultDelay));

        if (simDownload) {
            Log.d(I_TAG, String.format("Starting simulated download with delay: %s", downloadDelay));
            Thread transferDelayThread = new Thread(processSelectedDelay(positions, downloadDelay, c));
            transferDelayThread.start();
        } else {
            processSelectedNow(positions, c);
        }
    }

    private void processSelectedNow(Selection<Long> positions, Context context) {
        if (listener.getIsConnected()) {
            for (Long pos : positions) {
                Video video = videos.get(pos.intValue());
                listener.getAddVideo(video);
            }
            listener.getNextTransfer();
        } else {
            for (Long pos : positions) {
                Video video = videos.get(pos.intValue());
                AnalysisTools.processVideo(video, context);
            }
        }
    }

    private Runnable processSelectedDelay(Selection<Long> positions, int delay, Context context) {
        // Not safe to concurrently modify recycler view list, better to copy videos first
        ArrayList<Video> selectedVideos = new ArrayList<>(positions.size());
        positions.iterator().forEachRemaining(p -> selectedVideos.add(videos.get(p.intValue())));

        return () -> {
            for (Video video : selectedVideos) {
                if (listener.getIsConnected()) {
                    listener.getAddVideo(video);
                    listener.getNextTransfer();
                } else {
                    AnalysisTools.processVideo(video, context);
                }

                try {
                    Thread.sleep(delay);
                } catch (InterruptedException e) {
                    Log.e(I_TAG, String.format("Thread error: \n%s", e.getMessage()));
                }
            }
        };
    }

    public Runnable simulateDownloads(int delay, Consumer<Video> downloadCallback, boolean dualDownload) {
        LinkedList<Video> videoList = videos.stream()
                .sorted((v1, v2) -> DashCam.testVideoComparator(v1.getName(), v2.getName()))
                .limit(DashCam.getTestVideoCount())
                .collect(Collectors.toCollection(LinkedList::new));
        int downloadCount = dualDownload ? 2 : 1;

        return () -> {
            ArrayList<Video> toDownload = new ArrayList<>(2);

            for (int i = 0; i < downloadCount; i++) {
                if (videoList.isEmpty()) {
                    downloadCallback.accept(null);
                    return;
                }
                Video video = videoList.pop();
                toDownload.add(video);
                TimeManager.addStartTime(video.getName());
            }

            // Assumes concurrent downloading, video pairs share a single delay and will complete at the same time
            ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
            executor.schedule(() -> toDownload.forEach(v -> {
                String time = DurationFormatUtils.formatDuration(delay, "ss.SSS");
                Log.i(I_TAG, String.format("Successfully downloaded %s in %ss, 0nW consumed", v.getName(), time));
                downloadCallback.accept(v);
            }), delay, TimeUnit.MILLISECONDS);
            executor.shutdown();
        };
    }

    @NonNull
    @Override
    public VideoViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.video_list_item, parent, false);
        return new VideoViewHolder(view);
    }

    @Override
    public int getItemCount() {
        return videos.size();
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    public void setVideos(List<Video> videos) {
        this.videos = videos;
        notifyDataSetChanged();
    }


    public static class VideoViewHolder extends RecyclerView.ViewHolder {
        final TextView videoFileNameView;
        final Button actionButton;
        Video video;
        final LinearLayout layout;

        private VideoViewHolder(View view) {
            super(view);
            videoFileNameView = view.findViewById(R.id.video_name);
            actionButton = view.findViewById(R.id.video_action_button);
            layout = itemView.findViewById(R.id.video_row);
        }

        public ItemDetailsLookup.ItemDetails<Long> getItemDetails() {
            return new ItemDetailsLookup.ItemDetails<>() {
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
