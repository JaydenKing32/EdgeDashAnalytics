package com.example.edgedashanalytics.event.video;

public class RemoveByNameEvent {
    public final String name;
    public final Type type;

    public RemoveByNameEvent(String name, Type type) {
        this.name = name;
        this.type = type;
    }
}
