package com.example.edgedashanalytics.data.video;

import androidx.lifecycle.LiveData;

import com.example.edgedashanalytics.model.Video;

import java.util.List;

public interface VideosRepository {
    LiveData<List<Video>> getVideos();

    void insert(Video video);

    void delete(int position);

    void delete(String path);

    void update(Video video, int position);
}
