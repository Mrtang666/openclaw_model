package com.example.spring.wechat.news.service;


import com.example.spring.wechat.news.model.NewsArticle;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 新闻会话管理（暂存30条新闻，支持分页）
 */

//管理用户搜索会话，支持分页功能。
@Slf4j
@Service
public class SessionManager {

    private final Map<String, NewsSession> sessionMap = new ConcurrentHashMap<>();

    @Data
    @Builder
    public static class NewsSession {
        private String sessionId;
        private List<NewsArticle> articles;
        private int currentIndex;
        private int batchSize;
        private String queryType;
        private String keyword;
        private LocalDateTime createdAt;
        private LocalDateTime expiresAt;

        public boolean hasMore() {
            return currentIndex < articles.size();
        }

        public List<NewsArticle> getNextBatch() {
            if (!hasMore()) {
                return List.of();
            }
            int end = Math.min(currentIndex + batchSize, articles.size());
            List<NewsArticle> batch = articles.subList(currentIndex, end);
            currentIndex = end;
            return batch;
        }

        public int getDisplayedCount() {
            return currentIndex;
        }

        public int getRemainingCount() {
            return articles.size() - currentIndex;
        }

        public boolean isExpired() {
            return LocalDateTime.now().isAfter(expiresAt);
        }
    }

    /**
     * 创建会话
     */
    public NewsSession createSession(String sessionId, List<NewsArticle> articles,
                                     String keyword) {
        NewsSession session = NewsSession.builder()
                .sessionId(sessionId)
                .articles(articles)
                .currentIndex(0)
                .batchSize(2)
                .keyword(keyword)
                .createdAt(LocalDateTime.now())
                .expiresAt(LocalDateTime.now().plusHours(1))
                .build();
        sessionMap.put(sessionId, session);
        log.debug("创建会话: sessionId={}, count={}", sessionId, articles.size());
        return session;
    }

    /**
     * 获取会话
     */
    public NewsSession getSession(String sessionId) {
        NewsSession session = sessionMap.get(sessionId);
        if (session == null) {
            return null;
        }
        if (session.isExpired()) {
            sessionMap.remove(sessionId);
            log.debug("会话已过期: sessionId={}", sessionId);
            return null;
        }
        return session;
    }

    /**
     * 重置会话索引（回到开头）
     */
    public void resetSession(String sessionId) {
        NewsSession session = getSession(sessionId);
        if (session != null) {
            session.setCurrentIndex(0);
            log.debug("重置会话: sessionId={}", sessionId);
        }
    }
}