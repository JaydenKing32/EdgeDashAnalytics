package com.example.edgedashanalytics.event.result;

import com.example.edgedashanalytics.model.Result;

public class AddResultEvent extends ResultEvent {
    public AddResultEvent(Result result) {
        super(result);
    }
}
