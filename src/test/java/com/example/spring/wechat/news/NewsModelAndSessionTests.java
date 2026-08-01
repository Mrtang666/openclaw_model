package com.example.spring.wechat.news;

import com.example.spring.wechat.news.client.TianNewsResponse;
import com.example.spring.wechat.news.model.NewsArticle;
import com.example.spring.wechat.news.model.NewsQuery;
import com.example.spring.wechat.news.service.NewsResult;
import com.example.spring.wechat.news.service.SessionManager;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class NewsModelAndSessionTests {

    @Test
    void explicitNewsModelsExposeTheSamePropertiesWithoutLombok() {
        NewsQuery query = new NewsQuery("user-1", "session-1", "科技", "more");
        NewsArticle article = article("1", "第一条", "2026-07-29 10:00:00");
        article.setCategoryName("科技");
        TianNewsResponse response = TianNewsResponse.success(List.of(article), 1);
        NewsResult result = NewsResult.success(response.getArticles(), response.getTotal(), 1, false);

        assertThat(query.getUserId()).isEqualTo("user-1");
        assertThat(query.getSessionId()).isEqualTo("session-1");
        assertThat(query.getKeyword()).isEqualTo("科技");
        assertThat(query.getAction()).isEqualTo("more");
        assertThat(response.isSuccess()).isTrue();
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getArticles()).containsExactly(article);
        assertThat(result.getArticles().get(0).getCategoryName()).isEqualTo("科技");
    }

    @Test
    void sessionPaginatesTwoArticlesAtATimeAndCanReset() {
        SessionManager manager = new SessionManager();
        SessionManager.NewsSession session = manager.createSession(
                "session-2",
                List.of(
                        article("1", "第一条", "2026-07-29 10:00:00"),
                        article("2", "第二条", "2026-07-29 09:00:00"),
                        article("3", "第三条", "2026-07-29 08:00:00")),
                "科技");

        assertThat(session.getNextBatch()).extracting(NewsArticle::getId).containsExactly("1", "2");
        assertThat(session.getDisplayedCount()).isEqualTo(2);
        assertThat(session.getRemainingCount()).isEqualTo(1);
        assertThat(session.hasMore()).isTrue();
        assertThat(session.getNextBatch()).extracting(NewsArticle::getId).containsExactly("3");
        assertThat(session.hasMore()).isFalse();

        manager.resetSession("session-2");
        assertThat(session.getDisplayedCount()).isZero();
        assertThat(session.getNextBatch()).extracting(NewsArticle::getId).containsExactly("1", "2");
    }

    private NewsArticle article(String id, String title, String publishTime) {
        return new NewsArticle(
                id, title, title + "描述", "https://example.com/" + id, "", "测试来源",
                publishTime, 13, null);
    }
}
