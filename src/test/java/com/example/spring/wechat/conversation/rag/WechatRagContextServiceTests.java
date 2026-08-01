package com.example.spring.wechat.conversation.rag;

import com.example.spring.wechat.knowledge.model.KnowledgeSearchResult;
import com.example.spring.wechat.knowledge.service.KnowledgeSearchService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WechatRagContextServiceTests {

    private final KnowledgeSearchService searchService = mock(KnowledgeSearchService.class);
    private final RagContextFormatter formatter = new RagContextFormatter();
    private final RagQueryPlanner queryPlanner = mock(RagQueryPlanner.class);
    private final RagRerankService rerankService = new RagRerankService();
    private final RagEvidencePackBuilder evidencePackBuilder = new RagEvidencePackBuilder(formatter);

    @Test
    void retrievesKnowledgeForNormalQuestion() {
        WechatRagContextService service = service(new RagProperties(true, true, 5, 0.2, 2000, true));
        when(queryPlanner.plan("项目流程是什么")).thenReturn(List.of("项目流程是什么", "OpenClaw 项目流程"));
        when(searchService.searchByQueries("user-1", List.of("项目流程是什么", "OpenClaw 项目流程"), 10, ""))
                .thenReturn(List.of(result(0.91)));

        String context = service.build("user-1", "项目流程是什么");

        assertThat(context).contains("[知识1]", "项目流程资料");
        verify(searchService).searchByQueries("user-1", List.of("项目流程是什么", "OpenClaw 项目流程"), 10, "");
    }

    @Test
    void retrievesKnowledgeWhenQuestionMentionsToolDomainAsProjectTopic() {
        WechatRagContextService service = service(new RagProperties(true, true, 5, 0.2, 2000, true));
        when(queryPlanner.plan("项目里天气工具怎么实现")).thenReturn(List.of("项目里天气工具怎么实现"));
        when(searchService.searchByQueries("user-1", List.of("项目里天气工具怎么实现"), 10, ""))
                .thenReturn(List.of(result(0.91)));

        String context = service.build("user-1", "项目里天气工具怎么实现");

        assertThat(context).contains("[知识1]");
        verify(searchService).searchByQueries("user-1", List.of("项目里天气工具怎么实现"), 10, "");
    }

    @Test
    void usesRewriteRerankAndEvidencePackTogether() {
        WechatRagContextService service = service(new RagProperties(true, true, 2, 0.2, 2000, true));
        when(queryPlanner.plan("这个项目的 Function Calling 流程是什么？"))
                .thenReturn(List.of("Function Calling 流程", "OpenClaw Agent Loop"));
        KnowledgeSearchResult unrelated = new KnowledgeSearchResult(
                1,
                "天气工具",
                0,
                "天气查询参数。",
                "text",
                "",
                0.95);
        KnowledgeSearchResult relevant = new KnowledgeSearchResult(
                2,
                "Function Calling 流程设计",
                0,
                "WechatConversationService 构造请求后进入 Function Calling Agent Loop。",
                "text",
                "https://example.com/function-calling",
                0.72);
        when(searchService.searchByQueries(
                "user-1",
                List.of("Function Calling 流程", "OpenClaw Agent Loop"),
                4,
                ""))
                .thenReturn(List.of(unrelated, relevant));

        String context = service.build("user-1", "这个项目的 Function Calling 流程是什么？");

        assertThat(context).contains("[知识1]", "Function Calling 流程设计");
        assertThat(context.indexOf("Function Calling 流程设计"))
                .isLessThan(context.indexOf("天气工具"));
    }

    @Test
    void skipsBlankNewCommandShortAcknowledgementsAndToolIntents() {
        WechatRagContextService service = service(new RagProperties(true, true, 5, 0.2, 2000, true));

        assertThat(service.build("user-1", "")).isBlank();
        assertThat(service.build("user-1", " #new ")).isBlank();
        assertThat(service.build("user-1", "好")).isBlank();
        assertThat(service.build("user-1", "帮我查杭州天气")).isBlank();
        assertThat(service.build("user-1", "帮我生成一张猫的图片")).isBlank();
        assertThat(service.build("user-1", "发邮件给测试@example.com")).isBlank();

        verify(searchService, never()).searchByQueries(anyString(), anyList(), anyInt(), anyString());
    }

    @Test
    void returnsBlankWhenDisabled() {
        WechatRagContextService service = service(new RagProperties(false, true, 5, 0.2, 2000, true));

        assertThat(service.build("user-1", "项目流程是什么")).isBlank();

        verify(searchService, never()).searchByQueries(anyString(), anyList(), anyInt(), anyString());
    }

    @Test
    void filtersLowScoreResults() {
        WechatRagContextService service = service(new RagProperties(true, true, 5, 0.5, 2000, true));
        when(queryPlanner.plan("项目流程是什么")).thenReturn(List.of("项目流程是什么"));
        when(searchService.searchByQueries("user-1", List.of("项目流程是什么"), 10, "")).thenReturn(List.of(result(0.49)));

        assertThat(service.build("user-1", "项目流程是什么")).isBlank();
    }

    @Test
    void failsOpenWhenSearchThrows() {
        WechatRagContextService service = service(new RagProperties(true, true, 5, 0.2, 2000, true));
        when(queryPlanner.plan("项目流程是什么")).thenReturn(List.of("项目流程是什么"));
        when(searchService.searchByQueries("user-1", List.of("项目流程是什么"), 10, ""))
                .thenThrow(new IllegalStateException("qdrant down"));

        assertThat(service.build("user-1", "项目流程是什么")).isBlank();
    }

    private WechatRagContextService service(RagProperties properties) {
        return new WechatRagContextService(
                searchService,
                formatter,
                properties,
                queryPlanner,
                rerankService,
                evidencePackBuilder);
    }

    private KnowledgeSearchResult result(double score) {
        return new KnowledgeSearchResult(
                1,
                "项目流程资料",
                0,
                "OpenClaw 会先读取上下文，再调用 Function Calling Agent Loop。",
                "text",
                "https://example.com/openclaw",
                score);
    }
}
