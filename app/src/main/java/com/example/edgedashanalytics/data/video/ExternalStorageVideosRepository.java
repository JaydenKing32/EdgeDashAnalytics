package com.example.edgedashanalytics.data.video;

import android.content.Context;
import android.util.Log;

import androidx.lifecycle.MutableLiveData;

import com.example.edgedashanalytics.model.Video;
import com.example.edgedashanalytics.util.video.VideoManager;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class ExternalStorageVideosRepository implements VideosRepository {
    private final static String TAG = ExternalStorageVideosRepository.class.getSimpleName();

    private Context context;
    private File videoDirectory;
    private List<Video> videos = new ArrayList<>();
    private final MutableLiveData<List<Video>> result = new MutableLiveData<>();


    public ExternalStorageVideosRepository(Context context, String path) {
        if (path == null) {
            Log.w(TAG, "Null path");
            return;
        }

        this.context = context;
        this.videoDirectory = new File(path);
        Log.v(TAG, String.format("Created repo: %s", videoDirectory.getAbsolutePath()));
    }

    @Override
    public MutableLiveData<List<Video>> getVideos() {
        videos = VideoManager.getAllVideoFromExternalStorageFolder(context.getApplicationContext(), videoDirectory);
        videos.sort(Comparator.comparing(Video::getName));
        result.setValue(videos);
        return result;
    }

    @Override
    public void insert(Video video) {
        videos.add(video);
        result.postValue(videos);
    }

    @Override
    public void delete(int position) {
        videos.remove(position);
        result.postValue(videos);
    }

    @Override
    public void delete(String path) {
        videos = videos.stream().filter(e -> !e.getData().equalsIgnoreCase(path)).collect(Collectors.toList());
        result.postValue(videos);
    }

    @Override
    public void update(Video video, int position) {
        videos.set(position, video);
        result.postValue(videos);
    }
}
