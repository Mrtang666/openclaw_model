package com.example.spring.wechat.context;

import java.util.List;

public interface MemoryGraphRepository {

    MemoryGraphNode createNode(MemoryGraphNodeDraft draft);

    MemoryGraphEdge createEdge(MemoryGraphEdgeDraft draft);

    List<MemoryGraphNode> findRecentNodes(String sessionKey, MemoryNodeType nodeType, int limit);

    List<MemoryGraphNode> findRecentNodesByTopic(String sessionKey, MemoryNodeType nodeType, String topicKey, int limit);

    List<MemoryGraphEdge> findOutgoingEdges(long sourceNodeId, MemoryEdgeType edgeType);

    void softDeleteNode(long nodeId);
}
