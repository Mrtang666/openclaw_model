package com.example.spring.wechat.news.model;

import lombok.Builder;
import lombok.Data;


//数据模型
//新闻实体新闻
//定义新闻数据的核心结构，是所有层之间传递数据的载体。

@Data
@Builder
public class NewsArticle {

    /** 文章ID */
    private String id;

    /** 标题 */
    private String title;

    /** 描述 */
    private String description;

    /** 链接 */
    private String url;

    /** 图片链接 */
    private String picUrl;

    /** 来源 */
    private String source;

    /** 发布时间 */
    private String publishTime;

    /** 分类ID */
    private Integer categoryId;

    /** 分类名称 */
    private String categoryName;
}