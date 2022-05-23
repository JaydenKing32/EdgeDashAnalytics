package com.example.edgedashanalytics.data.result;

import android.app.Application;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.ViewModel;

import com.example.edgedashanalytics.model.Result;

import java.util.List;

public class ResultViewModel extends ViewModel {
    private final ResultRepository repository;
    private final LiveData<List<Result>> liveData;

    ResultViewModel(Application application, ResultRepository resultRepository) {
        super();
        repository = resultRepository;
        liveData = repository.getResults();
    }

    public LiveData<List<Result>> getResults() {
        return liveData;
    }

    public void insert(Result result) {
        repository.insert(result);
    }

    public void remove(int position) {
        repository.delete(position);
    }

    public void update(Result result, int position) {
        repository.update(result, position);
    }
}
