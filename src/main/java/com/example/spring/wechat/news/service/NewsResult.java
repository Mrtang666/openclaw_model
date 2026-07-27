package com.example.spring.wechat.news.service;

import com.example.spring.wechat.news.model.NewsArticle;
import lombok.Builder;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;


//封装业务层的返回结果，供上层（WechatTool）使用。
@Data
@Builder
public class NewsResult {

    private boolean success;
    private String message;
    private List<NewsArticle> articles;
    private int total;
    private int displayedCount;
    private boolean hasMore;
    private String keyword;
    private boolean sessionExpired;

    public static NewsResult success(List<NewsArticle> articles, int total,
                                     int displayedCount, boolean hasMore) {
        return NewsResult.builder()
                .success(true)
                .articles(articles != null ? articles : new ArrayList<>())
                .total(total)
                .displayedCount(displayedCount)
                .hasMore(hasMore)
                .build();
    }

    public static NewsResult empty(String keyword) {
        String msg;
        if (keyword != null && !keyword.trim().isEmpty()) {
            msg = "未找到关于「" + keyword + "」的新闻，请尝试其他关键词";
        } else {
            msg = "请输入要搜索的关键词，例如：华为、科技";
        }
        return NewsResult.builder()
                .success(false)
                .message(msg)
                .articles(new ArrayList<>())
                .build();
    }

    public static NewsResult sessionExpired() {
        return NewsResult.builder()
                .success(false)
                .sessionExpired(true)
                .message("会话已过期，请重新搜索")
                .articles(new ArrayList<>())
                .build();
    }

    public static NewsResult noMoreNews(int total) {
        return NewsResult.builder()
                .success(false)
                .message("已展示全部 " + total + " 条新闻")
                .articles(new ArrayList<>())
                .build();
    }

    public static NewsResult moreNews(List<NewsArticle> articles, int displayedCount,
                                      int total, boolean hasMore) {
        return NewsResult.builder()
                .success(true)
                .articles(articles != null ? articles : new ArrayList<>())
                .displayedCount(displayedCount)
                .total(total)
                .hasMore(hasMore)
                .build();
    }

    public static NewsResult allNews(List<NewsArticle> articles) {
        return NewsResult.builder()
                .success(true)
                .articles(articles != null ? articles : new ArrayList<>())
                .total(articles != null ? articles.size() : 0)
                .displayedCount(articles != null ? articles.size() : 0)
                .hasMore(false)
                .build();
    }

    public static NewsResult error(String message) {
        return NewsResult.builder()
                .success(false)
                .message(message)
                .articles(new ArrayList<>())
                .build();
    }
}