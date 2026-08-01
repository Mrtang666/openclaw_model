package com.example.spring.wechat.conversation.tools;

import com.example.spring.wechat.bot.WechatReply;
import com.example.spring.wechat.video.VideoUnderstandingException;
import com.example.spring.wechat.video.client.VideoUnderstandingClient;
import com.example.spring.wechat.video.model.VideoUnderstandingRequest;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class VideoUnderstandWechatTool implements WechatTool {

    private final VideoUnderstandingClient videoUnderstandingClient;

    public VideoUnderstandWechatTool(VideoUnderstandingClient videoUnderstandingClient) {
        this.videoUnderstandingClient = videoUnderstandingClient;
    }

    @Override
    public String name() {
        return "video_understand";
    }

    @Override
    public String description() {
        return "识别、描述和分析用户发送的视频，支持场景描述、关键事件提取和自定义问题分析。";
    }

    @Override
    public List<String> arguments() {
        return List.of("action", "custom_prompt");
    }

    @Override
    public List<WechatToolParameter> parameters() {
        return List.of(
                WechatToolParameter.optionalEnum(
                        "action",
                        "视频处理方式",
                        List.of("describe", "analyze", "extract", "custom"),
                        "analyze"),
                WechatToolParameter.optionalString(
                        "custom_prompt",
                        "用户对视频提出的自定义分析问题",
                        "这个视频里的人物动作标准吗？"));
    }

    @Override
    public WechatToolCapability capability() {
        return new WechatToolCapability(
                "理解微信视频并回答内容相关问题",
                List.of(),
                List.of(),
                List.of());
    }

    @Override
    public WechatReply execute(WechatToolRequest request) {
        if (request.videos().isEmpty()) {
            return WechatReply.text("我还没有收到可分析的视频，请先发送视频。");
        }
        String instruction = buildInstruction(request);
        try {
            String reply = videoUnderstandingClient.reply(new VideoUnderstandingRequest(instruction, request.videos()));
            return WechatReply.text(reply);
        } catch (VideoUnderstandingException exception) {
            return WechatReply.text("视频分析失败：" + messageOrDefault(exception, "请稍后重试。"));
        }
    }

    private String buildInstruction(WechatToolRequest request) {
        String customPrompt = firstNonBlank(request.argument("custom_prompt"), request.argument("question"), request.userText());
        String action = firstNonBlank(request.argument("action"), inferAction(customPrompt));
        if ("custom".equalsIgnoreCase(action) && !customPrompt.isBlank()) {
            return customPrompt;
        }
        if (!customPrompt.isBlank() && !"describe".equalsIgnoreCase(action)) {
            return customPrompt;
        }
        return switch (action.toLowerCase(java.util.Locale.ROOT)) {
            case "describe" -> "请客观描述这个视频的画面内容，包括场景、人物、物体、动作、顺序和明显变化。";
            case "extract" -> "请从这个视频中提取关键信息，按时间顺序列出关键事件、重要对象和可见文字。";
            case "analyze" -> "请深度分析这个视频：先描述内容，再总结关键事件，最后给出结论和建议。";
            default -> customPrompt.isBlank()
                    ? "请分析这个视频，并给出清晰、简洁、可直接发给微信用户的回答。"
                    : customPrompt;
        };
    }

    private String inferAction(String text) {
        String value = text == null ? "" : text.strip().toLowerCase(java.util.Locale.ROOT);
        if (value.contains("描述") || value.contains("看见") || value.contains("what do you see")) {
            return "describe";
        }
        if (value.contains("提取") || value.contains("关键信息") || value.contains("重点")) {
            return "extract";
        }
        if (!value.isBlank()) {
            return "custom";
        }
        return "analyze";
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.strip();
            }
        }
        return "";
    }

    private String messageOrDefault(Exception exception, String defaultMessage) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? defaultMessage : message;
    }
}
