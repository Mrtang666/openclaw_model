package com.example.context;

import com.example.intent.BotIntent;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/** Maintains lightweight routing context in addition to the LLM chat history. */
public class ConversationContextService {

    private static final Pattern NON_CONTENT_SYMBOLS = Pattern.compile("[^\\u4e00-\\u9fa5A-Za-z0-9]");
    private static final Pattern FOLLOW_UP_CITY = Pattern.compile(
            "^(?:那|就|那就|换成|改成|换到|改到)?([\\u4e00-\\u9fa5]{2,6}?)(?:市)?(?:呢|吧|呀|啊|怎么样)?$");
    private static final Pattern DATE_WORD = Pattern.compile(
            "大后天|后天|明天|明早|今天|今晚|现在|目前|当前|昨天|前天|星期[一二三四五六日天]|周[一二三四五六日天]|\\d{1,2}[.\\-/]\\d{1,2}");

    private final Map<String, UserContext> contexts = new ConcurrentHashMap<>();

    /** Expands short follow-up messages using the user's last routed intent. */
    public String resolveFollowUp(String userId, String text) {
        if (userId == null || text == null || text.isBlank()) {
            return text;
        }
        UserContext context = contexts.get(userId);
        if (context == null || context.lastIntent != BotIntent.WEATHER
                || context.lastCity == null || context.lastCity.isBlank()) {
            return text;
        }

        // STT may append emoji or other symbols; remove them before matching
        // a short follow-up such as "那南京呢？😔".
        String normalized = NON_CONTENT_SYMBOLS.matcher(text).replaceAll("");
        if (normalized.isBlank()) {
            return text;
        }

        String city = extractFollowUpCity(normalized);
        boolean hasDate = DATE_WORD.matcher(normalized).find();

        if (city != null && !city.isBlank() && !isDateWord(city)) {
            return city + (hasDate ? normalized : dayPhrase(context.lastDayOffset)) + "天气";
        }
        if (hasDate || normalized.matches("^(那)?(呢|天气|气温|温度)$")) {
            return context.lastCity + normalized + (normalized.contains("天气") ? "" : "天气");
        }
        return text;
    }

    public String buildChatInput(String userId, String text) {
        UserContext context = contexts.get(userId);
        if (context == null || context.lastReply == null || context.lastReply.isBlank()) {
            return text;
        }
        return "[最近对话上下文]\n"
                + "上一轮功能: " + context.lastIntent.getDescription() + "\n"
                + "上一轮用户问题: " + context.lastUserText + "\n"
                + "上一轮助手回复: " + context.lastReply + "\n"
                + "请结合以上上下文回答当前问题。\n当前问题: " + text;
    }

    public void rememberWeather(String userId, String userText, String city, int dayOffset, String reply) {
        UserContext context = contextFor(userId);
        context.lastIntent = BotIntent.WEATHER;
        context.lastUserText = userText;
        context.lastCity = city;
        context.lastDayOffset = dayOffset;
        context.lastReply = limit(reply);
    }

    public void remember(String userId, BotIntent intent, String userText, String reply) {
        UserContext context = contextFor(userId);
        context.lastIntent = intent;
        context.lastUserText = userText;
        context.lastReply = limit(reply);
    }

    public void rememberVoiceModeCommand(String userId, String userText, String reply) {
        UserContext context = contextFor(userId);
        context.lastUserText = userText;
        context.lastReply = limit(reply);
    }

    private UserContext contextFor(String userId) {
        return contexts.computeIfAbsent(userId, ignored -> new UserContext());
    }

    private String extractFollowUpCity(String text) {
        java.util.regex.Matcher matcher = FOLLOW_UP_CITY.matcher(text);
        if (!matcher.matches()) {
            return null;
        }
        String city = matcher.group(1);
        return city == null ? null : city.trim();
    }

    private boolean isDateWord(String value) {
        return DATE_WORD.matcher(value).matches();
    }

    private String dayPhrase(int dayOffset) {
        if (dayOffset == 1) return "明天";
        if (dayOffset == 2) return "后天";
        if (dayOffset == 3) return "大后天";
        return "今天";
    }

    private String limit(String value) {
        if (value == null) return "";
        return value.length() <= 1200 ? value : value.substring(0, 1200);
    }

    private static class UserContext {
        private BotIntent lastIntent = BotIntent.CHAT;
        private String lastUserText = "";
        private String lastCity = "";
        private int lastDayOffset;
        private String lastReply = "";
    }
}
