package com.example.spring.wechat.news.client;

import com.example.spring.wechat.news.model.NewsArticle;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
public class TianNewsClient {

    @Value("${tian.api.key}")
    private String apiKey;

    @Value("${tian.api.url:https://apis.tianapi.com/allnews/index}")
    private String apiUrl;

    @Value("${news.fetch.size:10}")
    private int defaultSize;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    private LocalDateTime lastCallTime;
    private static final long MIN_INTERVAL_MS = 2000;

    // 分类映射
    private static final int[] CATEGORY_IDS = {1, 5, 8, 10, 12, 13, 26, 27, 64, 29};
    private static final String[] CATEGORY_NAMES = {"国内", "社会", "国际", "娱乐", "体育", "科技", "足球", "军事", "财经", "人工智能"};

    // ✅ 最大返回条数
    private static final int MAX_TOTAL_RESULTS = 30;

    public TianNewsClient() {
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
    }

    /**
     * 关键词搜索新闻（遍历所有分类，限制总条数）
     */
    public TianNewsResponse searchByKeyword(String keyword) {
        return searchByKeyword(keyword, defaultSize);
    }

    /**
     * 关键词搜索新闻（遍历所有分类，限制总条数）
     */
    public TianNewsResponse searchByKeyword(String keyword, int num) {
        if (!isValidApiKey()) {
            return TianNewsResponse.error("API Key未配置");
        }

        if (!checkRateLimit()) {
            return TianNewsResponse.error("调用频率超限，请稍后再试");
        }

        List<NewsArticle> allArticles = new ArrayList<>();

        for (int i = 0; i < CATEGORY_IDS.length; i++) {
            // ✅ 如果已经达到最大条数，停止继续请求
            if (allArticles.size() >= MAX_TOTAL_RESULTS) {
                log.info("已达到最大条数 {}，停止继续搜索", MAX_TOTAL_RESULTS);
                break;
            }

            // ✅ 每个分类最多取 5 条，减少 API 调用量
            int perCategoryNum = Math.min(num, 5);
            String url = buildUrl(CATEGORY_IDS[i], perCategoryNum, keyword);
            log.info("调用天行API: categoryId={}, keyword={}", CATEGORY_IDS[i], keyword);

            TianNewsResponse response = doRequest(url, CATEGORY_IDS[i]);
            if (response.isSuccess() && response.getArticles() != null) {
                for (NewsArticle article : response.getArticles()) {
                    article.setCategoryName(CATEGORY_NAMES[i]);
                }
                allArticles.addAll(response.getArticles());
            }
        }

        if (allArticles.isEmpty()) {
            return TianNewsResponse.error("未找到相关新闻");
        }

        // ✅ 截断到最大条数
        if (allArticles.size() > MAX_TOTAL_RESULTS) {
            allArticles = allArticles.subList(0, MAX_TOTAL_RESULTS);
        }

        log.info("搜索完成: keyword={}, 共获取 {} 条", keyword, allArticles.size());
        return TianNewsResponse.success(allArticles, allArticles.size());
    }

    /**
     * 根据分类ID获取新闻
     */
    public TianNewsResponse fetchByCategory(int categoryId) {
        return fetchByCategory(categoryId, defaultSize);
    }

    public TianNewsResponse fetchByCategory(int categoryId, int num) {
        if (!isValidApiKey()) {
            return TianNewsResponse.error("API Key未配置");
        }

        if (!checkRateLimit()) {
            return TianNewsResponse.error("调用频率超限，请稍后再试");
        }

        String url = buildUrl(categoryId, num, null);
        log.info("调用天行API: categoryId={}", categoryId);
        return doRequest(url, categoryId);
    }

    private boolean isValidApiKey() {
        return apiKey != null && !apiKey.trim().isEmpty() && !"your_api_key_here".equals(apiKey);
    }

