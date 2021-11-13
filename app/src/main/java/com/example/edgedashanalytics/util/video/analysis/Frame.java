package com.example.edgedashanalytics.util.video.analysis;

import java.util.List;

@SuppressWarnings({"FieldCanBeLocal", "unused"})
public class Frame {
    public int frame;
    private final List<Person> people;

    Frame(int frame, List<Person> people) {
        this.frame = frame;
        this.people = people;
    }
}
