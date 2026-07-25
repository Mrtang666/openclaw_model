package com.example.spring.wechat.web.mcp;

import java.util.Map;

/**
 * MCP 工具客户端抽象。
 */
public interface McpToolClient {

    McpCallResult callTool(String endpoint, String apiKey, String toolName, Map<String, Object> arguments);
}
