package com.example.spring.wechat.care.service;

import org.springframework.stereotype.Component;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extracts unambiguous daily clock times from a doctor-authored Chinese care plan.
 * Relative phrases such as "晚些时候" are deliberately ignored so they cannot create
 * an unintended reminder; the doctor can correct those items in the review page.
 */
@Component
public class CarePlanTimeParser {

    private static final String PERIOD = "凌晨|早上|早晨|上午|中午|下午|午后|傍晚|晚上|晚间|夜间|睡前";
    private static final Pattern COLON_TIME = Pattern.compile(
            "(?<period>" + PERIOD + ")?\\s*(?<hour>\\d{1,2})\\s*[:：]\\s*(?<minute>\\d{1,2})");
    private static final Pattern POINT_TIME = Pattern.compile(
            "(?<period>" + PERIOD + ")?\\s*(?<hour>\\d{1,2}|零|一|二|三|四|五|六|七|八|九|十|十一|十二|十三|十四|十五|十六|十七|十八|十九|二十|二十一|二十二|二十三|两)\\s*点\\s*(?:(?<half>半)|(?<minute>\\d{1,2})\\s*分?)?");
    private static final Pattern INTERVAL_HOURS = Pattern.compile("每\\s*(\\d{1,2})\\s*(?:个)?\\s*小时");
    private static final Pattern TIME_RANGE = Pattern.compile(
            "(?:\\d{1,2}|[零一二三四五六七八九十两]+)\\s*(?:点|[:：])?\\s*(?:到|至|~|～|—|-)\\s*"
                    + "(?:\\d{1,2}|[零一二三四五六七八九十两]+)");
    private static final Map<String, Integer> CHINESE_HOURS = Map.ofEntries(
            Map.entry("零", 0), Map.entry("一", 1), Map.entry("二", 2), Map.entry("两", 2),
            Map.entry("三", 3), Map.entry("四", 4), Map.entry("五", 5), Map.entry("六", 6),
            Map.entry("七", 7), Map.entry("八", 8), Map.entry("九", 9), Map.entry("十", 10),
            Map.entry("十一", 11), Map.entry("十二", 12), Map.entry("十三", 13), Map.entry("十四", 14),
            Map.entry("十五", 15), Map.entry("十六", 16), Map.entry("十七", 17), Map.entry("十八", 18),
            Map.entry("十九", 19), Map.entry("二十", 20), Map.entry("二十一", 21),
            Map.entry("二十二", 22), Map.entry("二十三", 23));

    public List<LocalTime> extractTimePoints(String source) {
        if (source == null || source.isBlank()) {
            return List.of();
        }
        Map<LocalTime, Boolean> times = new LinkedHashMap<>();
        collect(COLON_TIME.matcher(source), false, times);
        collect(POINT_TIME.matcher(source), true, times);
        return times.keySet().stream().sorted(Comparator.naturalOrder()).toList();
    }

    /**
     * Resolves explicit points first. For an explicit hourly interval, expands a
     * bounded daytime range and caps the result at 20 tasks, matching plan limits.
     */
    public List<LocalTime> resolveDailyTimes(String source, LocalTime fallback) {
        List<LocalTime> explicit = extractTimePoints(source);
        int intervalHours = extractIntervalHours(source);
        if (intervalHours == 0) {
            return explicit.isEmpty() ? List.of(fallback) : explicit;
        }
        boolean rangeDeclared = source != null && TIME_RANGE.matcher(source).find();
        if (!rangeDeclared && !explicit.isEmpty()) {
            return explicit;
        }
        LocalTime start = explicit.isEmpty() ? LocalTime.of(8, 0) : explicit.get(0);
        LocalTime end = explicit.size() >= 2 ? explicit.get(1) : LocalTime.of(20, 0);
        if (!end.isAfter(start)) {
            return explicit.isEmpty() ? List.of(fallback) : explicit;
        }
        List<LocalTime> resolved = new ArrayList<>();
        for (LocalTime current = start; !current.isAfter(end) && resolved.size() < 20;
                current = current.plusHours(intervalHours)) {
            resolved.add(current);
        }
        return List.copyOf(resolved);
    }

    private void collect(Matcher matcher, boolean supportsHalfHour, Map<LocalTime, Boolean> result) {
        while (matcher.find()) {
            Integer rawHour = parseHour(matcher.group("hour"));
            int minute = supportsHalfHour && matcher.group("half") != null
                    ? 30 : parseMinute(matcher.group("minute"));
            if (rawHour == null || minute < 0 || minute > 59) {
                continue;
            }
            Integer hour = normalizeHour(rawHour, matcher.group("period"));
            if (hour != null) {
                result.put(LocalTime.of(hour, minute), Boolean.TRUE);
            }
        }
    }

    private int extractIntervalHours(String source) {
        if (source == null || source.isBlank()) {
            return 0;
        }
        Matcher matcher = INTERVAL_HOURS.matcher(source);
        if (!matcher.find()) {
            return 0;
        }
        try {
            int hours = Integer.parseInt(matcher.group(1));
            return hours >= 1 && hours <= 12 ? hours : 0;
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private Integer parseHour(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String value = text.strip();
        if (CHINESE_HOURS.containsKey(value)) {
            return CHINESE_HOURS.get(value);
        }
        try {
            int hour = Integer.parseInt(value);
            return hour >= 0 && hour <= 23 ? hour : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private int parseMinute(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        try {
            return Integer.parseInt(text.strip());
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }

    private Integer normalizeHour(int hour, String period) {
        String value = period == null ? "" : period.toLowerCase(Locale.ROOT);
        if (value.equals("凌晨")) {
            return hour == 12 ? 0 : hour;
        }
        if (value.equals("中午")) {
            return hour >= 1 && hour <= 10 ? hour + 12 : hour;
        }
        if (value.equals("下午") || value.equals("午后") || value.equals("傍晚")
                || value.equals("晚上") || value.equals("晚间") || value.equals("夜间") || value.equals("睡前")) {
            return hour >= 1 && hour <= 11 ? hour + 12 : hour;
        }
        return hour;
    }
}
