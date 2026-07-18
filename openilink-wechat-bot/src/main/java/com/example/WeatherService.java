package com.example;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public class WeatherService {

    private static final Logger log = LoggerFactory.getLogger(WeatherService.class);
    private static final String WTTR_URL = "https://wttr.in/%s?format=j1&lang=zh";

    private static final String[][] DESC_RULES = {
        {"blizzard", "暴风雪"},
        {"ice pellets", "冰雹"},
        {"sleet", "雨夹雪"},
        {"thunder", "雷阵雨"},
        {"drizzle", "毛毛雨"},
        {"snow", "雪"},
        {"rain", "雨"},
        {"haze", "霾"},
        {"fog", "雾"},
        {"mist", "薄雾"},
        {"cloudy", "多云"},
        {"overcast", "阴"},
        {"sunny", "晴"},
        {"clear", "晴"},
    };

    private static String toZh(String desc) {
        if (desc == null || desc.isEmpty()) return desc;
        String lower = desc.toLowerCase().trim();
        for (String[] rule : DESC_RULES) {
            if (lower.contains(rule[0])) return rule[1];
        }
        return desc;
    }
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public WeatherService() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.objectMapper = new ObjectMapper()
                .configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    public String queryWeather(String city) {
        return queryWeather(city, 0);
    }

    public String queryWeather(String city, int dayOffset) {
        try {
            String url = String.format(WTTR_URL, city.trim());
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(15))
                    .GET()
                    .build();
            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());

            if (resp.statusCode() != 200) {
                log.warn("天气API返回非200: status={}", resp.statusCode());
                return null;
            }

            JsonNode root = objectMapper.readTree(resp.body());
            String areaName = root.path("nearest_area").get(0).path("areaName").get(0).path("value").asText();

            if (dayOffset == 0) {
                JsonNode current = root.path("current_condition").get(0);
                if (current == null) return null;

                String temp = current.path("temp_C").asText();
                String feelsLike = current.path("FeelsLikeC").asText();
                String humidity = current.path("humidity").asText();
                String windSpeed = current.path("windspeedKmph").asText();
                String rawDesc = current.path("weatherDesc").get(0).path("value").asText();
                log.debug("当前天气原始描述: [{}]", rawDesc);
                String desc = toZh(rawDesc.trim());
                String obsTime = current.path("observation_time").asText();

                return String.format(
                        "📍 %s 当前天气\n🌡 温度: %s°C (体感 %s°C)\n🌥 状况: %s\n💧 湿度: %s%%\n💨 风速: %s km/h\n🕐 观测时间: %s",
                        areaName, temp, feelsLike, desc, humidity, windSpeed, obsTime);
            } else {
                JsonNode weather = root.path("weather");
                int idx = Math.min(dayOffset, weather.size() - 1);
                JsonNode day = weather.get(idx);
                if (day == null) return null;

                String date = day.path("date").asText();
                String maxTemp = day.path("maxtempC").asText();
                String minTemp = day.path("mintempC").asText();
                JsonNode noon = day.path("hourly").get(4);
                String rawDesc = noon != null ? noon.path("weatherDesc").get(0).path("value").asText() : "";
                log.debug("预报天气原始描述: [{}]", rawDesc);
                String desc = toZh(rawDesc.trim());
                String humidity = noon != null ? noon.path("humidity").asText() : "";
                String windSpeed = noon != null ? noon.path("windspeedKmph").asText() : "";
                String sunHour = day.path("sunHour").asText();
                String uvIndex = day.path("uvIndex").asText();

                String dayLabel = idx == 1 ? "明天" : idx == 2 ? "后天" : "第" + idx + "天";
                if (idx != dayOffset) {
                    dayLabel += "(预报截至当日)";
                }
                return String.format(
                        "📍 %s %s(%s)天气\n🌡 温度: %s~%s°C\n🌥 状况: %s\n💧 湿度: %s%%\n💨 风速: %s km/h\n☀ 日照: %s h\n☂ UV指数: %s",
                        areaName, dayLabel, date, minTemp, maxTemp, desc, humidity, windSpeed, sunHour, uvIndex);
            }
        } catch (Exception e) {
            log.error("查询天气失败: city={}, dayOffset={}, error={}", city, dayOffset, e.getMessage());
            return null;
        }
    }
}
