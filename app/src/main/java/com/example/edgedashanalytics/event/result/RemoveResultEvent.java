package com.example.edgedashanalytics.event.result;

import com.example.edgedashanalytics.model.Result;

public class RemoveResultEvent extends ResultEvent {
    public RemoveResultEvent(Result result) {
        super(result);
    }
}
