package com.example.spring.wechat.conversation.tools;

import com.example.spring.wechat.bot.WechatReply;
import com.example.spring.wechat.netdisk.service.NetdiskToolService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 百度网盘目录列表工具。
 */
@Component
@ConditionalOnProperty(prefix = "baidu.netdisk", name = "enabled", havingValue = "true")
public class NetdiskListWechatTool implements WechatTool {

    private final NetdiskToolService netdiskToolService;

    public NetdiskListWechatTool(NetdiskToolService netdiskToolService) {
        this.netdiskToolService = netdiskToolService;
    }

    @Override
    public String name() {
        return "netdisk_list";
    }

    @Override
    public String description() {
        return "列出当前微信用户百度网盘中指定目录的文件";
    }

    @Override
    public List<String> arguments() {
        return List.of("dir", "page");
    }

    @Override
    public List<WechatToolParameter> parameters() {
        return List.of(
                WechatToolParameter.optionalString("dir", "目录路径，默认根目录", "/"),
                WechatToolParameter.optionalString("page", "页码，默认 1", "1"));
    }

    @Override
    public WechatReply execute(WechatToolRequest request) {
        return WechatReply.text(netdiskToolService.list(
                request.sessionKey(),
                defaultText(request.argument("dir"), "/"),
                parseInt(request.argument("page"), 1)));
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
