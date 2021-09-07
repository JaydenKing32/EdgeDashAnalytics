package com.example.edgedashanalytics.model;

import android.os.Parcelable;

public abstract class Content implements Parcelable {
    final String data;
    final String name;

    public Content(String data, String name) {
        this.data = data;
        this.name = name;
    }

    public String getData() {
        return data;
    }

    public String getName() {
        return name;
    }
}
