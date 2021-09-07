package com.example.edgedashanalytics.model;

import android.os.Parcel;

import androidx.annotation.NonNull;

public class Result extends Content {
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
        super(data, name);
    }

    private Result(Parcel in) {
        super(in.readString(), in.readString());
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @NonNull
    @Override
    public String toString() {
        return "Result{" +
                "data='" + data + '\'' +
                ", name='" + name + '\'' +
                '}';
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(data);
        dest.writeString(name);
    }
}
