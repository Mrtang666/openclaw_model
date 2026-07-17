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
            JsonNode current = root.path("current_condition").get(0);
            if (current == null) return null;

            String temp = current.path("temp_C").asText();
            String feelsLike = current.path("FeelsLikeC").asText();
            String humidity = current.path("humidity").asText();
            String windSpeed = current.path("windspeedKmph").asText();
            String desc = current.path("lang_zh").get(0).path("value").asText();
            String obsTime = current.path("observation_time").asText();

            JsonNode nearest = root.path("nearest_area").get(0);
            String areaName = nearest.path("areaName").get(0).path("value").asText();

            return String.format(
                    "📍 %s 当前天气\n🌡 温度: %s°C (体感 %s°C)\n🌥 状况: %s\n💧 湿度: %s%%\n💨 风速: %s km/h\n🕐 观测时间: %s",
                    areaName, temp, feelsLike, desc, humidity, windSpeed, obsTime);

        } catch (Exception e) {
            log.error("查询天气失败: city={}, error={}", city, e.getMessage());
            return null;
        }
    }
}
