package com.example.spring.speech;

import com.example.spring.memory.ConversationMemoryService;
import org.springframework.stereotype.Service;

@Service
public class ReadAloudService {
    private final ConversationMemoryService memoryService;

    public ReadAloudService(ConversationMemoryService memoryService) {
        this.memoryService = memoryService;
    }

    public ReadAloudRequest resolve(String userId, String requestText) {
        if (!ReadAloudIntentParser.isReadAloudIntent(requestText)) {
            return ReadAloudRequest.ignored();
        }
        String inline = ReadAloudIntentParser.extractInlineText(requestText);
        if (!inline.isBlank()) {
            return ReadAloudRequest.resolved(inline);
        }
        String latestAssistantText = memoryService.getLatestAssistantText(userId);
        if (latestAssistantText == null || latestAssistantText.isBlank()) {
            return ReadAloudRequest.unresolved(
                "没有找到可以朗读的历史文字，请把需要朗读的内容直接发给我。");
        }
        return ReadAloudRequest.resolved(latestAssistantText);
    }
}
