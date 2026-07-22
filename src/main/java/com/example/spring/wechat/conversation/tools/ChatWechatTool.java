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
    public List<WechatToolParameter> parameters() {
        return List.of(WechatToolParameter.requiredString(
                "message",
                "用户要让普通大模型处理的文本任务；只有无法归类到专用工具时才使用 chat",
                "帮我写一段自我介绍"));
    }

    @Override
    public WechatToolCapability capability() {
        return new WechatToolCapability(
                "处理不需要专用工具的普通文本对话、写作、总结、计划和解释类需求。",
                List.of("如果用户需求明确属于天气、图片、语音、音色、文件等专用能力，不要优先使用 chat。"),
                List.of("message：用户要普通大模型处理的文本任务"),
                List.of("文本回复"));
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

