package com.example.spring.wechat.context;

import com.example.spring.wechat.conversation.WechatConversationMode;
import com.example.spring.wechat.memory.model.WechatConversationMemory;

public record ContextBuildRequest(
        String sessionKey,
        String userText,
        WechatConversationMemory memory,
        String resourceContext,
        String ragContext,
        WechatConversationMode conversationMode) {

    public ContextBuildRequest {
        sessionKey = sessionKey == null ? "" : sessionKey.strip();
        userText = userText == null ? "" : userText.strip();
        resourceContext = resourceContext == null ? "" : resourceContext.strip();
        ragContext = ragContext == null ? "" : ragContext.strip();
        conversationMode = conversationMode == null ? WechatConversationMode.GENERAL : conversationMode;
    }
}
