package com.example.edgedashanalytics.data.result;

import android.util.Log;

import androidx.lifecycle.MutableLiveData;

import com.example.edgedashanalytics.model.Result;
import com.example.edgedashanalytics.util.file.FileManager;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class ResultRepository {
    private final static String TAG = ResultRepository.class.getSimpleName();

    private List<Result> results = new ArrayList<>();
    private final MutableLiveData<List<Result>> liveData = new MutableLiveData<>();

    public ResultRepository() {
    }

    public MutableLiveData<List<Result>> getResults() {
        results = retrieveResults();
        results.sort(Comparator.comparing(Result::getName));
        liveData.setValue(results);
        return liveData;
    }

    private List<Result> retrieveResults() {
        List<Result> results = new ArrayList<>();
        File resultDir = new File(FileManager.getResultDirPath());
        File[] resultFiles = resultDir.listFiles();

        if (resultFiles == null) {
            Log.e(TAG, "Could not retrieve result files");
            return results;
        }

        for (File resultFile : resultFiles) {
            Result result = new Result(resultFile.getAbsolutePath(), resultFile.getName());
            results.add(result);
        }
        return results;
    }

    public void insert(Result result) {
        results.add(result);
        liveData.postValue(results);
    }

    public void delete(int position) {
        results.remove(position);
        liveData.postValue(results);
    }

    public void delete(String path) {
        results = results.stream().filter(e -> !e.getData().equalsIgnoreCase(path)).collect(Collectors.toList());
        liveData.postValue(results);
    }

    public void update(Result result, int position) {
        results.set(position, result);
        liveData.postValue(results);
    }
}
