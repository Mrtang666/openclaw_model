package com.example.guidance;

import com.example.intent.BotIntent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Collects missing task details before a weather query or poster generation. */
public class GuidedConversationService {

    private static final Pattern NON_TEXT = Pattern.compile("[^\\u4e00-\\u9fa5A-Za-z0-9：:，,。.!！?？\\s]");
    private static final Pattern AREA = Pattern.compile("([\\u4e00-\\u9fa5]{2,6}(?:区|县|镇|乡))");
    private static final Pattern THEME = Pattern.compile("(?:主题|内容)(?:是|为|：|:)?(.+?)(?=(?:风格|用途|用来|用于)|$)");
    private static final Pattern STYLE = Pattern.compile("风格(?:是|为|：|:)?(.+?)(?=(?:用途|用来|用于)|$)");
    private static final Pattern PURPOSE = Pattern.compile("(?:用途|用来|用于)(?:是|为|：|:)?(.+)$");
    private static final Pattern ABOUT = Pattern.compile("关于(.+?)(?:的)?海报");
    private static final Pattern ASPECT_RATIO = Pattern.compile("(?<!\\d)(\\d{1,2})\\s*[:：]\\s*(\\d{1,2})(?!\\d)");
    private static final Pattern ROUTE_DESTINATION = Pattern.compile(
            "([\\u4e00-\\u9fa5]{2,6})(?=(?:[一二三四五六七\\d]+)(?:日游|天))");
    private static final Pattern ROUTE_DESTINATION_WITH_SUBJECT = Pattern.compile(
            "([\\u4e00-\\u9fa5]{2,6})(?=(?:旅游|旅行|路线|行程|攻略|规划))");
    private static final Pattern ROUTE_DURATION = Pattern.compile("([一二三四五六七\\d]+)(?:日游|天)");

    private final Map<String, PendingTask> pendingTasks = new ConcurrentHashMap<>();

    public Result acceptPending(String userId, String text) {
        PendingTask task = pendingTasks.get(userId);
        if (task == null) {
            return Result.notHandled();
        }

        if (task.type == TaskType.WEATHER_AREA) {
            String normalized = normalize(text);
            if (normalized.contains("全市") || normalized.contains("整个南京") || normalized.contains("整个城市")) {
                pendingTasks.remove(userId);
                return Result.weatherReady(task.city, task.dayOffset);
            }

            String area = extractArea(normalized, task.city);
            if (area != null) {
                pendingTasks.remove(userId);
                return Result.weatherReady(task.city + area, task.dayOffset);
            }
            return Result.ask("为了查询更准确的天气，请告诉我具体区县，例如鼓楼区、玄武区；如果查询全市，请回复“查全市”。");
        }

        if (task.type == TaskType.ROUTE_INPUT) {
            applyRouteFields(task, text);
            if (task.destination == null || task.destination.isBlank()) {
                return Result.ask("请告诉我旅游目的地，例如南京、杭州或无锡。");
            }
            if (task.durationDays <= 0) {
                return Result.ask("计划安排几天？例如一日游、三日游或五天四晚。");
            }
            pendingTasks.remove(userId);
            return Result.routeReady(buildRoutePrompt(task));
        }

        if (task.type == TaskType.IMAGE_INPUT) {
            String prompt = text == null ? "" : text.trim();
            if (prompt.isBlank()) {
                return Result.ask("请告诉我想生成的具体内容，例如主体、风格或使用场景。");
            }
            pendingTasks.remove(userId);
            return Result.imageReady(prompt);
        }

        if (task.type == TaskType.POSTER_INPUT) {
            applyPosterFields(task, text, true);
            String missing = missingPosterFields(task);
            if (!missing.isBlank()) {
                return Result.ask("为了生成合适的海报，还需要补充：" + missing + "。");
            }
            pendingTasks.remove(userId);
            return Result.posterReady(buildPosterPrompt(task));
        }

        if (task.type == TaskType.POSTER_REVIEW) {
            String normalized = normalize(text);
            if (isCloseMessage(normalized)) {
                pendingTasks.remove(userId);
                return Result.satisfied("好的，海报已确认。如果之后还需要调整，直接告诉我修改内容即可。");
            }
            if (!isRevisionMessage(normalized)) {
                pendingTasks.remove(userId);
                return Result.notHandled();
            }
            String revisionPrompt = task.prompt + "\n请根据用户的修改意见重新生成：" + text.trim();
            pendingTasks.remove(userId);
            return Result.posterReady(revisionPrompt);
        }

        return Result.notHandled();
    }

    public boolean hasPending(String userId) {
        return userId != null && pendingTasks.containsKey(userId);
    }

