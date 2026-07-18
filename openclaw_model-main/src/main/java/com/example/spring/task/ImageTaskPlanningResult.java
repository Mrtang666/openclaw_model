package com.example.spring.task;

public record ImageTaskPlanningResult(
    boolean imageTask,
    boolean ready,
    String question,
    String finalPrompt,
    ImageTaskBrief brief) {

    public ImageTaskPlanningResult {
        question = question == null ? "" : question.trim();
        finalPrompt = finalPrompt == null ? "" : finalPrompt.trim();
        brief = brief == null ? ImageTaskBrief.empty() : brief;
    }
}
