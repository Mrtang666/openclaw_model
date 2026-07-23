package com.example.spring.wechat.web.client;

import com.example.spring.wechat.web.exception.WebToolException;
import com.example.spring.wechat.web.model.WebSearchResult;
import com.example.spring.wechat.web.mcp.McpToolClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 百炼联网搜索客户端。
 *
 * <p>百炼 WebSearch MCP 暴露的工具名是 bailian_web_search，核心参数是 query/count。
 * 项目内部仍然使用统一的 web_search 工具名，对外屏蔽不同搜索供应商的协议差异。</p>
 */
public class BailianWebSearchClient implements WebSearchClient {

    private static final Logger log = LoggerFactory.getLogger(BailianWebSearchClient.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Pattern MARKDOWN_LINK = Pattern.compile("\\[([^\\]]{1,200})]\\((https?://[^\\s)]+)\\)");

    private final RestClient restClient;
    private final McpToolClient mcpToolClient;
    private final String endpoint;
    private final String apiKey;
    private final String fallbackBaseUrl;
    private final String fallbackModel;

    public BailianWebSearchClient(
            RestClient.Builder builder,
            McpToolClient mcpToolClient,
            String endpoint,
            String apiKey) {
        this(builder, mcpToolClient, endpoint, apiKey, "", "");
    }

    public BailianWebSearchClient(
            RestClient.Builder builder,
            McpToolClient mcpToolClient,
            String endpoint,
            String apiKey,
            String fallbackBaseUrl,
            String fallbackModel) {
        this.mcpToolClient = mcpToolClient;
        this.endpoint = endpoint == null ? "" : endpoint.strip();
        this.apiKey = apiKey == null ? "" : apiKey.strip();
        this.fallbackBaseUrl = stripTrailingSlash(fallbackBaseUrl);
        this.fallbackModel = fallbackModel == null || fallbackModel.isBlank() ? "qwen3.7-max-2026-06-08" : fallbackModel.strip();
        this.restClient = builder.build();
    }

    @Override
    public List<WebSearchResult> search(String query, int limit, String freshness, String language) {
        if (endpoint.isBlank() || apiKey.isBlank()) {
            throw new WebToolException("百炼 WebSearch 未配置 endpoint 或 api key");
        }
        List<WebSearchResult> results = searchByMcp(query, limit, freshness, language);
        if (results.isEmpty()) {
            log.warn("百炼 MCP WebSearch 未返回可用结果，准备 fallback 到 Chat Completions 联网搜索，query={}", safeQueryTitle(query));
            results = fallbackSearch(query, limit);
        }
        if (results.isEmpty()) {
            throw new WebToolException("百炼 WebSearch 未返回可用搜索结果");
        }
        return results.stream().limit(Math.max(1, limit)).toList();
    }

    private List<WebSearchResult> searchByMcp(String query, int limit, String freshness, String language) {
        try {
            JsonNode root = mcpToolClient.callTool(
                    endpoint,
                    apiKey,
                    "bailian_web_search",
                    mcpArguments(query, limit, freshness, language)).result();
            return parseResults(root);
        } catch (WebToolException | RestClientException exception) {
            log.warn("百炼 MCP WebSearch 调用失败，准备 fallback，query={}, error={}", safeQueryTitle(query), exception.getMessage());
            return List.of();
        }
    }

    private Map<String, Object> mcpArguments(String query, int limit, String freshness, String language) {
        Map<String, Object> arguments = new LinkedHashMap<>();
        arguments.put("query", query == null ? "" : query.strip());
        arguments.put("count", Math.max(1, limit));
        arguments.put("freshness", freshness == null || freshness.isBlank() ? "any" : freshness.strip());
        arguments.put("language", language == null || language.isBlank() ? "zh-CN" : language.strip());
        return arguments;
    }

    private List<WebSearchResult> fallbackSearch(String query, int limit) {
        if (fallbackBaseUrl.isBlank()) {
            return List.of();
        }
        try {
            JsonNode root = restClient.post()
                    .uri(fallbackBaseUrl + "/chat/completions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .headers(headers -> headers.setBearerAuth(apiKey))
                    .body(fallbackRequestBody(query))
                    .retrieve()
                    .body(JsonNode.class);
            String content = root == null ? "" : root.path("choices").path(0).path("message").path("content").asText("");
            if (content.isBlank()) {
                return List.of();
            }
            List<WebSearchResult> results = new ArrayList<>();
            collectResultsFromText(content, results, new HashSet<>());
            if (!results.isEmpty()) {
                return results.stream().limit(Math.max(1, limit)).toList();
            }
            return List.of(new WebSearchResult(
                    "联网搜索摘要：" + safeQueryTitle(query),
                    "",
                    content.strip(),
                    "DashScope WebSearch",
                    ""));
        } catch (RestClientException exception) {
            return List.of();
        }
    }

    private Map<String, Object> fallbackRequestBody(String query) {
        Map<String, Object> searchOptions = new LinkedHashMap<>();
        searchOptions.put("forced_search", true);

        Map<String, Object> extraBody = new LinkedHashMap<>();
        extraBody.put("enable_thinking", false);
        extraBody.put("enable_search", true);
        extraBody.put("search_options", searchOptions);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", fallbackModel);
        body.put("messages", List.of(
                Map.of("role", "system", "content", "你是网页搜索工具。请进行联网搜索，并用中文给出精炼摘要；如果能给出来源链接，请用 Markdown 链接格式返回。"),
                Map.of("role", "user", "content", query == null ? "" : query.strip())));
        body.put("extra_body", extraBody);
        body.put("stream", false);
        return body;
    }

    private List<WebSearchResult> parseResults(JsonNode root) {
        List<WebSearchResult> results = new ArrayList<>();
        collectSearchResults(root, results, new HashSet<>());
        return results;
    }

    private void collectSearchResults(JsonNode node, List<WebSearchResult> results, Set<String> visitedText) {
        if (node == null || node.isNull()) {
            return;
        }
        if (node.isArray()) {
            for (JsonNode child : node) {
                collectSearchResults(child, results, visitedText);
            }
            return;
        }
        if (node.isValueNode()) {
            collectResultsFromText(node.asText(""), results, visitedText);
            return;
        }
        if (!node.isObject()) {
            return;
        }
        String url = firstText(node, "url", "link", "href", "pageUrl", "page_url", "page_url_mobile");
        String title = firstText(node, "title", "name", "pageTitle", "page_title");
        String snippet = firstText(node, "snippet", "summary", "description", "content", "text");
        if (!url.isBlank() && !title.isBlank()) {
            results.add(new WebSearchResult(title, url, snippet, firstText(node, "source", "site"), firstText(node, "published_at", "date", "time")));
        }
        node.fields().forEachRemaining(entry -> collectSearchResults(entry.getValue(), results, visitedText));
    }

    private void collectResultsFromText(String value, List<WebSearchResult> results, Set<String> visitedText) {
        String text = value == null ? "" : value.strip();
        if (text.isBlank() || !visitedText.add(text)) {
            return;
        }
        if ((text.startsWith("{") && text.endsWith("}")) || (text.startsWith("[") && text.endsWith("]"))) {
            try {
                collectSearchResults(OBJECT_MAPPER.readTree(text), results, visitedText);
                return;
            } catch (Exception ignored) {
                // 如果不是合法 JSON，则继续按普通文本解析 Markdown 链接。
            }
        }
        Matcher matcher = MARKDOWN_LINK.matcher(text);
        while (matcher.find()) {
            results.add(new WebSearchResult(
                    matcher.group(1).strip(),
                    matcher.group(2).strip(),
                    surroundingText(text, matcher.start(), matcher.end()),
                    "",
                    ""));
        }
    }

    private String surroundingText(String text, int start, int end) {
        int from = Math.max(0, start - 120);
        int to = Math.min(text.length(), end + 220);
        return text.substring(from, to).replaceAll("\\s+", " ").strip();
    }

    private String firstText(JsonNode node, String... names) {
        for (String name : names) {
            JsonNode value = node.get(name);
            if (value != null && value.isValueNode() && !value.asText("").isBlank()) {
                return value.asText("").strip();
            }
        }
        return "";
    }

    private String safeQueryTitle(String query) {
        String value = query == null ? "" : query.strip();
        return value.length() <= 80 ? value : value.substring(0, 80);
    }

    private String stripTrailingSlash(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String result = value.strip();
        while (result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }
}
