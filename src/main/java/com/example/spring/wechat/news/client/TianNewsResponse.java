package com.example.spring.wechat.news.client;

import com.example.spring.wechat.news.model.NewsArticle;
import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 天行API响应
 */

//封装第三方API（天行数据）的返回结果，统一成功/失败格式。

@Data
@Builder
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

    public static TianNewsResponse success(List<NewsArticle> articles, int total) {
        return TianNewsResponse.builder()
                .success(true)
                .articles(articles)
                .total(total)
                .build();
    }

    public static TianNewsResponse error(String message) {
        return TianNewsResponse.builder()
                .success(false)
                .errorMessage(message)
                .articles(List.of())
                .build();
    }
}