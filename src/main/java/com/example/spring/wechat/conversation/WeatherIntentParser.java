package com.example.spring.wechat.conversation;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class WeatherIntentParser {

    private static final List<String> WEATHER_KEYWORDS = List.of(
            "天气",
            "气温",
            "温度",
            "体感",
            "下雨",
            "降雨",
            "下雪",
            "刮风",
            "风力",
            "风速",
            "湿度",
            "空气质量",
            "紫外线",
            "能见度",
            "穿衣",
            "穿什么",
            "怎么穿",
            "带伞",
            "适合出门",
            "适不适合出门",
            "适合穿什么",
            "冷不冷",
            "热不热",
            "会不会下雨",
            "是否下雨",
            "要不要带伞",
            "天气预报");
    private static final List<String> TIME_WORDS = List.of(
            "今天",
            "今日",
            "现在",
            "当前",
            "明天",
            "后天",
            "最近",
            "这几天",
            "这周",
            "本周",
            "今晚",
            "早上",
            "上午",
            "中午",
            "下午",
            "晚上");
    private static final List<String> PREFIX_WORDS = List.of(
            "帮我",
            "帮我看看",
            "帮忙看看",
            "请",
            "麻烦",
            "我想知道",
            "我想了解",
            "我想看看",
            "告诉我",
            "查看",
            "看一下",
            "看看",
            "查一下",
            "查询",
            "查查",
            "帮查",
            "看",
            "查");
    private static final List<String> INVALID_CITY_WORDS = List.of("怎么", "怎么样", "如何", "情况", "真好", "不错", "好吗", "怎样");

    public Optional<String> extractCity(String input) {
        if (input == null || input.isBlank()) {
            return Optional.empty();
        }

        String normalized = normalize(input);
        Optional<String> keyword = WEATHER_KEYWORDS.stream()
                .filter(normalized::contains)
                .findFirst();
        if (keyword.isEmpty()) {
            return Optional.empty();
        }

        int keywordIndex = normalized.indexOf(keyword.get());
        return cityFrom(normalized.substring(0, keywordIndex))
                .or(() -> cityFrom(normalized.substring(keywordIndex + keyword.get().length())));
    }

    private Optional<String> cityFrom(String value) {
        String city = value;
        for (String prefix : PREFIX_WORDS) {
            city = city.replace(prefix, "");
        }
        for (String timeWord : TIME_WORDS) {
            city = city.replace(timeWord, "");
        }
        city = normalize(city)
                .replace("的", "")
                .replace("一下", "")
                .replace("适合", "");

        if (city.isBlank() || city.length() > 20) {
            return Optional.empty();
        }
        boolean invalid = INVALID_CITY_WORDS.stream().anyMatch(city::contains);
        return invalid ? Optional.empty() : Optional.of(city);
    }

    private String normalize(String value) {
        return value.strip()
                .replaceAll("[\\s，。！？、：:,.!?]+", "");
    }
}
