package com.example.spring.wechat.bot.multiclient;

import java.util.List;

public record ClawBotManagerSnapshot(
        int connectedCount,
        int pendingCount,
        int totalConnections,
        int maxConnections,
        int maxPendingLogins,
        int workerThreads,
        int modelMaxConcurrency,
        int activeTasks,
        int queuedTasks,
        List<ClawBotConnectionSnapshot> connections) {

    public ClawBotManagerSnapshot {
        connections = connections == null ? List.of() : List.copyOf(connections);
    }
}
