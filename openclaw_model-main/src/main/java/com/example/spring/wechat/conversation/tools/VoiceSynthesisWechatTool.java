package com.example.spring.wechat.conversation.tools;

import com.example.spring.chat.ChatService;
import com.example.spring.wechat.bot.WechatReply;
import com.example.spring.wechat.voice.synthesis.model.VoiceSynthesisSegment;
import com.example.spring.wechat.voice.synthesis.service.VoiceSynthesisService;
import com.example.spring.wechat.voice.style.service.VoicePreferenceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 微信语音合成工具：把最终文本回答转成语音回复片段。
 */
@Component
public class VoiceSynthesisWechatTool implements WechatTool {

    private static final Logger log = LoggerFactory.getLogger(VoiceSynthesisWechatTool.class);

    private final ChatService chatService;
    private final VoiceSynthesisService voiceSynthesisService;
    private final VoicePreferenceService voicePreferenceService;

    @Autowired
    public VoiceSynthesisWechatTool(
            ChatService chatService,
            VoiceSynthesisService voiceSynthesisService,
            ObjectProvider<VoicePreferenceService> voicePreferenceServiceProvider) {
        this(chatService, voiceSynthesisService, voicePreferenceServiceProvider.getIfAvailable());
    }

    public VoiceSynthesisWechatTool(ChatService chatService, VoiceSynthesisService voiceSynthesisService) {
        this(chatService, voiceSynthesisService, (VoicePreferenceService) null);
    }

    public VoiceSynthesisWechatTool(
            ChatService chatService,
            VoiceSynthesisService voiceSynthesisService,
            VoicePreferenceService voicePreferenceService) {
        this.chatService = chatService;
        this.voiceSynthesisService = voiceSynthesisService;
        this.voicePreferenceService = voicePreferenceService;
    }

    @Override
    public String name() {
        return "voice_synthesis";
    }

    @Override
    public String description() {
        return "语音合成工具：当用户明确要求“用语音回复、发语音、语音播报、读给我听”时，把最终回答合成为微信可接收的语音文件或语音气泡";
    }

    @Override
    public List<String> arguments() {
        return List.of("text", "message", "voice", "source", "previous_result", "target_text");
    }

    @Override
    public WechatReply execute(WechatToolRequest request) {
        String finalText = resolveTargetText(request);
        if (finalText.isBlank()) {
            finalText = chatService.reply(buildReplyPrompt(request));
        }
        finalText = finalText == null ? "" : finalText.strip();
        if (finalText.isBlank()) {
            return WechatReply.text("我还没有足够内容可以合成为语音，请再补充一下你想让我说什么。");
        }

        try {
            List<VoiceSynthesisSegment> segments = synthesizeWithUserVoice(request.sessionKey(), finalText);
            List<WechatReply.Part> parts = new ArrayList<>();
            for (VoiceSynthesisSegment segment : segments) {
                parts.add(WechatReply.Part.voice(new WechatReply.Voice(
                        segment.audioBytes(),
                        segment.fileName(),
                        segment.durationMs(),
                        segment.sampleRate(),
                        segment.encodeType(),
                        segment.bitsPerSample(),
                        segment.transcriptText())));
            }
            return WechatReply.ordered(parts);
        } catch (RuntimeException exception) {
            log.warn("微信语音合成工具失败，userId={}, text={}, error={}",
                    request.sessionKey(),
                    preview(finalText),
                    rootMessage(exception));
            return WechatReply.text(finalText + "\n\n语音生成失败：" + friendlyErrorMessage(exception));
        }
    }

    private List<VoiceSynthesisSegment> synthesizeWithUserVoice(String sessionKey, String finalText) {
        if (voicePreferenceService == null) {
            return voiceSynthesisService.synthesizeForWechat(finalText);
        }
        String voice = voicePreferenceService.effectiveVoice(sessionKey);
        return voiceSynthesisService.synthesizeForWechat(finalText, voice);
    }

    private String resolveTargetText(WechatToolRequest request) {
        String previousResult = firstNonBlank(
                request.argument("previous_result"),
                request.argument("previous_tool_result"),
                request.argument("target_text"));
        if (!previousResult.isBlank()) {
            return previousResult;
        }

        String source = request.argument("source");
        if ("previous".equalsIgnoreCase(source)
                || "last_assistant".equalsIgnoreCase(source)
                || "latest_assistant".equalsIgnoreCase(source)) {
            String remembered = extractLatestAssistantText(request.historyText());
            if (!remembered.isBlank()) {
                return remembered;
            }
        }

        String explicit = firstNonBlank(
                request.argument("text"),
                request.argument("reply"));
        if (!explicit.isBlank() && !looksLikeVoiceInstruction(explicit)) {
            return explicit;
        }

        if (wantsToReadPreviousReply(request.userText())) {
            String remembered = extractLatestAssistantText(request.historyText());
            if (!remembered.isBlank()) {
                return remembered;
            }
        }

        return "";
    }

