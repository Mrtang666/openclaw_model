package com.example.spring.wechat.news.client;

import com.example.spring.wechat.news.model.NewsArticle;

import java.util.List;

/**
 * 天行API响应
 */

//封装第三方API（天行数据）的返回结果，统一成功/失败格式。

public class TianNewsResponse {

    /** 是否成功 */
    private boolean success;

    /** 错误信息（失败时） */
    private String errorMessage;

    /** 新闻列表 */
    private List<NewsArticle> articles;

    /** 总数 */
    private int total;

    /** 分类ID */
    private int categoryId;

    private TianNewsResponse(
            boolean success,
            String errorMessage,
            List<NewsArticle> articles,
            int total,
            int categoryId) {
        this.success = success;
        this.errorMessage = errorMessage;
        this.articles = articles;
        this.total = total;
        this.categoryId = categoryId;
    }

    public static TianNewsResponse success(List<NewsArticle> articles, int total) {
        return new TianNewsResponse(true, null, articles == null ? List.of() : articles, total, 0);
    }

    public static TianNewsResponse error(String message) {
        return new TianNewsResponse(false, message, List.of(), 0, 0);
    }

    public boolean isSuccess() {
        return success;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public List<NewsArticle> getArticles() {
        return articles;
    }

    public int getTotal() {
        return total;
    }

    public int getCategoryId() {
        return categoryId;
    }
}
