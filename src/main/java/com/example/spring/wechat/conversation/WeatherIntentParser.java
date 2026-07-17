package com.example.spring.wechat.conversation;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class WeatherIntentParser {

    private static final List<String> WEATHER_KEYWORDS = List.of("天气", "气温", "温度", "下雨", "降雨", "风力", "湿度");
    private static final List<String> TIME_WORDS = List.of("今天", "今日", "现在", "当前", "明天", "后天", "最近", "这几天");
    private static final List<String> PREFIX_WORDS = List.of(
            "帮我",
            "请",
            "麻烦",
            "我想知道",
            "告诉我",
            "查看",
            "看一下",
            "看看",
            "查一下",
            "查询",
            "查查",
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
                .replace("一下", "");

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
