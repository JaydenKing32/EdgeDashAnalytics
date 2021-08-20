package com.example.edgedashanalytics.util.video.viewholderprocessor;

import static com.example.edgedashanalytics.util.file.FileManager.getCompleteDirPath;
import static org.apache.commons.io.FilenameUtils.getBaseName;

import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.widget.Toast;

import com.example.edgedashanalytics.data.VideoViewModel;
import com.example.edgedashanalytics.event.AddEvent;
import com.example.edgedashanalytics.event.RemoveEvent;
import com.example.edgedashanalytics.event.Type;
import com.example.edgedashanalytics.model.Video;
import com.example.edgedashanalytics.page.main.VideoRecyclerViewAdapter;
import com.example.edgedashanalytics.util.video.analysis.VideoAnalysisIntentService;

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
            final String output = String.format("%s/%s.json", getCompleteDirPath(), getBaseName(video.getName()));

            Intent analyseIntent = new Intent(context, VideoAnalysisIntentService.class);
            analyseIntent.putExtra(VideoAnalysisIntentService.VIDEO_KEY, video);
            analyseIntent.putExtra(VideoAnalysisIntentService.OUTPUT_KEY, output);
            context.startService(analyseIntent);

            EventBus.getDefault().post(new AddEvent(video, Type.PROCESSING));
            EventBus.getDefault().post(new RemoveEvent(video, Type.RAW));

            Toast.makeText(context, "Add to processing queue", Toast.LENGTH_SHORT).show();
        });
    }
}
