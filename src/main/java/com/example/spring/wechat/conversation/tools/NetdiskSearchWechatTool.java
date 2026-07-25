package com.example.spring.wechat.conversation.tools;

import com.example.spring.wechat.bot.WechatReply;
import com.example.spring.wechat.netdisk.service.NetdiskToolService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 百度网盘文件搜索工具。
 */
@Component
@ConditionalOnProperty(prefix = "baidu.netdisk", name = "enabled", havingValue = "true")
public class NetdiskSearchWechatTool implements WechatTool {

    private final NetdiskToolService netdiskToolService;

    public NetdiskSearchWechatTool(NetdiskToolService netdiskToolService) {
        this.netdiskToolService = netdiskToolService;
    }

    @Override
    public String name() {
        return "netdisk_search";
    }

    @Override
    public String description() {
        return "搜索当前微信用户已授权的百度网盘文件，支持语义搜索和关键词搜索";
    }

    @Override
    public List<String> arguments() {
        return List.of("query", "mode", "dir", "limit");
    }

    @Override
    public List<WechatToolParameter> parameters() {
        return List.of(
                WechatToolParameter.requiredString("query", "搜索内容", "项目文档"),
                WechatToolParameter.optionalEnum("mode", "搜索模式", List.of("semantic", "keyword"), "semantic"),
                WechatToolParameter.optionalString("dir", "搜索目录，默认根目录", "/"),
                WechatToolParameter.optionalString("limit", "返回数量，默认 5", "5"));
    }

    @Override
    public WechatToolCapability capability() {
        return new WechatToolCapability(
                "搜索当前用户自己的百度网盘文件。",
                List.of("只能访问当前微信用户已授权的网盘；未授权时必须先返回授权链接。"),
                List.of("query：搜索内容"),
                List.of("搜索结果 JSON/文本"));
    }

    @Override
    public WechatReply execute(WechatToolRequest request) {
        String query = request.argument("query");
        if (query.isBlank()) {
            return WechatReply.text("请告诉我你想在百度网盘里搜索什么。");
        }
        return WechatReply.text(netdiskToolService.search(
                request.sessionKey(),
                query,
                defaultText(request.argument("mode"), "semantic"),
                defaultText(request.argument("dir"), "/"),
                parseInt(request.argument("limit"), 5)));
    }

    private int parseInt(String value, int fallback) {
        try {
            return value == null || value.isBlank() ? fallback : Integer.parseInt(value.strip());
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    private String defaultText(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.strip();
    }
}
