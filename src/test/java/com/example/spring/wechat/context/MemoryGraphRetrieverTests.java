package com.example.spring.wechat.context;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MemoryGraphRetrieverTests {

    @Test
    void retrievesStrongTopicExtractsByTopic() {
        MemoryGraphRepository repository = mock(MemoryGraphRepository.class);
        MemoryGraphNode node = node(MemoryNodeType.ACTIVE_EXTRACT, "topic-a", "活摘内容");
        when(repository.findRecentNodesByTopic("session", MemoryNodeType.ACTIVE_EXTRACT, "topic-a", 5))
                .thenReturn(List.of(node));

        MemoryGraphRetriever retriever = new MemoryGraphRetriever(repository);

        assertThat(retriever.activeExtracts("session", "topic-a", 5))
                .extracting(MemoryGraphNode::content)
                .containsExactly("活摘内容");
    }

    private MemoryGraphNode node(MemoryNodeType type, String topic, String content) {
        return new MemoryGraphNode(1, "session", 1L, type, topic, topic, content, "",
                1, 1, 1, null, null, "", "", "", null, null, null, false);
    }
}
