package com.example.spring.wechat.context;

import com.example.spring.chat.ChatService;
import com.example.spring.wechat.memory.model.ConversationTurn;
import com.example.spring.wechat.memory.model.WechatConversationMemory;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SlidingWindowSummaryService {

    private final ChatService chatService;

    public SlidingWindowSummaryService(ChatService chatService) {
        this.chatService = chatService;
    }

    public String summarizeOutsideRecentWindow(WechatConversationMemory memory, int recentTurns) {
        if (chatService == null || memory == null) {
            return "";
        }
        List<ConversationTurn> all = memory.snapshot();
        int keep = Math.max(1, recentTurns);
        int end = Math.max(0, all.size() - keep);
        if (end == 0) {
            return "";
        }
        String transcript = format(all.subList(0, end));
        if (transcript.isBlank()) {
            return "";
        }
        String reply = chatService.reply(prompt(transcript));
        return reply == null ? "" : reply.strip();
    }

    private String prompt(String transcript) {
        return """
                你是微信 Agent 的滑动窗口摘要器。
                只保留用户明确事实、偏好、当前目标、已完成事项、未完成事项、待确认问题、关键工具结果。
                删除寒暄、重复确认、临时噪声、系统提示词、敏感凭据。

                需要摘要的窗口外对话：
                %s
                """.formatted(transcript);
    }

    private String format(List<ConversationTurn> turns) {
        StringBuilder text = new StringBuilder();
        for (ConversationTurn turn : turns) {
            if (!text.isEmpty()) {
                text.append(System.lineSeparator());
            }
            text.append("用户：").append(turn.userText()).append(System.lineSeparator())
                    .append("助手：").append(turn.assistantText());
        }
        return text.toString();
    }
}
