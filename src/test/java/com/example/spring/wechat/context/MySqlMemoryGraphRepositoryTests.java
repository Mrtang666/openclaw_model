package com.example.spring.wechat.context;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(args = "/status")
@ActiveProfiles("test")
class MySqlMemoryGraphRepositoryTests {

    @Autowired
    private MemoryGraphRepository repository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void clean() {
        assertUsingTestDatabase();
        jdbcTemplate.update("DELETE FROM memory_graph_edges");
        jdbcTemplate.update("DELETE FROM memory_graph_nodes");
    }

    @Test
    void createsNodesEdgesAndQueriesByType() {
        MemoryGraphNode summary = repository.createNode(new MemoryGraphNodeDraft(
                "session-1",
                42L,
                MemoryNodeType.CONVERSATION_SUMMARY,
                "memory-graph",
                "摘要",
                "第 1-6 轮摘要",
                "摘要",
                0.8,
                0.7,
                0.9,
                1L,
                12L,
                "conversation",
                "conversation://42",
                "memory_type:conversation_summary",
                null));
        MemoryGraphNode extract = repository.createNode(new MemoryGraphNodeDraft(
                "session-1",
                42L,
                MemoryNodeType.ACTIVE_EXTRACT,
                "memory-graph",
                "活摘",
                "和当前主题相关的历史重点",
                "活摘",
                0.9,
                0.95,
                0.9,
                1L,
                12L,
                "conversation_summary",
                "memory://summary/" + summary.id(),
                "memory_type:active_extract",
                null));

        repository.createEdge(new MemoryGraphEdgeDraft(
                "session-1",
                extract.id(),
                summary.id(),
                MemoryEdgeType.DERIVED_FROM,
                1));

        List<MemoryGraphNode> extracts = repository.findRecentNodes(
                "session-1",
                MemoryNodeType.ACTIVE_EXTRACT,
                5);
        List<MemoryGraphEdge> edges = repository.findOutgoingEdges(extract.id(), MemoryEdgeType.DERIVED_FROM);

        assertThat(extracts).extracting(MemoryGraphNode::content)
                .containsExactly("和当前主题相关的历史重点");
        assertThat(edges).hasSize(1);
        assertThat(edges.get(0).targetNodeId()).isEqualTo(summary.id());
    }

    @Test
    void softDeletedNodesAreExcludedFromRecentQueries() {
        MemoryGraphNode node = repository.createNode(new MemoryGraphNodeDraft(
                "session-2",
                null,
                MemoryNodeType.CONVERSATION_TOPIC,
                "memory-graph",
                "Memory Graph",
                "用户聊过 Memory Graph 上下文机制",
                "Memory Graph",
                0.5,
                0.5,
                0.9,
                null,
                null,
                "topic",
                "topic://memory-graph",
                "memory_type:topic",
                null));

        repository.softDeleteNode(node.id());

        assertThat(repository.findRecentNodes("session-2", MemoryNodeType.CONVERSATION_TOPIC, 10))
                .isEmpty();
    }

    private void assertUsingTestDatabase() {
        String database = jdbcTemplate.queryForObject("SELECT DATABASE()", String.class);
        if (!"openclaw_test".equals(database)) {
            throw new IllegalStateException("测试禁止清理非 openclaw_test 数据库，当前数据库：" + database);
        }
    }
}
