package com.example.spring.wechat.knowledge.service;

import com.example.spring.wechat.knowledge.config.KnowledgeProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class KnowledgeChunkServiceTests {

    @Test
    void splitsLongTextIntoOrderedChunksWithOverlap() {
        KnowledgeChunkService service = new KnowledgeChunkService(new KnowledgeProperties(20, 5, 5, 6000, 0.2));

        var chunks = service.split("第一段内容很长很长。\n\n第二段内容也很长很长，需要继续切分。");

        assertThat(chunks).hasSizeGreaterThan(1);
        assertThat(chunks.get(0).index()).isZero();
        assertThat(chunks.get(1).index()).isEqualTo(1);
        assertThat(chunks)
                .allSatisfy(chunk -> assertThat(chunk.content()).isNotBlank());
    }
}
