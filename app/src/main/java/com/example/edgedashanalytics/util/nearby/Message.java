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
        SEGMENT, // Analyse the transferred file as a video segment
        COMPLETE, // Completed file transfer
        RETURN, // Returning results file
        HW_INFO, // Message contains hardware information
        HW_INFO_REQUEST, // Requesting hardware information
        ADJ_ESD // Adjust ESD
    }

    static boolean isAnalyse(Command command) {
        return (command.equals(Command.ANALYSE) || command.equals(Command.SEGMENT));
    }
}
