package com.example.feature.weather;

import com.example.WeatherService;
import com.example.context.ConversationContextService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.time.DayOfWeek;
import java.time.temporal.ChronoUnit;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Owns weather intent parsing, QWeather execution, and weather context recording. */
public class WeatherHandler {

    private static final Logger log = LoggerFactory.getLogger(WeatherHandler.class);
    private static final Pattern WEATHER_KEYWORD = Pattern.compile(
            "天气|气温|温度|下雨|下雪|台风|刮风|晴天|阴天|雨天|雪天|有雨|有雪|风力|风速|大雾|霾|沙尘");
    private static final Pattern CITY_CANDIDATE = Pattern.compile(
            "([\\u4e00-\\u9fa5]{2,6}(?:市|区|县|镇|乡)?)");
    private static final Pattern WEATHER_DATE_WORDS = Pattern.compile(
            "大后天|后天|明天|明早|今天|今晚|现在|目前|当前|昨天|前天|\\d{1,2}[.\\-/]\\d{1,2}|\\d{1,2}月\\d{1,2}日");
    private static final Pattern WEATHER_FILLER_WORDS = Pattern.compile(
            "请问|先帮我|先给我|先查|先看|先|帮我|给我|帮忙|查一下|查下|查询一下|查询|查|看一下|看下|看看|看|告诉我|告诉|问一下|可以|能不能|能|麻烦|我想知道|想知道|一下|的|吗|呢|呀|啊|吧|哈|呗|么|怎么样|如何");
    private static final Set<String> SKIP_WORDS = Set.of(
            "大后天", "今天", "明天", "昨天", "后天", "前天", "早上", "上午", "中午", "下午", "晚上",
            "今晚", "明早", "今早", "昨晚", "现在", "目前", "当前", "这里", "那里", "这边",
            "那边", "什么", "怎么", "如何", "为啥", "为什么", "请问", "一下", "告诉");

    private final WeatherService weatherService;
    private final ConversationContextService context;

    public WeatherHandler(WeatherService weatherService, ConversationContextService context) {
        this.weatherService = weatherService;
        this.context = context;
    }

    public boolean isWeatherRequest(String text) {
        return text != null && WEATHER_KEYWORD.matcher(text).find();
    }

    public Query parse(String text) {
        String city = extractCity(text);
        int dayOffset = detectDayOffset(text);
        int parsed = parseDateOffset(text);
        if (parsed >= 0) dayOffset = parsed;
        return new Query(city, dayOffset);
    }

    public String queryAndRemember(String userId, String userText, String city, int dayOffset) {
        String weather = weatherService.queryWeather(city, dayOffset);
        if (weather == null) {
            weather = "暂时查不到 " + city + " 的天气信息，请检查城市名或区域名称是否正确。";
        }
        context.rememberWeather(userId, userText, city, dayOffset, weather);
        return weather;
    }

    public String tryQuery(String text) {
        if (!isWeatherRequest(text)) return null;
        Query query = parse(text);
        if (query.city == null || query.city.isBlank()) {
            return "没有识别到明确的城市，请在天气问题中写出城市名称。";
        }
        log.info("检测到天气查询: text={}, city={}, dayOffset={}", text, query.city, query.dayOffset);
        String weather = weatherService.queryWeather(query.city, query.dayOffset);
        return weather == null
                ? "暂时查不到 " + query.city + " 的天气信息，请检查城市名或区域名称是否正确。"
                : weather;
    }

    public static class Query {
        private final String city;
        private final int dayOffset;

        public Query(String city, int dayOffset) {
            this.city = city;
            this.dayOffset = dayOffset;
        }

        public String getCity() { return city; }
        public int getDayOffset() { return dayOffset; }
    }

    private String extractCity(String text) {
        if (text == null || text.isBlank()) return null;
        String cleaned = text.replaceAll("[\\s，。！？、,.?；;：:]+", "");
        cleaned = WEATHER_DATE_WORDS.matcher(cleaned).replaceAll("");
        cleaned = WEATHER_FILLER_WORDS.matcher(cleaned).replaceAll("");
        cleaned = WEATHER_KEYWORD.matcher(cleaned).replaceAll("");
        String[] stripWords = SKIP_WORDS.stream()
                .sorted((a, b) -> Integer.compare(b.length(), a.length()))
                .toArray(String[]::new);
        for (String word : stripWords) cleaned = cleaned.replace(word, "");
        cleaned = cleaned.replaceAll("^(我目前在|我在|我去|我到)+", "");
        cleaned = cleaned.replaceAll("^(这边|那边|这里|那里)+", "");
        cleaned = cleaned.replaceAll("(这边|那边|这里|那里|附近|周边)$", "");
        if (cleaned.isBlank()) return null;

        Matcher matcher = CITY_CANDIDATE.matcher(cleaned);
        String city = null;
        while (matcher.find()) {
            String candidate = WEATHER_DATE_WORDS.matcher(matcher.group(1)).replaceAll("");
            candidate = WEATHER_FILLER_WORDS.matcher(candidate).replaceAll("");
            candidate = WEATHER_KEYWORD.matcher(candidate).replaceAll("");
            if (!candidate.isBlank() && !SKIP_WORDS.contains(candidate)) city = candidate;
        }
        return city;
    }

    private int detectDayOffset(String text) {
        if (text == null) return 0;
        if (text.contains("大后天")) return 3;
        if (text.contains("后天")) return 2;
        if (text.contains("明天") || text.contains("明早")) return 1;
        if (text.contains("今天") || text.contains("今晚") || text.contains("现在")) return 0;

        String[] dayNames = {"", "一", "二", "三", "四", "五", "六", "日", "天"};
        DayOfWeek today = LocalDate.now().getDayOfWeek();
        for (int i = 1; i <= 7; i++) {
            if (text.contains("星期" + dayNames[i]) || text.contains("周" + dayNames[i])) {
                int offset = i - today.getValue();
                return offset < 0 ? offset + 7 : offset;
            }
        }
        return 0;
    }

    private int parseDateOffset(String text) {
        Matcher matcher = Pattern.compile("(\\d{1,2})[.\\-/](\\d{1,2})(?:[^\\d]|$)").matcher(text == null ? "" : text);
        if (matcher.find()) {
            int offset = dateOffset(Integer.parseInt(matcher.group(1)), Integer.parseInt(matcher.group(2)));
            if (offset >= 0) return offset;
        }
        matcher = Pattern.compile("(\\d{1,2})月(\\d{1,2})日").matcher(text == null ? "" : text);
        if (matcher.find()) return dateOffset(Integer.parseInt(matcher.group(1)), Integer.parseInt(matcher.group(2)));
        return -1;
    }

    private int dateOffset(int month, int day) {
        if (month < 1 || month > 12 || day < 1 || day > 31) return -1;
        try {
            LocalDate target = LocalDate.of(LocalDate.now().getYear(), month, day);
            long diff = ChronoUnit.DAYS.between(LocalDate.now(), target);
            return diff >= 0 && diff <= 14 ? (int) diff : -1;
        } catch (RuntimeException e) {
            return -1;
        }
    }
}
