package com.example.edgedashanalytics.util.video.analysis;

import java.util.List;

@SuppressWarnings({"FieldCanBeLocal", "unused"})
public class Frame {
    public int frame;
    private final List<Person> people;
    private final int count;
    private final boolean overCapacity;

    Frame(int frame, List<Person> people, boolean overCapacity) {
        this.frame = frame;
        this.people = people;
        this.count = people.size();
        this.overCapacity = overCapacity;
    }
}
