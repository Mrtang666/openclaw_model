package com.example.spring.speech;

public record ReadAloudRequest(boolean requested, String targetText, String errorReply) {
    public static ReadAloudRequest ignored() {
        return new ReadAloudRequest(false, "", "");
    }

    public static ReadAloudRequest resolved(String targetText) {
        return new ReadAloudRequest(true, targetText, "");
    }

    public static ReadAloudRequest unresolved(String errorReply) {
        return new ReadAloudRequest(true, "", errorReply);
    }
}
