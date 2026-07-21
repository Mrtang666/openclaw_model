package com.example.spring.wechat.conversation.tools;

import com.example.spring.chat.ChatService;
import com.example.spring.wechat.image.generation.model.ImageGenerationRequest;
import com.example.spring.wechat.image.generation.model.ImageGenerationResult;
import com.example.spring.wechat.image.generation.service.ImageGenerationService;
import com.example.spring.wechat.bot.WechatReply;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 微信图片生成工具。
 * 负责把用户原始需求、历史上下文和可选参考图片整理成图片提示词，
 * 再调用图片生成服务得到图片结果，并返回给微信回复流程发送。
 */
@Component
public class ImageGenerationWechatTool implements WechatTool {

    private static final Logger log = LoggerFactory.getLogger(ImageGenerationWechatTool.class);

    private final ChatService chatService;
    private final ImageGenerationService imageGenerationService;

    public ImageGenerationWechatTool(ChatService chatService, ImageGenerationService imageGenerationService) {
        this.chatService = chatService;
        this.imageGenerationService = imageGenerationService;
    }

    @Override
    public String name() {
        return "image_generation";
    }

    @Override
    public String description() {
        return "生成图片；支持先优化提示词、等待用户确认后再生成、根据文字提示生成图片";
    }

    @Override
    public List<String> arguments() {
        return List.of("prompt", "optimize_prompt", "wait_for_approval");
    }

    @Override
    public WechatReply execute(WechatToolRequest request) {
        String roughPrompt = firstNonBlank(request.argument("prompt"), request.argument("message"), request.userText());
        if (roughPrompt.isBlank()) {
            return WechatReply.text("请告诉我你想生成什么图片。");
        }

        boolean optimizePrompt = request.booleanArgument("optimize_prompt");
        String finalPrompt = optimizePrompt ? optimizeImagePrompt(request, roughPrompt) : roughPrompt;
        String optimizedText = "优化后的图片提示词：\n" + finalPrompt;

        if (request.booleanArgument("wait_for_approval")) {
            request.rememberPendingImagePrompt(finalPrompt);
            return WechatReply.text(optimizedText + "\n\n如果确认要生成，直接回复“可以生成了”就行。");
        }

        try {
            ImageGenerationResult image = imageGenerationService.generate(new ImageGenerationRequest(finalPrompt));
            request.rememberGeneratedImage(finalPrompt);
            return WechatReply.textsAndImage(
                    optimizePrompt ? List.of(optimizedText) : List.of(),
                    "我已经帮你生成好了，图片如下：",
                    image);
        } catch (RuntimeException exception) {
            log.warn("微信图片工具生成失败，prompt={}, error={}", preview(finalPrompt), rootMessage(exception));
            return WechatReply.text("图片生成失败，请稍后重试");
        }
    }

    private String optimizeImagePrompt(WechatToolRequest request, String roughPrompt) {
        try {
            String optimized = chatService.reply(buildOptimizationPrompt(request, roughPrompt));
            optimized = sanitizePrompt(optimized);
            return optimized.isBlank() ? roughPrompt : optimized;
        } catch (RuntimeException exception) {
            log.warn("微信图片工具提示词优化失败，prompt={}, error={}", preview(roughPrompt), rootMessage(exception));
            return roughPrompt;
        }
    }

    private String buildOptimizationPrompt(WechatToolRequest request, String roughPrompt) {
        return """
                你是图片提示词优化器。你的任务不是闲聊，而是把用户的图片需求改写成可直接交给图片生成模型的中文提示词。
                输出规则：
                1. 只输出最终图片提示词正文，不要输出解释、标题、编号、Markdown、引号。
                2. 提示词要具体描述主体、动作、场景、风格、光线、色彩、构图、镜头、画幅、画面质感。
                3. 不要把“先优化提示词”“再生成图片”“等我允许”这类流程性文字放进图片提示词。
                最近对话：
                %s
                用户当前原话：%s
                待优化的粗提示词：%s
                请输出优化后的最终图片提示词：
                """.formatted(request.historyText(), request.userText(), roughPrompt);
    }

    private String sanitizePrompt(String value) {
        if (value == null) {
            return "";
        }
        String text = value.strip();
        String[] prefixes = {
                "优化后的图片提示词：",
                "优化后的提示词：",
                "图片提示词：",
                "提示词：",
                "最终图片提示词：",
                "最终提示词："
        };
        boolean changed = true;
        while (changed) {
            changed = false;
            for (String prefix : prefixes) {
                if (text.startsWith(prefix)) {
                    text = text.substring(prefix.length()).strip();
                    changed = true;
                    break;
                }
            }
        }
        return text;
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

    private String preview(String value) {
        String text = value == null ? "" : value.strip();
        return text.length() <= 80 ? text : text.substring(0, 77) + "...";
    }

    private String rootMessage(Throwable exception) {
        Throwable current = exception;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }
}

