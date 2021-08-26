package com.example.edgedashanalytics.data.result;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

public class ResultViewModelFactory implements ViewModelProvider.Factory {
    private final Application application;
    private final ResultRepository repository;

    public ResultViewModelFactory(Application application, ResultRepository repository) {
        this.application = application;
        this.repository = repository;
    }

    @NonNull
    @Override
    public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
        //noinspection unchecked
        return (T) new ResultViewModel(application, repository);
    }
}
