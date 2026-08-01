package com.example.spring.wechat.news.service;

import com.example.spring.wechat.news.model.NewsArticle;

import java.util.ArrayList;
import java.util.List;


//封装业务层的返回结果，供上层（WechatTool）使用。
public class NewsResult {

    private boolean success;
    private String message;
    private List<NewsArticle> articles;
    private int total;
    private int displayedCount;
    private boolean hasMore;
    private String keyword;
    private boolean sessionExpired;

    private NewsResult(
            boolean success,
            String message,
            List<NewsArticle> articles,
            int total,
            int displayedCount,
            boolean hasMore,
            String keyword,
            boolean sessionExpired) {
        this.success = success;
        this.message = message;
        this.articles = articles;
        this.total = total;
        this.displayedCount = displayedCount;
        this.hasMore = hasMore;
        this.keyword = keyword;
        this.sessionExpired = sessionExpired;
    }

    public static NewsResult success(List<NewsArticle> articles, int total,
                                     int displayedCount, boolean hasMore) {
        return new NewsResult(
                true, null, list(articles), total, displayedCount, hasMore, null, false);
    }

    public static NewsResult empty(String keyword) {
        String msg;
        if (keyword != null && !keyword.trim().isEmpty()) {
            msg = "未找到关于「" + keyword + "」的新闻，请尝试其他关键词";
        } else {
            msg = "请输入要搜索的关键词，例如：华为、科技";
        }
        return new NewsResult(false, msg, List.of(), 0, 0, false, null, false);
    }

    public static NewsResult sessionExpired() {
        return new NewsResult(false, "会话已过期，请重新搜索", List.of(), 0, 0, false, null, true);
    }

    public static NewsResult noMoreNews(int total) {
        return new NewsResult(
                false, "已展示全部 " + total + " 条新闻", List.of(), 0, 0, false, null, false);
    }

    public static NewsResult moreNews(List<NewsArticle> articles, int displayedCount,
                                      int total, boolean hasMore) {
        return new NewsResult(
                true, null, list(articles), total, displayedCount, hasMore, null, false);
    }

    public static NewsResult allNews(List<NewsArticle> articles) {
        List<NewsArticle> values = list(articles);
        return new NewsResult(true, null, values, values.size(), values.size(), false, null, false);
    }

    public static NewsResult error(String message) {
        return new NewsResult(false, message, List.of(), 0, 0, false, null, false);
    }

    public boolean isSuccess() {
        return success;
    }

    public String getMessage() {
        return message;
    }

    public List<NewsArticle> getArticles() {
        return articles;
    }

    public int getTotal() {
        return total;
    }

    public int getDisplayedCount() {
        return displayedCount;
    }

    public boolean isHasMore() {
        return hasMore;
    }

    public String getKeyword() {
        return keyword;
    }

    public boolean isSessionExpired() {
        return sessionExpired;
    }

    private static List<NewsArticle> list(List<NewsArticle> articles) {
        return articles == null ? new ArrayList<>() : articles;
    }
}
