package com.example.edgedashanalytics.model;

import android.os.Parcel;
import android.os.Parcelable;

public class Result implements Parcelable {
    private final String data;
    private final String name;

    public static final Creator<Result> CREATOR = new Creator<Result>() {
        @Override
        public Result createFromParcel(Parcel in) {
            return new Result(in);
        }

        @Override
        public Result[] newArray(int size) {
            return new Result[size];
        }
    };

    public Result(String data, String name) {
        this.data = data;
        this.name = name;
    }

    private Result(Parcel in) {
        data = in.readString();
        name = in.readString();
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(data);
        dest.writeString(name);
    }

    public String getData() {
        return data;
    }

    public String getName() {
        return name;
    }
}
