package com.example.spring.wechat.news.service;


import com.example.spring.wechat.news.model.NewsArticle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 新闻会话管理（暂存30条新闻，支持分页）
 */

//管理用户搜索会话，支持分页功能。
@Service
public class SessionManager {

    private static final Logger log = LoggerFactory.getLogger(SessionManager.class);

    private final Map<String, NewsSession> sessionMap = new ConcurrentHashMap<>();

    public static class NewsSession {
        private final String sessionId;
        private final List<NewsArticle> articles;
        private int currentIndex;
        private final int batchSize;
        private final String keyword;
        private final LocalDateTime createdAt;
        private final LocalDateTime expiresAt;

        public NewsSession(
                String sessionId,
                List<NewsArticle> articles,
                int currentIndex,
                int batchSize,
                String keyword,
                LocalDateTime createdAt,
                LocalDateTime expiresAt) {
            this.sessionId = sessionId;
            this.articles = articles;
            this.currentIndex = currentIndex;
            this.batchSize = batchSize;
            this.keyword = keyword;
            this.createdAt = createdAt;
            this.expiresAt = expiresAt;
        }

        public synchronized boolean hasMore() {
            return currentIndex < articles.size();
        }

        public synchronized List<NewsArticle> getNextBatch() {
            if (!hasMore()) {
                return List.of();
            }
            int end = Math.min(currentIndex + batchSize, articles.size());
            List<NewsArticle> batch = articles.subList(currentIndex, end);
            currentIndex = end;
            return batch;
        }

        public synchronized int getDisplayedCount() {
            return currentIndex;
        }

        public synchronized int getRemainingCount() {
            return articles.size() - currentIndex;
        }

        public boolean isExpired() {
            return LocalDateTime.now().isAfter(expiresAt);
        }

        public String getSessionId() {
            return sessionId;
        }

        public List<NewsArticle> getArticles() {
            return articles;
        }

        public synchronized int getCurrentIndex() {
            return currentIndex;
        }

        public synchronized void setCurrentIndex(int currentIndex) {
            this.currentIndex = Math.max(0, Math.min(currentIndex, articles.size()));
        }

        public int getBatchSize() {
            return batchSize;
        }

        public String getKeyword() {
            return keyword;
        }

        public LocalDateTime getCreatedAt() {
            return createdAt;
        }

        public LocalDateTime getExpiresAt() {
            return expiresAt;
        }
    }

    /**
     * 创建会话
     */
    public NewsSession createSession(String sessionId, List<NewsArticle> articles,
                                     String keyword) {
        LocalDateTime now = LocalDateTime.now();
        NewsSession session = new NewsSession(
                sessionId, List.copyOf(articles), 0, 2, keyword, now, now.plusHours(1));
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
