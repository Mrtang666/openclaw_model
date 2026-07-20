package com.example.spring.tool.protocol;

/**
 * 承载一次用户输入的任务拆解结果，包括是否需要追问以及按顺序排列的工具任务。
 */
import java.util.List;

public record ConversationIntentDecision(
        List<ToolCall> tasks,
        boolean needsClarification,
        String clarificationQuestion) {

    public ConversationIntentDecision {
        tasks = tasks == null ? List.of() : List.copyOf(tasks);
        clarificationQuestion = clarificationQuestion == null ? "" : clarificationQuestion.strip();
    }

    public boolean hasTasks() {
        return !tasks.isEmpty();
    }

    public boolean canExecute() {
        return !needsClarification && hasTasks();
    }
}
