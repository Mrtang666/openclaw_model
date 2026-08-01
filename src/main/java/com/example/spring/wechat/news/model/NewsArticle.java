package com.example.spring.wechat.news.model;

//数据模型
//新闻实体新闻
//定义新闻数据的核心结构，是所有层之间传递数据的载体。

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

    public NewsArticle(
            String id,
            String title,
            String description,
            String url,
            String picUrl,
            String source,
            String publishTime,
            Integer categoryId,
            String categoryName) {
        this.id = id;
        this.title = title;
        this.description = description;
        this.url = url;
        this.picUrl = picUrl;
        this.source = source;
        this.publishTime = publishTime;
        this.categoryId = categoryId;
        this.categoryName = categoryName;
    }

    public String getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public String getUrl() {
        return url;
    }

    public String getPicUrl() {
        return picUrl;
    }

    public String getSource() {
        return source;
    }

    public String getPublishTime() {
        return publishTime;
    }

    public Integer getCategoryId() {
        return categoryId;
    }

    public String getCategoryName() {
        return categoryName;
    }

    public void setCategoryName(String categoryName) {
        this.categoryName = categoryName;
    }
}
