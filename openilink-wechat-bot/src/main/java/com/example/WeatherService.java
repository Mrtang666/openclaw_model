package com.example;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.zip.GZIPInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class WeatherService {

    private static final Logger log = LoggerFactory.getLogger(WeatherService.class);

    private final String apiHost;
    private final String apiKey;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public WeatherService() {
        LocalLLMService.Config cfg = LocalLLMService.getConfig();
        this.apiHost = normalizeHost(firstNonBlank(
                System.getenv("QWEATHER_API_HOST"), cfg.getQWeatherApiHost()));
        this.apiKey = firstNonBlank(
                System.getenv("QWEATHER_API_KEY"), cfg.getQWeatherApiKey());
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.objectMapper = new ObjectMapper();
    }

    public String queryWeather(String city) {
        return queryWeather(city, 0);
    }

    public String queryWeather(String city, int dayOffset) {
        if (city == null || city.isBlank()) return null;
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("和风天气 API Key 未配置");
            return null;
        }
        try {
            JsonNode location = lookupLocation(city);
            if (location == null) return null;
            String locationId = location.path("id").asText("");
            String areaName = formatLocationName(city, location);
            if (locationId.isBlank()) return null;

            if (dayOffset <= 0) {
                JsonNode root = getJson("/v7/weather/now?location=" + encode(locationId) + "&lang=zh");
                JsonNode now = root == null ? null : root.path("now");
                if (now == null || now.isMissingNode()) return null;
                return String.format(
                        "📍 %s 当前天气\n"
                                + "🌡 温度: %s°C (体感 %s°C)\n"
                                + "🌥 天气: %s (图标 %s)\n"
                                + "💧 湿度: %s%%\n"
                                + "💨 风向: %s %s°\n"
                                + "💨 风力: %s级，%s km/h\n"
                                + "🌧 降水: %s mm\n"
                                + "📊 气压: %s hPa\n"
                                + "👁 能见度: %s km\n"
                                + "☁ 云量: %s%%\n"
                                + "💦 露点: %s°C\n"
                                + "🕐 观测时间: %s",
                        areaName,
                        now.path("temp").asText("未知"),
                        now.path("feelsLike").asText("未知"),
                        now.path("text").asText("未知"),
                        now.path("icon").asText("未知"),
                        now.path("humidity").asText("未知"),
                        now.path("windDir").asText("未知"),
                        now.path("wind360").asText("未知"),
                        now.path("windScale").asText("未知"),
                        now.path("windSpeed").asText("未知"),
                        now.path("precip").asText("未知"),
                        now.path("pressure").asText("未知"),
                        now.path("vis").asText("未知"),
                        now.path("cloud").asText("未知"),
                        now.path("dew").asText("未知"),
                        now.path("obsTime").asText("未知"));
            }

            JsonNode root = getJson("/v7/weather/7d?location=" + encode(locationId) + "&lang=zh");
            JsonNode daily = root == null ? null : root.path("daily");
            if (daily == null || !daily.isArray() || daily.isEmpty()) return null;
            int index = Math.min(dayOffset, daily.size() - 1);
            JsonNode day = daily.get(index);
            String dayLabel = index == 1 ? "明天" : index == 2 ? "后天" : index == 0 ? "今天" : "第" + index + "天";
            return String.format(
                    "📍 %s %s(%s)天气\n"
                            + "🌡 温度: %s~%s°C\n"
                            + "🌞 白天天气: %s (图标 %s)\n"
                            + "🌙 夜间天气: %s (图标 %s)\n"
                            + "🌅 日出: %s，日落: %s\n"
                            + "🌙 月相: %s\n"
                            + "💧 湿度: %s%%\n"
                            + "🌧 降水: %s mm\n"
                            + "💨 白天风向: %s，风力: %s级，%s km/h\n"
                            + "🌙 夜间风向: %s，风力: %s级，%s km/h\n"
                            + "📊 气压: %s hPa\n"
                            + "👁 能见度: %s km\n"
                            + "☁ 云量: %s%%\n"
                            + "☀ 日照: %s h\n"
                            + "☂ UV指数: %s",
                    areaName,
                    dayLabel,
                    day.path("fxDate").asText("未知"),
                    day.path("tempMin").asText("未知"),
                    day.path("tempMax").asText("未知"),
                    day.path("textDay").asText("未知"),
                    day.path("iconDay").asText("未知"),
                    day.path("textNight").asText("未知"),
                    day.path("iconNight").asText("未知"),
                    day.path("sunrise").asText("未知"),
                    day.path("sunset").asText("未知"),
                    day.path("moonPhase").asText("未知"),
                    day.path("humidity").asText("未知"),
                    day.path("precip").asText("未知"),
                    day.path("windDirDay").asText("未知"),
                    day.path("windScaleDay").asText("未知"),
                    day.path("windSpeedDay").asText("未知"),
                    day.path("windDirNight").asText("未知"),
                    day.path("windScaleNight").asText("未知"),
                    day.path("windSpeedNight").asText("未知"),
                    day.path("pressure").asText("未知"),
                    day.path("vis").asText("未知"),
                    day.path("cloud").asText("未知"),
                    day.path("sunHour").asText("未知"),
                    day.path("uvIndex").asText("未知"));
        } catch (Exception e) {
            log.error("查询和风天气失败: city={}, dayOffset={}, error={}", city, dayOffset, e.getMessage());
            return null;
        }
    }

    private JsonNode lookupLocation(String city) throws Exception {
        String area = extractArea(city);
        String query = area == null ? city : area;
        JsonNode root = getJson("/geo/v2/city/lookup?location=" + encode(query) + "&number=10&lang=zh");
        JsonNode locations = root == null ? null : root.path("location");
        if (locations == null || !locations.isArray() || locations.isEmpty()) {
            log.warn("和风天气未找到地点: city={}", city);
            return null;
        }
        if (area != null) {
            String areaCore = area.replaceAll("(区|县|镇|乡)$", "");
            for (JsonNode candidate : locations) {
                String name = candidate.path("name").asText("");
                if (name.contains(areaCore) || areaCore.contains(name)) return candidate;
            }
        }
        return locations.get(0);
    }

    private String formatLocationName(String requestedCity, JsonNode location) {
        String area = extractArea(requestedCity);
        String adm2 = location.path("adm2").asText("").replaceAll("市$", "");
        String name = location.path("name").asText("");
        if (area != null && !adm2.isBlank()) {
            return adm2 + "市" + area;
        }
        return name.isBlank() ? requestedCity : name;
    }

    private String extractArea(String city) {
        Matcher matcher = Pattern.compile("([\\u4e00-\\u9fa5]{2,6}(?:区|县|镇|乡))$").matcher(city == null ? "" : city);
        return matcher.find() ? matcher.group(1) : null;
    }

    private JsonNode getJson(String path) throws Exception {
        String url = "https://" + apiHost + path + "&key=" + encode(apiKey);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(15))
                .GET()
                .build();
        HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
        String responseBody = decodeResponse(response.body(),
                response.headers().firstValue("Content-Encoding").orElse(""));
        if (response.statusCode() != 200) {
            log.warn("和风天气 API 返回非200: status={}, body={}", response.statusCode(), responseBody);
            return null;
        }
        JsonNode root = objectMapper.readTree(responseBody);
        if (!"200".equals(root.path("code").asText())) {
            log.warn("和风天气 API 错误: code={}, body={}", root.path("code").asText(), responseBody);
            return null;
        }
        return root;
    }

    private String decodeResponse(byte[] bytes, String contentEncoding) throws IOException {
        if (bytes == null || bytes.length == 0) return "";
        boolean gzip = contentEncoding.toLowerCase().contains("gzip")
                || (bytes.length >= 2 && (bytes[0] & 0xff) == 0x1f && (bytes[1] & 0xff) == 0x8b);
        if (!gzip) return new String(bytes, StandardCharsets.UTF_8);

        try (GZIPInputStream gzipInput = new GZIPInputStream(new ByteArrayInputStream(bytes));
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int count;
            while ((count = gzipInput.read(buffer)) != -1) {
                output.write(buffer, 0, count);
            }
            return output.toString(StandardCharsets.UTF_8.name());
        }
    }

    private String encode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    private String normalizeHost(String host) {
        String value = host == null ? "" : host.trim();
        value = value.replaceFirst("^https?://", "");
        return value.replaceAll("/+$", "");
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) return value;
        }
        return "";
    }
}
