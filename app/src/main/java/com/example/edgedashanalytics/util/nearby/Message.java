package com.example.edgedashanalytics.util.nearby;

import com.example.edgedashanalytics.model.Video;

public class Message {
    final Video video;
    final Command command;

    Message(Video video, Command command) {
        this.video = video;
        this.command = command;
    }

    public enum Command {
        ERROR, // Error during transfer
        ANALYSE, // Analyse the transferred file
        COMPLETE, // Completed file transfer
        RETURN // Returning results file
    }
}
