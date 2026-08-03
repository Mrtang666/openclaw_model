package com.example.spring.wechat.context;

import java.time.Instant;

public record MemoryGraphEdge(
        long id,
        String sessionKey,
        long sourceNodeId,
        long targetNodeId,
        MemoryEdgeType edgeType,
        double weight,
        Instant createdAt) {

    public MemoryGraphEdge {
        sessionKey = sessionKey == null ? "" : sessionKey.strip();
        edgeType = edgeType == null ? MemoryEdgeType.REFERENCES : edgeType;
        weight = weight <= 0 ? 1 : weight;
    }
}
