package com.example.spring.wechat.web.context;

import com.example.spring.wechat.web.model.WebPageContent;
import com.example.spring.wechat.web.model.WebSearchResult;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class WebResourceContextServiceTests {

    @Test
    void keepsRecentThreeSearchesAndFiveResultsPerSearch() {
        WebResourceContextService service = new WebResourceContextService();

        for (int round = 1; round <= 4; round++) {
            service.recordSearch("user-1", "query-" + round, List.of(
                    result(round, 1),
                    result(round, 2),
                    result(round, 3),
                    result(round, 4),
                    result(round, 5),
                    result(round, 6)));
        }

        List<WebSearchSnapshot> searches = service.recentSearches("user-1");

        assertThat(searches).hasSize(3);
        assertThat(searches.get(0).query()).isEqualTo("query-4");
        assertThat(searches.get(0).results()).hasSize(5);
        assertThat(searches.get(2).query()).isEqualTo("query-2");
    }

    @Test
    void keepsRecentFiveReadPages() {
        WebResourceContextService service = new WebResourceContextService();

        for (int index = 1; index <= 6; index++) {
            service.recordRead("user-1", new WebPageContent(
                    "https://example.com/" + index,
                    "页面" + index,
                    "正文" + index,
                    Instant.now()));
        }

        List<WebReadSnapshot> reads = service.recentReads("user-1");

        assertThat(reads).hasSize(5);
        assertThat(reads.get(0).url()).isEqualTo("https://example.com/6");
        assertThat(reads.get(4).url()).isEqualTo("https://example.com/2");
    }

    @Test
    void resolvesOrdinalAndPreviousWebReferences() {
        WebResourceContextService service = new WebResourceContextService();
        service.recordSearch("user-1", "qdrant", List.of(
                result(1, 1),
                result(1, 2),
                result(1, 3)));
        service.recordRead("user-1", new WebPageContent(
                "https://read.example.com/latest",
                "刚读过的页面",
                "正文",
                Instant.now()));

        assertThat(service.resolveUrl("user-1", "第二个网页详细看看"))
                .contains("https://example.com/1/2");
        assertThat(service.resolveUrl("user-1", "上一个链接保存下来"))
                .contains("https://read.example.com/latest");
        assertThat(service.resolveUrl("user-1", "刚才那个保存"))
                .contains("https://read.example.com/latest");
    }

    @Test
    void buildsCompactContextWithoutFullPageContent() {
        WebResourceContextService service = new WebResourceContextService();
        service.recordSearch("user-1", "qdrant", List.of(result(1, 1)));
        service.recordRead("user-1", new WebPageContent(
                "https://read.example.com/latest",
                "刚读过的页面",
                "正文开头。" + "中间内容。".repeat(80) + "正文结尾不应该进入上下文。",
                Instant.now()));

        String context = service.contextText("user-1");

        assertThat(context).contains("最近搜索", "qdrant", "https://example.com/1/1", "最近阅读");
        assertThat(context).contains("正文开头");
        assertThat(context).doesNotContain("正文结尾不应该进入上下文");
    }

    private WebSearchResult result(int round, int index) {
        return new WebSearchResult(
                "标题" + round + "-" + index,
                "https://example.com/" + round + "/" + index,
                "摘要" + round + "-" + index,
                "Example",
                "");
    }
}
