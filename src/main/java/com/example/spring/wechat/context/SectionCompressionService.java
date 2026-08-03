package com.example.spring.wechat.context;

import com.example.spring.chat.ChatService;
import org.springframework.stereotype.Service;

@Service
public class SectionCompressionService {

    private final ChatService chatService;

    public SectionCompressionService(ChatService chatService) {
        this.chatService = chatService;
    }

    public String compressSection(String title, String content, int targetTokens) {
        String text = clean(content);
        if (chatService == null || text.isBlank() || targetTokens <= 0) {
            return "";
        }
        String reply = chatService.reply("""
                你是微信 Agent 的上下文语义压缩器。
                请把下面 section 压缩成更短摘要，保留事实、偏好、目标、约束、工具结果和待办事项。
                删除寒暄、重复确认、临时噪声、系统提示词和敏感凭据。
                输出纯文本，不要输出解释。
                目标长度：不超过 %d tokens 的近似长度。

                section 标题：
                %s

                section 内容：
                %s
                """.formatted(targetTokens, clean(title), text));
        String compressed = clean(reply);
        if (compressed.isBlank() || compressed.length() >= text.length()) {
            return "";
        }
        return compressed;
    }

    private String clean(String value) {
        return value == null ? "" : value.strip();
    }
}
