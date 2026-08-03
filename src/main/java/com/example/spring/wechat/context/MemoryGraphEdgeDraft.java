package com.example.spring.wechat.context;

public record MemoryGraphEdgeDraft(
        String sessionKey,
        long sourceNodeId,
        long targetNodeId,
        MemoryEdgeType edgeType,
        double weight) {

    public MemoryGraphEdgeDraft {
        sessionKey = sessionKey == null ? "" : sessionKey.strip();
        edgeType = edgeType == null ? MemoryEdgeType.REFERENCES : edgeType;
        weight = weight <= 0 ? 1 : weight;
    }
}