    private boolean looksLikeVoiceInstruction(String value) {
        String text = value == null ? "" : value.strip().toLowerCase(Locale.ROOT);
        if (text.isBlank()) {
            return false;
        }

        boolean mentionsVoice = text.contains("语音")
                || text.contains("朗读")
                || text.contains("读一遍")
                || text.contains("念一遍")
                || text.contains("voice")
                || text.contains("audio")
                || text.contains("read")
                || text.contains("synthesize");
        boolean referencesContext = text.contains("刚才")
                || text.contains("上面")
                || text.contains("上一")
                || text.contains("这些")
                || text.contains("故事")
                || text.contains("生成")
                || text.contains("previous")
                || text.contains("last")
                || text.contains("just now")
                || text.contains("generated");
        return mentionsVoice && referencesContext;
    }

    private boolean wantsToReadPreviousReply(String userText) {
        String text = userText == null ? "" : userText.strip();
        if (text.isBlank()) {
            return false;
        }
        return text.contains("朗读")
                || text.contains("读一遍")
                || text.contains("念一遍")
                || text.contains("再说一遍")
                || text.contains("发给我")
                || text.contains("上一条")
                || text.contains("刚才")
                || text.contains("之前那段");
    }

    private String extractLatestAssistantText(String historyText) {
        if (historyText == null || historyText.isBlank()) {
            return "";
        }

        String[] lines = historyText.split("\\R", -1);
        String latestAssistantBlock = "";
        for (int index = 0; index < lines.length; index++) {
            String line = lines[index] == null ? "" : lines[index];
            String assistantContent = assistantLineContent(line.strip());
            if (assistantContent == null) {
                continue;
            }

            List<String> block = new ArrayList<>();
            block.add(assistantContent);
            for (int next = index + 1; next < lines.length; next++) {
                String nextLine = lines[next] == null ? "" : lines[next];
                if (isConversationRoleLine(nextLine.strip())) {
                    break;
                }
                block.add(nextLine);
            }

            String content = String.join("\n", block).strip();
            if (!content.isBlank() && !isSyntheticReplyMarker(content)) {
                latestAssistantBlock = content;
            }
        }
        return latestAssistantBlock;
    }

    private String assistantLineContent(String line) {
        if (line == null) {
            return null;
        }
        List<String> markers = List.of("助手：", "助手:", "Assistant:", "assistant:");
        for (String marker : markers) {
            if (line.startsWith(marker)) {
                return line.substring(marker.length()).strip();
            }
        }
        return null;
    }

    private boolean isConversationRoleLine(String line) {
        if (line == null) {
            return false;
        }
        return line.startsWith("用户：")
                || line.startsWith("用户:")
                || line.startsWith("助手：")
                || line.startsWith("助手:")
                || line.startsWith("User:")
                || line.startsWith("user:")
                || line.startsWith("Assistant:")
                || line.startsWith("assistant:");
    }

    private boolean isSyntheticReplyMarker(String content) {
        return content.contains("[已发送语音]") || content.contains("[已发送图片]");
    }

    private String buildReplyPrompt(WechatToolRequest request) {
        String message = firstNonBlank(request.argument("message"), request.userText());
        return """
                你是微信聊天助手。用户明确要求你用语音回复。
                请先生成一段适合被语音播报的中文回答，再交给语音合成工具。
                要求：
                1. 直接回答用户，不要说“我将为你生成语音”。
                2. 句子要自然，适合朗读，避免复杂表格和 Markdown。
                3. 如果最近对话里已有前置工具结果，比如天气、图片、计划，请综合这些结果回答。
                最近对话和前置工具结果：
                %s
                用户当前需求：
                %s
                请输出最终要朗读的中文正文：
                """.formatted(request.historyText(), message);
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

    private String friendlyErrorMessage(Throwable exception) {
        String message = rootMessage(exception);
        if (message == null || message.isBlank()) {
            return "请稍后重试";
        }

        String normalized = message.toLowerCase(Locale.ROOT);
        if (normalized.contains("signaturedoesnotmatch")
                || normalized.contains("ossaccesskeyid")
                || normalized.contains("stringtosign")
                || normalized.contains("<error>")
                || normalized.contains("403 forbidden")) {
            return "语音文件下载失败，请稍后重试";
        }
        if (normalized.contains("invalidparameter") || normalized.contains("url error")) {
            return "语音合成接口参数异常，请检查 TTS 配置";
        }
        if (message.length() > 80) {
            return message.substring(0, 80) + "...";
        }
        return message;
    }
}
