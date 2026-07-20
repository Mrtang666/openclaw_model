package com.example.spring.wechat;

import java.util.Locale;
import java.util.Set;

public final class PendingReplyIntentParser {
    private static final Set<String> AFFIRMATIVE = Set.of(
        "需要", "要", "回复", "继续", "继续回复", "可以", "好的", "好", "是", "确认", "请回复");
    private static final Set<String> NEGATIVE = Set.of(
        "不需要", "不要", "不用", "算了", "忽略", "跳过", "不回复", "否", "取消");

    private PendingReplyIntentParser() {
    }

    public static RecoveryIntent parse(String text) {
        if (text == null || text.isBlank()) {
            return RecoveryIntent.UNCLEAR;
        }
        String normalized = text.trim()
            .replaceAll("[，。！？,.!?\\s]", "")
            .replaceAll("[了吧啦呀啊呢]+$", "")
            .toLowerCase(Locale.ROOT);
        if (NEGATIVE.contains(normalized)) {
            return RecoveryIntent.DECLINE;
        }
        if (AFFIRMATIVE.contains(normalized)) {
            return RecoveryIntent.AFFIRM;
        }
        return RecoveryIntent.NEW_REQUEST;
    }

    public enum RecoveryIntent {
        AFFIRM,
        DECLINE,
        NEW_REQUEST,
        UNCLEAR
    }
}
