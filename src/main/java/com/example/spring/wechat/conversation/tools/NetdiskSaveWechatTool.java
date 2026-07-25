package com.example.spring.wechat.conversation.tools;

import com.example.spring.wechat.bot.WechatReply;
import com.example.spring.wechat.netdisk.service.NetdiskToolService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 保存文本内容到百度网盘的工具。
 */
@Component
@ConditionalOnProperty(prefix = "baidu.netdisk", name = "enabled", havingValue = "true")
public class NetdiskSaveWechatTool implements WechatTool {

    private final NetdiskToolService netdiskToolService;

    public NetdiskSaveWechatTool(NetdiskToolService netdiskToolService) {
        this.netdiskToolService = netdiskToolService;
    }

    @Override
    public String name() {
        return "netdisk_save";
    }

    @Override
    public String description() {
        return "把 AI 助手生成的文本内容保存为 Markdown 文本文件到当前用户自己的百度网盘";
    }

    @Override
    public List<String> arguments() {
        return List.of("content", "dir", "filename");
    }

    @Override
    public List<WechatToolParameter> parameters() {
        return List.of(
                WechatToolParameter.requiredString("content", "要保存到网盘的文本内容", "这是一段总结内容"),
                WechatToolParameter.optionalString("dir", "保存目录，默认 /OpenClaw/", "/OpenClaw/"),
                WechatToolParameter.optionalString("filename", "文件名，默认 openclaw-note.md", "summary.md"));
    }

    @Override
    public WechatReply execute(WechatToolRequest request) {
        String content = request.argument("content");
        if (content.isBlank()) {
            content = request.userText();
        }
        return WechatReply.text(netdiskToolService.saveText(
                request.sessionKey(),
                content,
                request.argument("dir"),
                request.argument("filename")));
    }
}
