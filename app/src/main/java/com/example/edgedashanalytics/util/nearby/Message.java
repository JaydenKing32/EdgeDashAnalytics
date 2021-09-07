package com.example.edgedashanalytics.util.nearby;

import com.example.edgedashanalytics.model.Content;

class Message {
    final Content content;
    final Command command;

    Message(Content content, Command command) {
        this.content = content;
        this.command = command;
    }

    public enum Command {
        ERROR, // Error during transfer
        ANALYSE, // Analyse the transferred file
        COMPLETE, // Completed file transfer
        RETURN // Returning results file
    }
}
