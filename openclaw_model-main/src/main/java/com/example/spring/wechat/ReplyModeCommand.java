package com.example.spring.wechat;

import java.util.Locale;

public enum ReplyModeCommand {
    ENABLE_VOICE,
    DISABLE_VOICE,
    NONE;

    public static ReplyModeCommand parse(String text) {
        if (text == null || text.isBlank()) {
            return NONE;
        }
        String normalized = text.toLowerCase(Locale.ROOT)
            .replaceAll("[\\s，。！？、,.!?]", "");
        if (containsAny(normalized, "不要关闭语音", "别关闭语音", "不要退出语音")) {
            return NONE;
        }
        if (containsAny(normalized,
            "关闭语音对话", "关闭语音模式", "退出语音对话", "退出语音模式",
            "关闭对话模式", "关掉对话模式", "退出对话模式", "结束对话模式",
            "关掉语音", "语音关掉", "语音模式关掉", "停止语音回复", "取消语音回复", "恢复文字回复",
            "改用文字回复", "使用文字回复", "以后文字回复", "文本回复",
            "不要用语音回复", "不要语音回复", "不用语音回复", "不再语音回复",
            "别用语音回复", "别再发语音", "不要再发语音", "别发语音")) {
            return DISABLE_VOICE;
        }
        if (containsAny(normalized,
            "开启语音对话", "打开语音对话", "进入语音对话", "开启语音模式",
            "打开语音模式", "进入语音模式", "用语音回复", "使用语音回复",
            "以后语音回复", "语音聊天")) {
            return ENABLE_VOICE;
        }
        return NONE;
    }

    private static boolean containsAny(String value, String... candidates) {
        for (String candidate : candidates) {
            if (value.contains(candidate)) {
                return true;
            }
        }
        return false;
    }
}
