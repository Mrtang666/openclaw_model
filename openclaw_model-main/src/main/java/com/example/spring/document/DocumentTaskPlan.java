package com.example.spring.document;

public record DocumentTaskPlan(
    DocumentTaskIntent intent,
    DocumentTaskSource source,
    DocumentOutputFormat output,
    String task,
    String title) {

    public DocumentTaskPlan {
        intent = intent == null ? DocumentTaskIntent.CHAT : intent;
        source = source == null ? DocumentTaskSource.NONE : source;
        output = output == null ? DocumentOutputFormat.NONE : output;
        task = task == null ? "" : task.trim();
        title = title == null ? "" : title.trim();
    }

    public boolean requiresSource() {
        return intent != DocumentTaskIntent.CREATE && intent != DocumentTaskIntent.CHAT;
    }
}
