package com.example.wechat.tool.impl;

import com.example.wechat.config.WeatherProperties;
import com.example.wechat.tool.Tool;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

@Component
public class WeatherTool implements Tool {

    private final OkHttpClient httpClient;
    private final WeatherProperties weatherProperties;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // 和风天气官方API地址：天气接口使用 api.qweather.com [citation:6]
    private static final String WEATHER_HOST = "https://api.qweather.com";
    private static final String GEO_HOST = "https://geoapi.qweather.com"; // 备用，用于城市名转经纬度

    public WeatherTool(OkHttpClient httpClient, WeatherProperties weatherProperties) {
        this.httpClient = httpClient;
        this.weatherProperties = weatherProperties;
    }

    // ... (getName, getDescription, getParametersSchema 方法保持不变) ...

    @Override
    public String getName() {
        return "";
    }

    @Override
    public String getDescription() {
        return "";
    }

    @Override
    public Map<String, Object> getParametersSchema() {
        return Map.of();
    }

    @Override
    public String execute(Map<String, Object> params) {
        // 从参数中获取地理位置信息，可以是 "城市名" 或 "经度,纬度"
        String location = (String) params.get("location");
        if (!StringUtils.hasText(location)) {
            return "请提供要查询的城市名或经纬度坐标（例如：116.41,39.92）。";
        }

        try {
            // 步骤1：判断输入是经纬度还是城市名
            // 如果包含逗号，认为是经纬度格式 "经度,纬度"
            // 否则，认为是城市名，需要通过GeoAPI查询经纬度
            String targetLocation = location;
            String cityDisplayName = location; // 用于显示的城市名

            if (!location.contains(",")) {
                // 是城市名，需要先查询经纬度 [citation:6]
                String geoResult = getGeoInfo(location);
                JsonNode geoRoot = objectMapper.readTree(geoResult);
                String code = geoRoot.path("code").asText();
                if (!"200".equals(code) || !geoRoot.path("location").isArray() || geoRoot.path("location").size() == 0) {
                    return "未找到城市: " + location + "，请检查名称是否正确。";
                }
                JsonNode firstLocation = geoRoot.path("location").get(0);
                targetLocation = firstLocation.path("lat").asText() + "," + firstLocation.path("lon").asText();
                cityDisplayName = firstLocation.path("name").asText() +
                        (firstLocation.path("adm1").asText() != null ? ", " + firstLocation.path("adm1").asText() : "");
            }

            // 步骤2：使用经纬度直接查询实时天气 [citation:1]
            // 接口支持 location=经度,纬度 的格式
            String weatherData = getWeatherNow(targetLocation);
            return parseWeatherData(weatherData, cityDisplayName);

        } catch (Exception e) {
            return "查询天气失败: " + e.getMessage();
        }
    }

    /**
     * 通过城市名查询地理位置信息（获取经纬度）
     */
    private String getGeoInfo(String city) throws IOException {
        String url = GEO_HOST + "/v2/city/lookup?location="
                + URLEncoder.encode(city, StandardCharsets.UTF_8)
                + "&key=" + weatherProperties.getApiKey();

        Request request = new Request.Builder().url(url).get().build();
        try (Response response = httpClient.newCall(request).execute()) {
            return response.body().string();
        }
    }

    /**
     * 查询实时天气 - 直接使用经纬度作为location参数 [citation:1]
     */
    private String getWeatherNow(String location) throws IOException {
        String url = WEATHER_HOST + "/v7/weather/now?location=" + location
                + "&key=" + weatherProperties.getApiKey();

        Request request = new Request.Builder().url(url).get().build();
        try (Response response = httpClient.newCall(request).execute()) {
            return response.body().string();
        }
    }

    /**
     * 解析和风天气API返回的JSON数据 [citation:1]
     */
    private String parseWeatherData(String json, String cityDisplayName) throws IOException {
        JsonNode root = objectMapper.readTree(json);
        String code = root.path("code").asText();

        if (!"200".equals(code)) {
            return "获取天气数据失败，错误码: " + code;
        }

        JsonNode now = root.path("now");
        if (now.isMissingNode()) {
            return "未获取到天气数据";
        }

        // 提取所有需要的字段 [citation:1]
        String temp = now.path("temp").asText();
        String text = now.path("text").asText();
        String windDir = now.path("windDir").asText();
        String windSpeed = now.path("windSpeed").asText();
        String humidity = now.path("humidity").asText();
        String feelsLike = now.path("feelsLike").asText();
        String pressure = now.path("pressure").asText();
        String vis = now.path("vis").asText();
        String cloud = now.path("cloud").asText();
        String obsTime = now.path("obsTime").asText();

        // 将 obsTime 从 "2020-06-30T21:40+08:00" 格式转换为更友好的显示
        String displayTime = obsTime;
        if (obsTime != null && obsTime.contains("T")) {
            displayTime = obsTime.replace("T", " ").replace("+08:00", "");
        }

        // 格式化输出
        return String.format(
                "【%s 实时天气】\n" +
                        "🌡️ 温度: %s°C (体感: %s°C)\n" +
                        "☁️ 天气: %s\n" +
                        "💨 风向: %s，风速: %s km/h\n" +
                        "💧 湿度: %s%%\n" +
                        "🌬️ 气压: %s hPa\n" +
                        "👁️ 能见度: %s km\n" +
                        "☁️ 云量: %s%%\n" +
                        "🕐 观测时间: %s",
                cityDisplayName, temp, feelsLike, text, windDir, windSpeed,
                humidity, pressure, vis, cloud, displayTime
        );
    }
}