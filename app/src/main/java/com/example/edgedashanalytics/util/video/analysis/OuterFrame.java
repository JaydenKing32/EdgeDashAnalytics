package com.example.edgedashanalytics.util.video.analysis;

import java.util.List;

@SuppressWarnings({"FieldCanBeLocal", "unused"})
class OuterFrame extends Frame {
    private final List<Person> people;
    private final int count;

    OuterFrame(int frame, List<Person> people) {
        this.frame = frame;
        this.people = people;
        this.count = people.size();
    }
}