    /** Determines whether the new message belongs to the pending task. */
    public boolean shouldContinuePending(String userId, BotIntent intent, String text) {
        PendingTask task = pendingTasks.get(userId);
        if (task == null) return false;

        if (task.type == TaskType.WEATHER_AREA) {
            return intent == BotIntent.WEATHER || intent == BotIntent.CHAT;
        }
        if (task.type == TaskType.ROUTE_INPUT) {
            return intent == BotIntent.ROUTE_MAP || intent == BotIntent.CHAT;
        }
        if (task.type == TaskType.IMAGE_INPUT || task.type == TaskType.POSTER_INPUT) {
            return intent == BotIntent.IMAGE_GENERATION || intent == BotIntent.CHAT;
        }
        if (task.type == TaskType.POSTER_REVIEW) {
            String normalized = normalize(text);
            return isCloseMessage(normalized) || isRevisionMessage(normalized);
        }
        return false;
    }

    public void cancelPending(String userId) {
        if (userId != null) pendingTasks.remove(userId);
    }

    public Result startWeather(String userId, String city, int dayOffset) {
        if (city == null || city.isBlank()) {
            return Result.ask("请告诉我需要查询的城市名称。");
        }
        if (hasArea(city)) {
            return Result.weatherReady(city, dayOffset);
        }
        PendingTask task = new PendingTask(TaskType.WEATHER_AREA);
        task.city = stripCitySuffix(city);
        task.dayOffset = dayOffset;
        pendingTasks.put(userId, task);
        return Result.ask(task.city + "可以细化到区县查询。请告诉我具体是哪个区，例如鼓楼区、玄武区；如果查全市，请回复“查全市”。");
    }

    public Result startPoster(String userId, String initialText) {
        PendingTask task = new PendingTask(TaskType.POSTER_INPUT);
        applyPosterFields(task, initialText, false);
        String missing = missingPosterFields(task);
        if (!missing.isBlank()) {
            pendingTasks.put(userId, task);
            return Result.ask("为了生成合适的海报，请补充：" + missing + "。例如可以告诉我主题、风格和用途。");
        }
        return Result.posterReady(buildPosterPrompt(task));
    }

    public Result startRouteMap(String userId, String initialText) {
        PendingTask task = new PendingTask(TaskType.ROUTE_INPUT);
        applyRouteFields(task, initialText);
        if (task.destination == null || task.destination.isBlank()) {
            pendingTasks.put(userId, task);
            return Result.ask("请告诉我旅游目的地，例如南京、杭州或无锡。");
        }
        if (task.durationDays <= 0) {
            pendingTasks.put(userId, task);
            return Result.ask(task.destination + "准备安排几天？例如一日游、三日游或五天四晚。");
        }
        return Result.routeReady(buildRoutePrompt(task));
    }

    public Result startImage(String userId, String prompt) {
        String value = prompt == null ? "" : prompt.trim();
        if (value.isBlank() || value.matches("图片|图像|一张图片|一张图|一个图片")) {
            pendingTasks.put(userId, new PendingTask(TaskType.IMAGE_INPUT));
            return Result.ask("你想生成什么内容？请告诉我主体，最好再补充风格或使用场景。");
        }
        return Result.imageReady(value);
    }

    public void markPosterGenerated(String userId, String prompt) {
        PendingTask task = new PendingTask(TaskType.POSTER_REVIEW);
        task.prompt = prompt;
        pendingTasks.put(userId, task);
    }

    private void applyPosterFields(PendingTask task, String text, boolean allowUnlabeled) {
        String value = text == null ? "" : text.trim();
        Matcher ratioMatcher = ASPECT_RATIO.matcher(value);
        if (ratioMatcher.find()) {
            task.aspectRatio = ratioMatcher.group(1) + ":" + ratioMatcher.group(2);
        }
        Matcher themeMatcher = THEME.matcher(value);
        if (themeMatcher.find()) task.theme = cleanField(themeMatcher.group(1));
        Matcher aboutMatcher = ABOUT.matcher(value);
        if (task.theme == null && aboutMatcher.find()) task.theme = cleanField(aboutMatcher.group(1));

        Matcher styleMatcher = STYLE.matcher(value);
        if (styleMatcher.find()) task.style = cleanField(styleMatcher.group(1));
        Matcher purposeMatcher = PURPOSE.matcher(value);
        if (purposeMatcher.find()) task.purpose = cleanField(purposeMatcher.group(1));

        if (allowUnlabeled && !value.isBlank()
                && !value.contains("主题") && !value.contains("内容")
                && !value.contains("风格") && !value.contains("用途")
                && !value.contains("用来") && !value.contains("用于")) {
            if (task.theme == null) task.theme = cleanField(value);
            else if (task.style == null) task.style = cleanField(value);
            else if (task.purpose == null) task.purpose = cleanField(value);
        }
    }

    private String missingPosterFields(PendingTask task) {
        List<String> missing = new ArrayList<>();
        if (isBlank(task.theme)) missing.add("海报主题");
        if (isBlank(task.style)) missing.add("视觉风格");
        if (isBlank(task.purpose)) missing.add("使用场景或用途");
        return String.join("、", missing);
    }

    private String buildPosterPrompt(PendingTask task) {
        String ratio = task.aspectRatio == null ? "" : "；画面比例：" + task.aspectRatio;
        return "请生成一张中文海报。主题：" + task.theme
                + "；视觉风格：" + task.style
                + "；使用场景和用途：" + task.purpose
                + ratio + "。画面要有清晰的视觉层级，文字准确可读，构图适合海报使用。";
    }

