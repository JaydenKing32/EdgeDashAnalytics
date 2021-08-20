package com.example.edgedashanalytics.model;

import android.content.ContentValues;
import android.content.Context;
import android.os.Build;
import android.os.Parcel;
import android.os.Parcelable;
import android.provider.MediaStore;

import androidx.annotation.NonNull;

import java.math.BigInteger;

public class Video implements Parcelable {
    private final String id;
    private final String data;
    private final String name;
    private final BigInteger size;
    private final boolean visible;
    private final String mimeType;

    public static final Creator<Video> CREATOR = new Creator<Video>() {
        @Override
        public Video createFromParcel(Parcel parcel) {
            return new Video(parcel);
        }

        @Override
        public Video[] newArray(int i) {
            return new Video[i];
        }

    };

    public Video(String id, String name, String data, String mimeType, BigInteger size) {
        this.id = id;
        this.data = data;
        this.name = name;
        this.size = size;
        this.mimeType = mimeType;
        this.visible = true;
    }

    public Video(String id, String name, String data, String mimeType, BigInteger size, boolean visible) {
        this.id = id;
        this.data = data;
        this.name = name;
        this.size = size;
        this.mimeType = mimeType;
        this.visible = visible;
    }

    public Video(Video video) {
        this.id = video.id;
        this.data = video.data;
        this.name = video.name;
        this.size = video.size;
        this.mimeType = video.mimeType;
        this.visible = video.visible;
    }

    public Video(Video video, boolean visible) {
        this.id = video.id;
        this.data = video.data;
        this.name = video.name;
        this.size = video.size;
        this.mimeType = video.mimeType;
        this.visible = visible;
    }

    private Video(Parcel in) {
        this.id = in.readString();
        this.data = in.readString();
        this.name = in.readString();
        String size = in.readString();
        if (size != null) {
            this.size = new BigInteger(size);
        } else {
            this.size = new BigInteger("-1");
        }
        this.mimeType = in.readString();
        this.visible = in.readByte() != 0;
    }

    public String getId() {
        return id;
    }

    public String getData() {
        return data;
    }

    public String getName() {
        return name;
    }

    public BigInteger getSize() {
        return size;
    }

    public boolean isVisible() {
        return visible;
    }

    public String getMimeType() {
        return mimeType;
    }

    @NonNull
    @Override
    public String toString() {
        return "Video{" +
                "id='" + id + '\'' +
                ", data='" + data + '\'' +
                ", name='" + name + '\'' +
                ", size=" + size +
                ", visible=" + visible +
                ", mimeType='" + mimeType + '\'' +
                '}';
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel parcel, int i) {
        parcel.writeString(id);
        parcel.writeString(data);
        parcel.writeString(name);
        parcel.writeString(size.toString());
        parcel.writeString(mimeType);
        parcel.writeByte(visible ? (byte) 1 : 0);
    }

    // Insert a new video's values into the MediaStore using an existing video as a basis
    public void insertMediaValues(Context context, String path) {
        ContentValues values = new ContentValues();
        values.put(MediaStore.Video.Media.TITLE, name);
        values.put(MediaStore.Video.Media.MIME_TYPE, mimeType);
        values.put(MediaStore.Video.Media.DISPLAY_NAME, "player");
        values.put(MediaStore.Video.Media.DESCRIPTION, "");
        values.put(MediaStore.Video.Media.DATE_ADDED, System.currentTimeMillis());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.put(MediaStore.Video.Media.DATE_TAKEN, System.currentTimeMillis());
        }
        values.put(MediaStore.Video.Media.DATA, path);
        context.getContentResolver().insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values);
    }
}
