package com.example.spring.wechat.conversation.tools;

import com.example.spring.wechat.bot.WechatReply;
import com.example.spring.wechat.netdisk.service.NetdiskToolService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 百度网盘分享链接工具。
 */
@Component
@ConditionalOnProperty(prefix = "baidu.netdisk", name = "enabled", havingValue = "true")
public class NetdiskShareWechatTool implements WechatTool {

    private final NetdiskToolService netdiskToolService;

    public NetdiskShareWechatTool(NetdiskToolService netdiskToolService) {
        this.netdiskToolService = netdiskToolService;
    }

    @Override
    public String name() {
        return "netdisk_share";
    }

    @Override
    public String description() {
        return "为当前微信用户百度网盘中的文件生成分享链接";
    }

    @Override
    public List<String> arguments() {
        return List.of("fsid_list", "period", "pwd");
    }

    @Override
    public List<WechatToolParameter> parameters() {
        return List.of(
                WechatToolParameter.requiredString("fsid_list", "文件 fsid 列表，多个用逗号分隔", "123456"),
                WechatToolParameter.optionalString("period", "分享有效期，0 通常表示永久，具体以百度返回为准", "0"),
                WechatToolParameter.optionalString("pwd", "分享提取码，可选", "abcd"));
    }

    @Override
    public WechatReply execute(WechatToolRequest request) {
        return WechatReply.text(netdiskToolService.share(
                request.sessionKey(),
                request.argument("fsid_list"),
                parseInt(request.argument("period"), 0),
                request.argument("pwd")));
    }

    private int parseInt(String value, int fallback) {
        try {
            return value == null || value.isBlank() ? fallback : Integer.parseInt(value.strip());
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }
}
