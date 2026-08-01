package com.example.spring.wechat.conversation.rag;

import com.example.spring.wechat.knowledge.service.KnowledgeQueryPlanner;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RagQueryPlannerTests {

    @Test
    void expandsQuestionIntoMultipleSearchQueriesWithoutDelegate() {
        RagQueryPlanner planner = new RagQueryPlanner(null);

        List<String> queries = planner.plan("这个项目的 Function Calling 流程是什么？");

        assertThat(queries).hasSizeBetween(2, 3);
        assertThat(queries.get(0)).isEqualTo("这个项目的 Function Calling 流程是什么？");
        assertThat(queries).anyMatch(value -> value.contains("Function Calling"));
        assertThat(queries).anyMatch(value -> value.contains("项目") && value.contains("流程"));
    }

    @Test
    void mergesDelegateQueriesWithRuleQueries() {
        KnowledgeQueryPlanner delegate = mock(KnowledgeQueryPlanner.class);
        when(delegate.planQueries("根据知识库讲讲 RAG 工作流")).thenReturn(List.of(
                "RAG 检索 增强 生成 工作流",
                "Retrieval Augmented Generation 流程"));
        RagQueryPlanner planner = new RagQueryPlanner(delegate);

        List<String> queries = planner.plan("根据知识库讲讲 RAG 工作流");

        assertThat(queries)
                .contains("RAG 检索 增强 生成 工作流")
                .contains("Retrieval Augmented Generation 流程")
                .anyMatch(value -> value.contains("RAG") && value.contains("工作流"));
        assertThat(queries).doesNotHaveDuplicates();
        assertThat(queries).hasSizeLessThanOrEqualTo(3);
    }

    @Test
    void returnsBlankListForBlankQuestion() {
        RagQueryPlanner planner = new RagQueryPlanner(null);

        assertThat(planner.plan("  ")).isEmpty();
    }
}
