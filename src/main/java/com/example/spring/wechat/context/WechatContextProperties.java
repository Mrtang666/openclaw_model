package com.example.spring.wechat.context;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "wechat.agent.context")
public record WechatContextProperties(
        boolean memoryGraphEnabled,
        boolean relevanceClassifierEnabled,
        boolean longTermMemoryIngestionEnabled,
        int strongRecentTurns,
        int weakRecentTurns,
        int minRecentTurns,
        int summaryWindowSize,
        int summaryOverlapTurns,
        int modelWindowTokens,
        int outputReserveTokens,
        int toolLoopReserveTokens,
        double maxInputRatio) {

    public WechatContextProperties {
        strongRecentTurns = strongRecentTurns <= 0 ? 5 : strongRecentTurns;
        weakRecentTurns = weakRecentTurns <= 0 ? 1 : weakRecentTurns;
        minRecentTurns = minRecentTurns <= 0 ? 2 : minRecentTurns;
        summaryWindowSize = summaryWindowSize <= 0 ? 5 : summaryWindowSize;
        summaryOverlapTurns = summaryOverlapTurns < 0 ? 1 : summaryOverlapTurns;
        if (summaryOverlapTurns >= summaryWindowSize) {
            summaryOverlapTurns = Math.max(0, summaryWindowSize - 1);
        }
        modelWindowTokens = modelWindowTokens <= 0 ? 128_000 : modelWindowTokens;
        outputReserveTokens = outputReserveTokens <= 0 ? 8_000 : outputReserveTokens;
        toolLoopReserveTokens = toolLoopReserveTokens <= 0 ? 12_000 : toolLoopReserveTokens;
        maxInputRatio = maxInputRatio <= 0 || maxInputRatio > 1 ? 0.8 : maxInputRatio;
    }
}
