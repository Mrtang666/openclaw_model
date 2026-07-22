package com.example.feature.image;

import com.example.adapter.wechat.WechatMessageSender;
import com.example.application.ReplyOrchestrator;
import com.example.guidance.GuidedConversationService;
import com.example.imagegen.ImageGenService;
import com.github.wechat.ilink.sdk.ILinkClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Owns image generation, aspect-ratio selection, delivery, and poster review state. */
public class ImageHandler {

    private static final Logger log = LoggerFactory.getLogger(ImageHandler.class);
    private static final Pattern ASPECT_RATIO = Pattern.compile(
            "(?<!\\d)(\\d{1,2})\\s*[:：]\\s*(\\d{1,2})(?!\\d)");

    private final ImageGenService imageGenService;
    private final WechatMessageSender sender;
    private final ReplyOrchestrator replies;
    private final GuidedConversationService guidedConversationService;

    public ImageHandler(ImageGenService imageGenService,
                        WechatMessageSender sender,
                        ReplyOrchestrator replies,
                        GuidedConversationService guidedConversationService) {
        this.imageGenService = imageGenService;
        this.sender = sender;
        this.replies = replies;
        this.guidedConversationService = guidedConversationService;
    }

    public void generateAndSend(ILinkClient client, String userId, String prompt) {
        generateAndSend(client, userId, prompt, false);
    }

    public void generateAndSend(ILinkClient client, String userId, String prompt, boolean poster) {
        String size = resolveImageSize(prompt, poster);
        log.info("图片生成尺寸: user={}, size={}, prompt={}", userId, size, prompt);
        ImageGenService.ImageGenResult result = imageGenService.generate(prompt, 1, size);
        if (!result.isSuccess()) {
            replies.reply(client, userId, "图片生成失败: " + result.getMessage());
            return;
        }

        try {
            byte[] imageBytes = Files.readAllBytes(result.getFilePath());
            sender.sendImage(client, userId, imageBytes,
                    result.getFilePath().getFileName().toString(), null);
            log.info("已通过 CDN 发送图片给用户 [{}]", userId);
            if (poster) {
                guidedConversationService.markPosterGenerated(userId, prompt);
                replies.reply(client, userId,
                        "海报已经生成并发给你了。请问你满意吗？如果不满意，直接告诉我需要修改的地方。");
            }
        } catch (Exception e) {
            log.warn("发送图片失败: {}", e.getMessage());
            replies.reply(client, userId,
                    "图片已保存到本地，但发送失败，请在服务器查看 downloads/imagegen 目录。");
        }
    }

    private String resolveImageSize(String prompt, boolean poster) {
        String value = prompt == null ? "" : prompt;
        Matcher ratioMatcher = ASPECT_RATIO.matcher(value);
        if (ratioMatcher.find()) {
            return sizeForRatio(Integer.parseInt(ratioMatcher.group(1)),
                    Integer.parseInt(ratioMatcher.group(2)));
        }
        if (value.matches("(?s).*(横版|横向|宽屏|电影感|电脑壁纸|16比).*")) {
            return "1024x576";
        }
        if (value.matches("(?s).*(竖版|竖向|手机壁纸|长图|9比6).*")) {
            return "576x1024";
        }
        if (value.matches("(?s).*(头像|图标|方形|正方形).*")) {
            return "1024x1024";
        }
        if (poster || value.contains("海报") || value.contains("小红书")) {
            return "768x1024";
        }
        return "1024x1024";
    }

    private String sizeForRatio(int width, int height) {
        if (width <= 0 || height <= 0) return "1024x1024";
        double ratio = width / (double) height;
        if (Math.abs(ratio - 1.0) < 0.08) return "1024x1024";
        if (Math.abs(ratio - 0.75) < 0.10) return "768x1024";
        if (Math.abs(ratio - 1.3333) < 0.12) return "1024x768";
        if (Math.abs(ratio - 1.7777) < 0.15) return "1024x576";
        if (Math.abs(ratio - 0.5625) < 0.12) return "576x1024";
        return ratio < 1.0 ? "768x1024" : "1024x768";
    }
}
