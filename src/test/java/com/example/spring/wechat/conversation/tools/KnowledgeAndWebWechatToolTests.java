package com.example.spring.wechat.conversation.tools;

import com.example.spring.wechat.knowledge.model.KnowledgeDocument;
import com.example.spring.wechat.knowledge.model.KnowledgeIngestionResult;
import com.example.spring.wechat.knowledge.model.KnowledgeSearchResult;
import com.example.spring.wechat.knowledge.service.KnowledgeIngestionService;
import com.example.spring.wechat.knowledge.service.KnowledgeManageService;
import com.example.spring.wechat.knowledge.service.KnowledgeSearchService;
import com.example.spring.wechat.web.model.WebPageContent;
import com.example.spring.wechat.web.model.WebSearchResult;
import com.example.spring.wechat.web.context.WebResourceContextService;
import com.example.spring.wechat.web.service.WebReadService;
import com.example.spring.wechat.web.service.WebSearchService;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class KnowledgeAndWebWechatToolTests {

    @Test
    void knowledgeQueryToolFormatsSearchResultsForModel() {
        KnowledgeSearchService searchService = mock(KnowledgeSearchService.class);
        when(searchService.search("user-1", "项目流程", 3, "")).thenReturn(List.of(
                new KnowledgeSearchResult(1, "项目说明", 0, "Function Calling 调用工具中心。", "text", "", 0.91)));
        KnowledgeQueryWechatTool tool = new KnowledgeQueryWechatTool(searchService);

        var reply = tool.execute(new WechatToolRequest(
                "user-1",
                "根据知识库讲项目流程",
                Map.of("question", "项目流程", "top_k", "3"),
                "",
                null,
                null));

        assertThat(reply.text()).contains("项目说明", "Function Calling", "相关知识片段");
    }

    @Test
    void webReadToolCanSavePageToKnowledgeBase() {
        WebReadService webReadService = mock(WebReadService.class);
        KnowledgeIngestionService ingestionService = mock(KnowledgeIngestionService.class);
        when(webReadService.read("https://example.com/a")).thenReturn(new WebPageContent(
                "https://example.com/a",
                "测试网页",
                "网页正文",
                Instant.parse("2026-07-23T00:00:00Z")));
        when(ingestionService.add("user-1", "测试网页", "网页正文", "web", "https://example.com/a", "web"))
                .thenReturn(new KnowledgeIngestionResult(7, "测试网页", 1, false));
        WebReadWechatTool tool = new WebReadWechatTool(webReadService, ingestionService, new WebResourceContextService());

        var reply = tool.execute(new WechatToolRequest(
                "user-1",
                "保存这个网页",
                Map.of("url", "https://example.com/a", "save_to_knowledge", "true"),
                "",
                null,
                null));

        assertThat(reply.text()).contains("测试网页", "已保存到知识库", "https://example.com/a");
    }

    @Test
    void webReadToolAutomaticallySavesWhenUserAsksToRememberPage() {
        WebReadService webReadService = mock(WebReadService.class);
        KnowledgeIngestionService ingestionService = mock(KnowledgeIngestionService.class);
        when(webReadService.read("https://example.com/remember")).thenReturn(new WebPageContent(
                "https://example.com/remember",
                "需要记住的网页",
                "这是一篇需要后续参考的网页正文",
                Instant.parse("2026-07-23T00:00:00Z")));
        when(ingestionService.add(
                "user-1",
                "需要记住的网页",
                "这是一篇需要后续参考的网页正文",
                "web",
                "https://example.com/remember",
                "web"))
                .thenReturn(new KnowledgeIngestionResult(8, "需要记住的网页", 1, false));
        WebReadWechatTool tool = new WebReadWechatTool(webReadService, ingestionService, new WebResourceContextService());

        var reply = tool.execute(new WechatToolRequest(
                "user-1",
                "阅读这个链接并记住，之后回答时参考它",
                Map.of("url", "https://example.com/remember"),
                "",
                null,
                null));

        assertThat(reply.text()).contains("已保存到知识库", "document_id=8");
        verify(ingestionService).add(
                "user-1",
                "需要记住的网页",
                "这是一篇需要后续参考的网页正文",
                "web",
                "https://example.com/remember",
                "web");
    }

    @Test
    void webReadToolDoesNotSavePageWhenUserOnlyAsksToSummarize() {
        WebReadService webReadService = mock(WebReadService.class);
        KnowledgeIngestionService ingestionService = mock(KnowledgeIngestionService.class);
        when(webReadService.read("https://example.com/summary")).thenReturn(new WebPageContent(
                "https://example.com/summary",
                "只总结的网页",
                "只需要本轮总结的网页正文",
                Instant.parse("2026-07-23T00:00:00Z")));
        WebReadWechatTool tool = new WebReadWechatTool(webReadService, ingestionService, new WebResourceContextService());

        var reply = tool.execute(new WechatToolRequest(
                "user-1",
                "帮我总结这个网页",
                Map.of("url", "https://example.com/summary"),
                "",
                null,
                null));

        assertThat(reply.text()).contains("只总结的网页", "正文片段");
        assertThat(reply.text()).doesNotContain("已保存到知识库");
        verifyNoInteractions(ingestionService);
    }

    @Test
    void webSearchToolReturnsConfiguredSearchResults() {
        WebSearchService service = mock(WebSearchService.class);
        KnowledgeIngestionService ingestionService = mock(KnowledgeIngestionService.class);
        when(service.search("Qdrant Java", 2, "any", "zh-CN")).thenReturn(List.of(
                new WebSearchResult("Qdrant Java Client", "https://qdrant.tech", "官方 Java 客户端", "Qdrant", "")));
        WebResourceContextService resourceContextService = new WebResourceContextService();
        WebSearchWechatTool tool = new WebSearchWechatTool(service, ingestionService, resourceContextService);

        var reply = tool.execute(new WechatToolRequest(
                "user-1",
                "搜索 Qdrant Java",
                Map.of("query", "Qdrant Java", "limit", "2"),
                "",
                null,
                null));

        assertThat(reply.text()).contains("参考来源", "[来源1]", "Qdrant Java Client", "https://qdrant.tech", "官方 Java 客户端");
        assertThat(resourceContextService.resolveUrl("user-1", "第一个网页详细看看")).contains("https://qdrant.tech");
        verifyNoInteractions(ingestionService);
    }

    @Test
    void webSearchToolSavesOnlySearchSummaryWhenUserAsksToRememberResults() {
        WebSearchService service = mock(WebSearchService.class);
        KnowledgeIngestionService ingestionService = mock(KnowledgeIngestionService.class);
        when(service.search("Qdrant Java", 2, "any", "zh-CN")).thenReturn(List.of(
                new WebSearchResult("Qdrant Java Client", "https://qdrant.tech", "官方 Java 客户端", "Qdrant", ""),
                new WebSearchResult("Qdrant Spring", "https://example.com/qdrant-spring", "Spring 接入示例", "Example", "")));
        when(ingestionService.add(
                "user-1",
                "网页搜索结果：Qdrant Java",
                """
                        搜索词：Qdrant Java

                        [来源1] Qdrant Java Client
                        链接：https://qdrant.tech
                        来源：Qdrant
                        摘要：官方 Java 客户端

                        [来源2] Qdrant Spring
                        链接：https://example.com/qdrant-spring
                        来源：Example
                        摘要：Spring 接入示例
                        """.strip(),
                "web_search",
                "",
                "web,search"))
                .thenReturn(new KnowledgeIngestionResult(9, "网页搜索结果：Qdrant Java", 1, false));
        WebSearchWechatTool tool = new WebSearchWechatTool(service, ingestionService, new WebResourceContextService());

        var reply = tool.execute(new WechatToolRequest(
                "user-1",
                "搜索 Qdrant Java，并记住这些结果以后参考",
                Map.of("query", "Qdrant Java", "limit", "2"),
                "",
                null,
                null));

        assertThat(reply.text()).contains("网页搜索结果", "已保存到知识库", "document_id=9");
        verify(ingestionService).add(
                "user-1",
                "网页搜索结果：Qdrant Java",
                """
                        搜索词：Qdrant Java

                        [来源1] Qdrant Java Client
                        链接：https://qdrant.tech
                        来源：Qdrant
                        摘要：官方 Java 客户端

                        [来源2] Qdrant Spring
                        链接：https://example.com/qdrant-spring
                        来源：Example
                        摘要：Spring 接入示例
                        """.strip(),
                "web_search",
                "",
                "web,search");
    }

    @Test
    void webReadToolCanResolveUrlFromRecentSearchContext() {
        WebReadService webReadService = mock(WebReadService.class);
        KnowledgeIngestionService ingestionService = mock(KnowledgeIngestionService.class);
        WebResourceContextService resourceContextService = new WebResourceContextService();
        resourceContextService.recordSearch("user-1", "Qdrant Java", List.of(
                new WebSearchResult("第一篇", "https://example.com/one", "摘要1", "Example", ""),
                new WebSearchResult("第二篇", "https://example.com/two", "摘要2", "Example", "")));
        when(webReadService.read("https://example.com/two")).thenReturn(new WebPageContent(
                "https://example.com/two",
                "第二篇",
                "第二篇正文",
                Instant.parse("2026-07-23T00:00:00Z")));
        WebReadWechatTool tool = new WebReadWechatTool(webReadService, ingestionService, resourceContextService);

        var reply = tool.execute(new WechatToolRequest(
                "user-1",
                "第二个网页详细看看",
                Map.of(),
                "",
                null,
                null));

        assertThat(reply.text()).contains("网页已读取", "第二篇", "https://example.com/two");
        assertThat(resourceContextService.resolveUrl("user-1", "上一个链接保存下来")).contains("https://example.com/two");
    }

    @Test
    void knowledgeManageToolListsDocuments() {
        KnowledgeManageService service = mock(KnowledgeManageService.class);
        when(service.list("user-1", "", "", "", 10)).thenReturn(List.of(
                new KnowledgeDocument(1, "user-1", "项目说明", "text", "", "agent", "hash", 3, Instant.now(), Instant.now(), false)));
        KnowledgeManageWechatTool tool = new KnowledgeManageWechatTool(service);

        var reply = tool.execute(new WechatToolRequest(
                "user-1",
                "列出知识库",
                Map.of("operation", "list"),
                "",
                null,
                null));

        assertThat(reply.text()).contains("项目说明", "document_id=1");
    }

    @Test
    void knowledgeManageToolFiltersByKeywordTagsAndSource() {
        KnowledgeManageService service = mock(KnowledgeManageService.class);
        when(service.list("user-1", "Qdrant", "Java", "web", 10)).thenReturn(List.of(
                new KnowledgeDocument(2, "user-1", "Qdrant Java", "web", "https://example.com", "Qdrant,Java", "hash", 2, Instant.now(), Instant.now(), false)));
        KnowledgeManageWechatTool tool = new KnowledgeManageWechatTool(service);

        var reply = tool.execute(new WechatToolRequest(
                "user-1",
                "列出 Qdrant 的网页资料",
                Map.of("operation", "list", "keyword", "Qdrant", "tags", "Java", "source_type", "web"),
                "",
                null,
                null));

        assertThat(reply.text()).contains("Qdrant Java", "来源：web", "标签：Qdrant,Java");
    }

    @Test
    void knowledgeManageToolUpdatesTitleAndTags() {
        KnowledgeManageService service = mock(KnowledgeManageService.class);
        when(service.updateTitle("user-1", 2, "新标题")).thenReturn(true);
        when(service.updateTags("user-1", 2, "Qdrant,Java")).thenReturn(true);
        KnowledgeManageWechatTool tool = new KnowledgeManageWechatTool(service);

        var titleReply = tool.execute(new WechatToolRequest(
                "user-1",
                "修改标题",
                Map.of("operation", "update_title", "document_id", "2", "title", "新标题"),
                "",
                null,
                null));
        var tagsReply = tool.execute(new WechatToolRequest(
                "user-1",
                "修改标签",
                Map.of("operation", "update_tags", "document_id", "2", "tags", "Qdrant,Java"),
                "",
                null,
                null));

        assertThat(titleReply.text()).contains("标题已更新");
        assertThat(tagsReply.text()).contains("标签已更新");
    }

    @Test
    void knowledgeManageToolRequiresConfirmationBeforeDelete() {
        KnowledgeManageService service = mock(KnowledgeManageService.class);
        when(service.delete("user-1", 3)).thenReturn(true);
        KnowledgeManageWechatTool tool = new KnowledgeManageWechatTool(service);

        var first = tool.execute(new WechatToolRequest(
                "user-1",
                "删除第三条资料",
                Map.of("operation", "delete", "document_id", "3"),
                "",
                null,
                null));
        verifyNoMoreInteractions(service);

        var second = tool.execute(new WechatToolRequest(
                "user-1",
                "确认",
                Map.of("operation", "delete", "confirm", "true"),
                "",
                null,
                null));

        assertThat(first.text()).contains("请确认");
        assertThat(second.text()).contains("已删除知识文档：document_id=3");
    }
}
