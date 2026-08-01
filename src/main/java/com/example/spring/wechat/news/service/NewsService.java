package com.example.spring.wechat.news.service;

import com.example.spring.wechat.news.client.TianNewsClient;
import com.example.spring.wechat.news.client.TianNewsResponse;
import com.example.spring.wechat.news.model.NewsArticle;
import com.example.spring.wechat.news.model.NewsQuery;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class NewsService {

    private static final Logger log = LoggerFactory.getLogger(NewsService.class);

    @Autowired
    private TianNewsClient newsClient;

    @Autowired
    private SessionManager sessionManager;

    private static final int MAX_RESULTS = 30;

    /**
     * 搜索新闻（主入口）
     */
    public NewsResult searchNews(NewsQuery query) {
        String userId = query.getUserId();
        String sessionId = query.getSessionId();
        String keyword = query.getKeyword();
        String action = query.getAction();

        log.debug("searchNews: userId={}, keyword={}, action={}", userId, keyword, action);

        // 处理分页操作
        if ("more".equals(action) || "all".equals(action)) {
            return handlePagination(sessionId, action);
        }

        // 关键词为空
        if (keyword == null || keyword.trim().isEmpty()) {
            return NewsResult.empty(null);
        }

        // 执行搜索
        log.info("执行关键词搜索: keyword={}", keyword);
        TianNewsResponse response = newsClient.searchByKeyword(keyword.trim());

        if (!response.isSuccess()) {
            log.warn("搜索失败: keyword={}, error={}", keyword, response.getErrorMessage());
            return NewsResult.error(response.getErrorMessage());
        }

        List<NewsArticle> articles = response.getArticles();
        if (articles == null || articles.isEmpty()) {
            log.warn("搜索无结果: keyword={}", keyword);
            return NewsResult.empty(keyword);
        }

        // 去重、排序、截断
        List<NewsArticle> processed = deduplicateAndSort(articles, MAX_RESULTS);

        // 创建会话并返回第一页
        SessionManager.NewsSession session = sessionManager.createSession(sessionId, processed, keyword);
        List<NewsArticle> firstBatch = session.getNextBatch();

        return NewsResult.success(
                firstBatch,
                session.getArticles().size(),
                session.getDisplayedCount(),
                session.hasMore()
        );
    }

    /**
     * 处理分页
     */
    private NewsResult handlePagination(String sessionId, String action) {
        SessionManager.NewsSession session = sessionManager.getSession(sessionId);
        if (session == null) {
            return NewsResult.sessionExpired();
        }

        if ("all".equals(action)) {
            List<NewsArticle> all = session.getArticles();
            session.setCurrentIndex(all.size());
            return NewsResult.allNews(all);
        }

        if (!session.hasMore()) {
            return NewsResult.noMoreNews(session.getArticles().size());
        }

        List<NewsArticle> batch = session.getNextBatch();
        return NewsResult.moreNews(
                batch,
                session.getDisplayedCount(),
                session.getArticles().size(),
                session.hasMore()
        );
    }

    /**
     * 去重并排序
     */
    private List<NewsArticle> deduplicateAndSort(List<NewsArticle> articles, int limit) {
        if (articles == null || articles.isEmpty()) {
            return new ArrayList<>();
        }

        // 去重
        Map<String, NewsArticle> uniqueMap = new LinkedHashMap<>();
        for (NewsArticle article : articles) {
            if (article.getId() != null && !article.getId().isEmpty()) {
                uniqueMap.putIfAbsent(article.getId(), article);
            }
        }

        // 按发布时间排序（最新的在前）
        return uniqueMap.values().stream()
                .sorted((a1, a2) -> {
                    String t1 = a1.getPublishTime();
                    String t2 = a2.getPublishTime();
                    if (t1 == null && t2 == null) return 0;
                    if (t1 == null) return 1;
                    if (t2 == null) return -1;
                    return t2.compareTo(t1);
                })
                .limit(limit)
                .collect(Collectors.toList());
    }
}
