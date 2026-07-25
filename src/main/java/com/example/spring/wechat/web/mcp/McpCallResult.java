package com.example.spring.wechat.web.mcp;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * MCP 工具调用结果。
 */
public record McpCallResult(
        JsonNode result,
        String sessionId) {
}
