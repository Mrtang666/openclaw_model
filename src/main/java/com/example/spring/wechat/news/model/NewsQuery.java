package com.example.spring.wechat.news.model;

/**
 * 新闻查询参数
 */

//封装前端/用户传入的查询请求参数。

public class NewsQuery {

    /** 用户ID */
    private final String userId;

    /** 会话ID */
    private final String sessionId;

    /** 搜索关键词（可选） */
    private final String keyword;

    /** 操作类型: init, more, all */
    //这是当用户想要继续操作：比如“更多”“全部”的操作
    private final String action;

    public NewsQuery(String userId, String sessionId, String keyword, String action) {
        this.userId = userId;
        this.sessionId = sessionId;
        this.keyword = keyword;
        this.action = action;
    }

    public String getUserId() {
        return userId;
    }

    public String getSessionId() {
        return sessionId;
    }

    public String getKeyword() {
        return keyword;
    }

    public String getAction() {
        return action;
    }
}
