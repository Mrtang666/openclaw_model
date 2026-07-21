package com.example.spring.wechat.conversation.tools;


/**
 * CLI 工具封装层，负责统一封装本地工具能力。
 */
import com.example.spring.chat.ChatService;
import com.example.spring.wechat.bot.WechatReply;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ChatWechatTool implements WechatTool {

    private final ChatService chatService;

    public ChatWechatTool(ChatService chatService) {
        this.chatService = chatService;
    }

    @Override
    public String name() {
        return "chat";
    }

    @Override
    public String description() {
        return "普通大模型对话、总结、计划生成、无法归类到专用工具的文字任务";
    }

    @Override
    public List<String> arguments() {
        return List.of("message");
    }

    @Override
    public WechatReply execute(WechatToolRequest request) {
        String message = request.argument("message").isBlank() ? request.userText() : request.argument("message");
        StringBuilder output = new StringBuilder();
        chatService.streamReply(buildPrompt(request, message), output::append);
        return WechatReply.text(output.toString().strip());
    }

    private String buildPrompt(WechatToolRequest request, String message) {
        return """
                你是微信聊天助手，请结合最近对话上下文自然回复。
                最近对话：
                %s
                当前用户任务：%s
                请直接给出可发给用户的中文回复。
                """.formatted(request.historyText(), message);
    }
}

