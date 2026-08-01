package com.example.spring.wechat.conversation.tools;

import com.example.spring.wechat.bot.WechatReply;
import com.example.spring.wechat.netdisk.service.NetdiskToolService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 百度网盘授权工具。
 */
@Component
@ConditionalOnProperty(prefix = "baidu.netdisk", name = "enabled", havingValue = "true")
public class NetdiskAuthWechatTool implements WechatTool {

    private final NetdiskToolService netdiskToolService;

    public NetdiskAuthWechatTool(NetdiskToolService netdiskToolService) {
        this.netdiskToolService = netdiskToolService;
    }

    @Override
    public String name() {
        return "netdisk_auth";
    }

    @Override
    public String description() {
        return "绑定、重新绑定或查看当前微信用户的百度网盘授权状态";
    }

    @Override
    public List<String> arguments() {
        return List.of("operation");
    }

    @Override
    public List<WechatToolParameter> parameters() {
        return List.of(WechatToolParameter.optionalEnum(
                "operation",
                "授权操作，status 查看状态，bind 生成授权链接，rebind 重新绑定",
                List.of("status", "bind", "rebind"),
                "status"));
    }

    @Override
    public WechatToolCapability capability() {
        return new WechatToolCapability(
                "检查百度网盘授权状态或返回 OAuth 授权链接",
                List.of(),
                List.of(),
                List.of());
    }

    @Override
    public WechatReply execute(WechatToolRequest request) {
        return WechatReply.text(netdiskToolService.auth(request.sessionKey(), request.argument("operation")));
    }
}
