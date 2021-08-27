package com.example.edgedashanalytics.event.result;

import com.example.edgedashanalytics.model.Result;

public class ResultEvent {
    public final Result result;

    ResultEvent(Result result) {
        this.result = result;
    }
}
