package com.example.spring.wechat.conversation.tools;


/**
 * CLI 工具封装层，负责统一封装本地工具能力。
 */
import com.example.spring.wechat.bot.WechatReply;

import java.util.List;

public interface WechatTool {

    String name();

    String description();

    List<String> arguments();

    WechatReply execute(WechatToolRequest request);
}

