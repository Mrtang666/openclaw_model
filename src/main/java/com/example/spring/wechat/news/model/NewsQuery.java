package com.example.spring.wechat.news.model;

import lombok.Builder;
import lombok.Data;

/**
 * 新闻查询参数
 */

//封装前端/用户传入的查询请求参数。

@Data
@Builder
public class NewsQuery {

    /** 用户ID */
    private String userId;

    /** 会话ID */
    private String sessionId;

    /** 搜索关键词（可选） */
    private String keyword;

    /** 操作类型: init, more, all */
    //这是当用户想要继续操作：比如“更多”“全部”的操作
    private String action;
}