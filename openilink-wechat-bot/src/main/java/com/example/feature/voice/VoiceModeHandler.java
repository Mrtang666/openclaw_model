package com.example.feature.voice;

import com.example.application.ReplyOrchestrator;
import com.example.context.ConversationContextService;
import com.github.wechat.ilink.sdk.ILinkClient;

import java.util.Map;
import java.util.regex.Pattern;

/** Handles per-user voice reply mode commands without coupling routing to TTS transport. */
public class VoiceModeHandler {

    private static final Pattern ENABLE = Pattern.compile(
            "^(开始|开启|打开|进入)\\s*(语音回复模式|语音模式|语音回答模式)$|.*(开始|开启|打开|进入).{0,6}(语音回复模式|语音模式|语音回答模式).*");
    private static final Pattern DISABLE = Pattern.compile(
            "^(关闭|退出|停止|结束)\\s*(语音回复模式|语音模式|语音回答模式)$|.*(关闭|退出|停止|结束).{0,6}(语音回复模式|语音模式|语音回答模式).*");

    private final Map<String, Boolean> modes;
    private final ReplyOrchestrator replies;
    private final ConversationContextService context;

    public VoiceModeHandler(Map<String, Boolean> modes,
                            ReplyOrchestrator replies,
                            ConversationContextService context) {
        this.modes = modes;
        this.replies = replies;
        this.context = context;
    }

    public boolean handle(ILinkClient client, String userId, String text) {
        String normalized = normalize(text);
        if (normalized.isBlank()) return false;

        if (ENABLE.matcher(normalized).matches()) {
            modes.put(userId, true);
            String reply = "好的，已开启语音回复模式。后续我会尽量用语音回复你。";
            replies.sendText(client, userId, reply);
            context.rememberVoiceModeCommand(userId, text, reply);
            return true;
        }
        if (DISABLE.matcher(normalized).matches()) {
            modes.remove(userId);
            String reply = "好的，已关闭语音回复模式。";
            replies.sendText(client, userId, reply);
            context.rememberVoiceModeCommand(userId, text, reply);
            return true;
        }
        return false;
    }

    private String normalize(String text) {
        return text == null ? "" : text.replaceAll("[\\s，。！？、,.?；;：:]+", "");
    }
}
