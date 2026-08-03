package com.example.spring.wechat.context;

public record ContextBudgetReport(
        int modelWindowTokens,
        int outputReserveTokens,
        int toolLoopReserveTokens,
        int availableInputTokens,
        int contextBudgetTokens,
        int actualTokens,
        boolean compressed) {
}
