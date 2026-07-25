package com.example.spring.wechat.netdisk.client;

import com.example.spring.wechat.netdisk.config.BaiduNetdiskProperties;
import com.example.spring.wechat.netdisk.exception.NetdiskToolException;
import com.example.spring.wechat.web.mcp.McpToolClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 百度网盘 MCP 工具客户端。
 *
 * <p>这一层只负责把项目内部的 search/list/share/saveText 调用，
 * 转换成百度网盘 MCP 官方工具名和参数。具体 MCP 传输由 McpToolClient 负责。</p>
 */
@Component
public class BaiduNetdiskMcpClient {

    private final McpToolClient mcpToolClient;
    private final ObjectMapper objectMapper;
    private final BaiduNetdiskProperties properties;

    public BaiduNetdiskMcpClient(
            McpToolClient mcpToolClient,
            ObjectMapper objectMapper,
            BaiduNetdiskProperties properties) {
        this.mcpToolClient = mcpToolClient;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    public JsonNode keywordSearch(String accessToken, String key, String dir, int num) {
        Map<String, Object> arguments = new LinkedHashMap<>();
        arguments.put("key", safe(key));
        arguments.put("dir", defaultText(dir, "/"));
        arguments.put("num", normalizeLimit(num));
        return call(accessToken, "file_keyword_search", arguments);
    }

    public JsonNode semanticSearch(String accessToken, String query, String dir, int num) {
        Map<String, Object> arguments = new LinkedHashMap<>();
        arguments.put("query", safe(query));
        arguments.put("dir", defaultText(dir, "/"));
        arguments.put("num", normalizeLimit(num));
        return call(accessToken, "file_semantics_search", arguments);
    }

    public JsonNode list(String accessToken, String dir, int page) {
        Map<String, Object> arguments = new LinkedHashMap<>();
        arguments.put("dir", defaultText(dir, "/"));
        arguments.put("page", page <= 0 ? 1 : page);
        return call(accessToken, "file_list", arguments);
    }

    public JsonNode share(String accessToken, String fsidList, int period, String pwd) {
        Map<String, Object> arguments = new LinkedHashMap<>();
        arguments.put("fsid_list", safe(fsidList));
        arguments.put("period", period <= 0 ? 0 : period);
        if (pwd != null && !pwd.isBlank()) {
            arguments.put("pwd", pwd.strip());
        }
        return call(accessToken, "file_sharelink_set", arguments);
    }

    public JsonNode uploadText(String accessToken, String content, String dir, String filename) {
        Map<String, Object> arguments = new LinkedHashMap<>();
        arguments.put("content", safe(content));
        arguments.put("dir", defaultText(dir, properties.defaultUploadPath()));
        arguments.put("filename", defaultText(filename, "openclaw-note.md"));
        return call(accessToken, "file_upload_by_text", arguments);
    }

    private JsonNode call(String accessToken, String toolName, Map<String, Object> arguments) {
        if (accessToken == null || accessToken.isBlank()) {
            throw new NetdiskToolException("百度网盘 access_token 为空，请重新授权");
        }
        return mcpToolClient.callTool(endpointWithAccessToken(accessToken), accessToken, toolName, arguments).result();
    }

    private String endpointWithAccessToken(String accessToken) {
        String base = properties.mcpSseBaseUrl();
        String separator = base.contains("?") ? "&" : "?";
        return base + separator + "access_token=" + UriUtils.encodeQueryParam(accessToken, StandardCharsets.UTF_8);
    }

    public String pretty(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return "";
        }
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(node);
        } catch (Exception exception) {
            return node.toString();
        }
    }

    private int normalizeLimit(int value) {
        if (value <= 0) {
            return properties.contextLimit();
        }
        return Math.min(value, 20);
    }

    private String defaultText(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.strip();
    }

    private String safe(String value) {
        return value == null ? "" : value.strip();
    }
}