    private void applyRouteFields(PendingTask task, String text) {
        String value = normalize(text).replaceFirst(
                "^(?:请帮我制定|请帮我规划|帮我制定|帮我规划|帮我安排|请帮我|帮我|请|麻烦|给我|制定|规划|安排)+", "");
        Matcher destinationMatcher = ROUTE_DESTINATION.matcher(value);
        if (destinationMatcher.find()) {
            task.destination = destinationMatcher.group(1);
        }
        if (task.destination == null || task.destination.isBlank()) {
            Matcher subjectMatcher = ROUTE_DESTINATION_WITH_SUBJECT.matcher(value);
            if (subjectMatcher.find()) {
                task.destination = subjectMatcher.group(1);
            }
        }
        Matcher durationMatcher = ROUTE_DURATION.matcher(value);
        if (durationMatcher.find()) {
            task.durationDays = parseChineseNumber(durationMatcher.group(1));
        }
        if ((task.destination == null || task.destination.isBlank())
                && task.durationDays <= 0
                && value.matches("[\\u4e00-\\u9fa5]{2,6}")
                && !value.matches(".*(日游|天)$")) {
            task.destination = value;
        }
    }

    private String buildRoutePrompt(PendingTask task) {
        return "请为" + task.destination + "制定" + task.durationDays
                + "日旅游规划，按天安排景点、景点之间的距离、路线和出行方式，并生成路线图。";
    }

    private int parseChineseNumber(String value) {
        if (value == null || value.isBlank()) return 0;
        if (value.matches("\\d+")) return Integer.parseInt(value);
        switch (value) {
            case "一": return 1;
            case "二": return 2;
            case "三": return 3;
            case "四": return 4;
            case "五": return 5;
            case "六": return 6;
            case "七": return 7;
            default: return 0;
        }
    }

    private String extractArea(String text, String city) {
        Matcher matcher = AREA.matcher(text);
        String result = null;
        while (matcher.find()) result = matcher.group(1);
        if (result == null) return null;
        if (city != null && result.startsWith(city)) result = result.substring(city.length());
        return result.isBlank() ? null : result;
    }

    private boolean hasArea(String city) {
        return city.matches(".*(区|县|镇|乡)$");
    }

    private String stripCitySuffix(String city) {
        return city.replaceAll("市$", "");
    }

    private String normalize(String text) {
        return NON_TEXT.matcher(text == null ? "" : text).replaceAll("").replaceAll("\\s+", "");
    }

    private String cleanField(String value) {
        if (value == null) return null;
        String cleaned = value.replaceAll("^[：:，,。.!！?？\\s]+|[：:，,。.!！?？\\s]+$", "").trim();
        return cleaned.isBlank() ? null : cleaned;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private boolean isCloseMessage(String text) {
        return text.matches(".*(满意|可以|很好|不错|不用改|就这样|没有了|不用了|不需要|谢谢|感谢|先这样|结束).*" );
    }

    private boolean isRevisionMessage(String text) {
        return text.matches(".*(修改|改成|换成|调整|不满意|重新|再生成|颜色|字体|比例|布局|内容|文字|风格).*" );
    }

    private enum TaskType { WEATHER_AREA, ROUTE_INPUT, IMAGE_INPUT, POSTER_INPUT, POSTER_REVIEW }

    private static class PendingTask {
        private final TaskType type;
        private String city;
        private int dayOffset;
        private String theme;
        private String style;
        private String purpose;
        private String prompt;
        private String aspectRatio;
        private String destination;
        private int durationDays;

        private PendingTask(TaskType type) {
            this.type = type;
        }
    }

    public enum Action { NONE, ASK, WEATHER_READY, ROUTE_READY, IMAGE_READY, POSTER_READY, SATISFIED }

    public static class Result {
        private final Action action;
        private final String message;
        private final String city;
        private final int dayOffset;
        private final String prompt;

        private Result(Action action, String message, String city, int dayOffset, String prompt) {
            this.action = action;
            this.message = message;
            this.city = city;
            this.dayOffset = dayOffset;
            this.prompt = prompt;
        }

        public static Result notHandled() { return new Result(Action.NONE, "", null, 0, null); }
        public static Result ask(String message) { return new Result(Action.ASK, message, null, 0, null); }
        public static Result weatherReady(String city, int dayOffset) { return new Result(Action.WEATHER_READY, "", city, dayOffset, null); }
        public static Result routeReady(String prompt) { return new Result(Action.ROUTE_READY, "", null, 0, prompt); }
        public static Result imageReady(String prompt) { return new Result(Action.IMAGE_READY, "", null, 0, prompt); }
        public static Result posterReady(String prompt) { return new Result(Action.POSTER_READY, "", null, 0, prompt); }
        public static Result satisfied(String message) { return new Result(Action.SATISFIED, message, null, 0, null); }

        public Action getAction() { return action; }
        public String getMessage() { return message; }
        public String getCity() { return city; }
        public int getDayOffset() { return dayOffset; }
        public String getPrompt() { return prompt; }
    }
}
