package com.example.spring.speech;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ReadAloudIntentParser {
    private static final Pattern ACTION = Pattern.compile(
        "(朗读|念一下|念一遍|读出来|读给我听|用语音读|语音朗读|帮我读|播报)");
    private static final Pattern NEGATIVE = Pattern.compile(
        "(不要|不用|别|无需).{0,5}(朗读|念|读出来|播报)");
    private static final Pattern INFORMATIONAL_QUESTION = Pattern.compile(
        "(什么是|是什么意思|如何|怎么|介绍|解释).{0,8}(朗读|播报)"
            + "|(朗读|播报).{0,5}(是什么|什么意思|怎么用|功能)");
    private static final Pattern HISTORY_REFERENCE = Pattern.compile(
        "(上面|上述|刚才|上一条|前面|之前).{0,8}(内容|文字|回复|笑话|答案|消息)?");

    private ReadAloudIntentParser() {
    }

    public static boolean isReadAloudIntent(String text) {
        if (text == null || text.isBlank() || NEGATIVE.matcher(text).find()
            || INFORMATIONAL_QUESTION.matcher(text).find()) {
            return false;
        }
        return ACTION.matcher(text).find();
    }

    public static String extractInlineText(String text) {
        if (!isReadAloudIntent(text) || HISTORY_REFERENCE.matcher(text).find()) {
            return "";
        }
        Matcher action = ACTION.matcher(text);
        if (!action.find()) {
            return "";
        }
        String remainder = text.substring(action.end()).trim();
        remainder = remainder.replaceFirst(
            "^(一下|一遍)?(下面|以下|下列|这段|这句话|这篇|这条)?的?"
                + "(内容|文字|文本|话)?[:：]?",
            "").trim();
        remainder = remainder.replaceFirst("^[:：\\s]+", "").trim();
        return isOnlyReference(remainder) ? "" : remainder;
    }

    private static boolean isOnlyReference(String value) {
        return value.isBlank()
            || value.matches("(上面|上述|刚才|上一条|前面|之前|这段|这句话|内容|文字).{0,4}");
    }
}
