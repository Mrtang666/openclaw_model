package com.example.spring.wechat.conversation.rag;

import com.example.spring.wechat.knowledge.model.KnowledgeSearchResult;
import com.example.spring.wechat.knowledge.service.KnowledgeSearchService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WechatRagContextServiceTests {

    private final KnowledgeSearchService searchService = mock(KnowledgeSearchService.class);
    private final RagContextFormatter formatter = new RagContextFormatter();

    @Test
    void retrievesKnowledgeForNormalQuestion() {
        WechatRagContextService service = service(new RagProperties(true, true, 5, 0.2, 2000, true));
        when(searchService.search("user-1", "项目流程是什么", 5, "")).thenReturn(List.of(result(0.91)));

        String context = service.build("user-1", "项目流程是什么");

        assertThat(context).contains("[知识1]", "项目流程资料");
        verify(searchService).search("user-1", "项目流程是什么", 5, "");
    }

    @Test
    void retrievesKnowledgeWhenQuestionMentionsToolDomainAsProjectTopic() {
        WechatRagContextService service = service(new RagProperties(true, true, 5, 0.2, 2000, true));
        when(searchService.search("user-1", "项目里天气工具怎么实现", 5, "")).thenReturn(List.of(result(0.91)));

        String context = service.build("user-1", "项目里天气工具怎么实现");

        assertThat(context).contains("[知识1]");
        verify(searchService).search("user-1", "项目里天气工具怎么实现", 5, "");
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

        verify(searchService, never()).search(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void returnsBlankWhenDisabled() {
        WechatRagContextService service = service(new RagProperties(false, true, 5, 0.2, 2000, true));

        assertThat(service.build("user-1", "项目流程是什么")).isBlank();

        verify(searchService, never()).search(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void filtersLowScoreResults() {
        WechatRagContextService service = service(new RagProperties(true, true, 5, 0.5, 2000, true));
        when(searchService.search("user-1", "项目流程是什么", 5, "")).thenReturn(List.of(result(0.49)));

        assertThat(service.build("user-1", "项目流程是什么")).isBlank();
    }

    @Test
    void failsOpenWhenSearchThrows() {
        WechatRagContextService service = service(new RagProperties(true, true, 5, 0.2, 2000, true));
        when(searchService.search("user-1", "项目流程是什么", 5, ""))
                .thenThrow(new IllegalStateException("qdrant down"));

        assertThat(service.build("user-1", "项目流程是什么")).isBlank();
    }

    private WechatRagContextService service(RagProperties properties) {
        return new WechatRagContextService(searchService, formatter, properties);
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