    private boolean checkRateLimit() {
        if (lastCallTime != null) {
            long elapsed = Duration.between(lastCallTime, LocalDateTime.now()).toMillis();
            if (elapsed < MIN_INTERVAL_MS) {
                return false;
            }
        }
        lastCallTime = LocalDateTime.now();
        return true;
    }

    private String buildUrl(int categoryId, int num, String keyword) {
        StringBuilder url = new StringBuilder(apiUrl)
                .append("?key=").append(apiKey)
                .append("&col=").append(categoryId)
                .append("&num=").append(Math.min(num, 50));

        if (keyword != null && !keyword.trim().isEmpty()) {
            url.append("&word=").append(keyword.trim());
        }

        return url.toString();
    }

    private TianNewsResponse doRequest(String url, int categoryId) {
        try {
            String responseJson = restTemplate.getForObject(url, String.class);

            if (responseJson == null || responseJson.trim().isEmpty()) {
                return TianNewsResponse.error("API返回空响应");
            }

            return parseResponse(responseJson, categoryId);

        } catch (Exception e) {
            log.error("天行API调用异常: {}", e.getMessage(), e);
            return TianNewsResponse.error("API调用异常: " + e.getMessage());
        }
    }

    private TianNewsResponse parseResponse(String responseJson, int categoryId) {
        try {
            JsonNode root = objectMapper.readTree(responseJson);

            int code = root.path("code").asInt();
            String msg = root.path("msg").asText("未知");

            if (code != 200) {
                return TianNewsResponse.error(getErrorMessage(code, msg));
            }

            JsonNode listNode = findNewsList(root);

            if (listNode == null || !listNode.isArray()) {
                log.warn("无法找到新闻列表");
                return TianNewsResponse.error("数据格式异常: 找不到新闻列表");
            }

            List<NewsArticle> articles = parseArticles(listNode, categoryId);

            return TianNewsResponse.success(articles, articles.size());

        } catch (Exception e) {
            log.error("解析天行API响应失败: {}", e.getMessage(), e);
            return TianNewsResponse.error("数据解析失败: " + e.getMessage());
        }
    }

    private JsonNode findNewsList(JsonNode root) {
        if (root.has("result") && root.path("result").has("newslist")) {
            return root.path("result").path("newslist");
        }
        if (root.has("data") && root.path("data").has("newslist")) {
            return root.path("data").path("newslist");
        }
        if (root.isArray()) {
            return root;
        }
        return null;
    }

    private List<NewsArticle> parseArticles(JsonNode listNode, int categoryId) {
        List<NewsArticle> articles = new ArrayList<>();

        for (JsonNode item : listNode) {
            String id = getSafeString(item, "id");
            String title = getSafeString(item, "title");

            if (id.isEmpty() || title.isEmpty()) {
                continue;
            }

            String description = getSafeString(item, "description");
            if (description.isEmpty()) {
                description = getSafeString(item, "content");
            }
            if (description.isEmpty()) {
                description = title;
            }

            String source = getSafeString(item, "source");
            if (source.isEmpty()) {
                source = "未知来源";
            }

            NewsArticle article = NewsArticle.builder()
                    .id(id)
                    .title(title)
                    .description(description)
                    .url(getSafeString(item, "url"))
                    .picUrl(getSafeString(item, "picUrl"))
                    .source(source)
                    .publishTime(getSafeString(item, "ctime"))
                    .categoryId(categoryId)
                    .build();

            articles.add(article);
        }

        return articles;
    }

    private String getErrorMessage(int code, String msg) {
        switch (code) {
            case 230: return "API Key无效，请检查配置";
            case 150: return "API调用次数已用完，请明天再试";
            case 130: return "API调用频率超限，请稍后再试";
            case 250: return "该分类暂无新闻数据";
            default: return "API返回错误: " + msg + " (code=" + code + ")";
        }
    }

    private String getSafeString(JsonNode node, String field) {
        if (node == null || !node.has(field)) {
            return "";
        }
        JsonNode value = node.path(field);
        return (value == null || value.isNull()) ? "" : value.asText().trim();
    }
}