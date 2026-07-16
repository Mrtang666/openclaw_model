package com.example.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

public class WeatherService {

    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .proxy(java.net.ProxySelector.getDefault())
            .build();
    private final ObjectMapper mapper = new ObjectMapper();

    @SuppressWarnings("unchecked")
    public void printWeather(String city) {
        try {
            String url = "https://wttr.in/" + city.trim() + "?format=j1";
            HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(30))
                    .header("User-Agent", "curl/8.0")
                    .GET().build();
            HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) {
                throw new RuntimeException("天气API返回状态码: " + resp.statusCode());
            }
            Map<String, Object> data = mapper.readValue(resp.body(), Map.class);
            List<Map<String, Object>> nearest = (List<Map<String, Object>>) data.get("nearest_area");
            String area = "", country = "";
            if (nearest != null && !nearest.isEmpty()) {
                Map<String, Object> areaObj = nearest.get(0);
                area = ((List<Map<String, String>>) areaObj.get("areaName")).get(0).get("value");
                country = ((List<Map<String, String>>) areaObj.get("country")).get(0).get("value");
            }
            List<Map<String, Object>> current = (List<Map<String, Object>>) data.get("current_condition");
            Map<String, Object> cc = current.get(0);
            String temp = cc.get("temp_C") + "°C";
            String feels = cc.get("FeelsLikeC") + "°C";
            String humidity = cc.get("humidity") + "%";
            String desc = ((List<Map<String, String>>) cc.get("weatherDesc")).get(0).get("value");
            String wind = cc.get("windspeedKmph") + " km/h";

            System.out.println("═══════════════════════════════");
            System.out.println("  城市: " + area + ", " + country);
            System.out.println("  天气: " + desc);
            System.out.println("  温度: " + temp + " (体感 " + feels + ")");
            System.out.println("  湿度: " + humidity);
            System.out.println("  风速: " + wind);
            System.out.println("═══════════════════════════════");
        } catch (Exception e) {
            throw new RuntimeException("查询天气失败: " + e.getMessage());
        }
    }
}