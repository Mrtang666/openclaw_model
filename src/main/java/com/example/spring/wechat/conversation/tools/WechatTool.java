package com.example.spring.wechat.conversation.tools;


/**
 * 微信工具接口定义，负责统一封装微信工具能力。
 */
import com.example.spring.wechat.bot.WechatReply;

import java.util.List;

public interface WechatTool {
// 工具名字
    String name();
// 工具描述
    String description();
// 旧版工具参数
    List<String> arguments();
//标准化参数定义
    default List<WechatToolParameter> parameters() {
        return arguments().stream()
                .map(argument -> WechatToolParameter.optionalString(argument, "", ""))
                .toList();
    }
//工具能力边界说明
    default WechatToolCapability capability() {
        return WechatToolCapability.empty();
    }
//执行工具
    WechatReply execute(WechatToolRequest request);
}

