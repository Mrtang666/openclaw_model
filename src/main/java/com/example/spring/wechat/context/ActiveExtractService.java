package com.example.spring.wechat.context;

import com.example.spring.chat.ChatService;
import org.springframework.stereotype.Service;

@Service
public class ActiveExtractService {

    private final ChatService chatService;

    public ActiveExtractService(ChatService chatService) {
        this.chatService = chatService;
    }

    public String extract(String currentTopic, String summaryOrTranscript) {
        if (chatService == null || currentTopic == null || currentTopic.isBlank()
                || summaryOrTranscript == null || summaryOrTranscript.isBlank()) {
            return "";
        }
        String reply = chatService.reply("""
                你是微信 Agent 的活摘抽取器。
                只抽取与当前主题强相关的信息。删除其他主题、寒暄、临时噪声和敏感凭据。

                当前主题：
                %s

                候选摘要或对话：
                %s
                """.formatted(currentTopic.strip(), summaryOrTranscript.strip()));
        return reply == null ? "" : reply.strip();
    }
}
