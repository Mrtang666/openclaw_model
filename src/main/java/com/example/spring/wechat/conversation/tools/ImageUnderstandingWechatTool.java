package com.example.spring.wechat.conversation.tools;

import com.example.spring.agent.ReplyEmitter;
import com.example.spring.wechat.bot.WechatReply;
import com.example.spring.wechat.image.archive.ImageArchiveService;
import com.example.spring.wechat.image.exception.ImageUnderstandingException;
import com.example.spring.wechat.image.service.ImageUnderstandingService;
import com.example.spring.wechat.model.WechatIncomingImage;
import com.example.spring.wechat.model.WechatIncomingMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 微信图片理解工具。
 *
 * <p>它把当前会话里可用的图片资源交给视觉模型分析，并且内置 5 张一批的分批处理。
 * 这样用户连续发送多张图片后，再说“帮我总结这些图”，模型就可以像调用天气工具一样调用本工具。</p>
 */
@Component
public class ImageUnderstandingWechatTool implements WechatTool {

    private static final Logger log = LoggerFactory.getLogger(ImageUnderstandingWechatTool.class);

    private final ImageUnderstandingService imageUnderstandingService;
    private final ImageArchiveService imageArchiveService;

    public ImageUnderstandingWechatTool(
            ImageUnderstandingService imageUnderstandingService,
            ImageArchiveService imageArchiveService) {
        this.imageUnderstandingService = imageUnderstandingService;
        this.imageArchiveService = imageArchiveService;
    }

    @Override
    public String name() {
        return "image_understanding";
    }

    @Override
    public String description() {
        return "识别、解析、总结用户发来的图片，支持多图按批处理，也可根据用户文字需求回答图片相关问题";
    }

    @Override
    public List<String> arguments() {
        return List.of("instruction");
    }

    @Override
    public List<WechatToolParameter> parameters() {
        return List.of(
                WechatToolParameter.requiredString(
                        "instruction",
                        "用户希望如何处理图片，例如描述图片、提取图片文字、比较多张图片、根据图片提出建议等",
                        "请描述这张图片并提取里面的文字"));
    }

    @Override
    public WechatToolCapability capability() {
        return new WechatToolCapability(
                "当用户围绕图片提问、要求识别图片、总结图片、提取图片文字、比较多张图片或基于图片给建议时使用。",
                List.of(
                        "如果当前没有可用图片资源，不能编造图片内容，需要追问用户先发送图片。",
                        "一次最多处理 5 张图片；超过 5 张时按 5 张一批依次处理并合并结果。",
                        "回答要先说明识别到的图片内容，再结合用户要求给出结论。"),
                List.of("instruction：用户对图片的具体处理要求"),
                List.of("图片内容描述", "图片文字提取结果", "基于图片的分析建议"));
    }

    @Override
    public WechatReply execute(WechatToolRequest request) {
        List<WechatIncomingImage> images = request.images();
        if (images.isEmpty()) {
            return WechatReply.text("我现在还没有可处理的图片，请先把图片发给我。");
        }
        if (imageUnderstandingService == null) {
            return WechatReply.text("图片识别服务暂未配置。");
        }

        String instruction = firstNonBlank(request.argument("instruction"), request.userText(), "请描述这些图片。");
        List<List<WechatIncomingImage>> batches = imageArchiveService.batches(images);
        List<String> batchReplies = new ArrayList<>();
        for (int i = 0; i < batches.size(); i++) {
            List<WechatIncomingImage> batch = batches.get(i);
            String batchInstruction = batches.size() == 1
                    ? instruction
                    : "这是第 %d 批图片，本批共 %d 张。%s".formatted(i + 1, batch.size(), instruction);
            WechatIncomingMessage imageMessage = new WechatIncomingMessage(
                    request.sessionKey(),
                    batchInstruction,
                    batch);
            try {
                StringBuilder reply = new StringBuilder();
                ReplyEmitter emitter = chunk -> {
                    if (chunk != null) {
                        reply.append(chunk);
                    }
                };
                imageUnderstandingService.streamReply(imageMessage, emitter);
                if (!reply.toString().isBlank()) {
                    batchReplies.add(reply.toString().strip());
                }
            } catch (ImageUnderstandingException exception) {
                log.warn("微信图片工具识别失败，userId={}, batch={}, error={}",
                        request.sessionKey(), i + 1, rootMessage(exception));
                batchReplies.add("第 %d 批图片识别失败：%s".formatted(i + 1, rootMessage(exception)));
            }
        }

        if (batchReplies.isEmpty()) {
            return WechatReply.text("图片识别没有返回有效内容，请稍后再试。");
        }
        return WechatReply.text(String.join("\n\n", batchReplies));
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

    private String rootMessage(Throwable exception) {
        Throwable current = exception;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }
}
