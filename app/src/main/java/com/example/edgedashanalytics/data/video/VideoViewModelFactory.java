package com.example.edgedashanalytics.data.video;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

public class VideoViewModelFactory implements ViewModelProvider.Factory {
    private final Application application;
    private final VideosRepository repository;

    public VideoViewModelFactory(Application application, VideosRepository repository) {
        this.application = application;
        this.repository = repository;
    }

    @NonNull
    @Override
    public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
        //noinspection unchecked
        return (T) new VideoViewModel(application, repository);
    }
}
