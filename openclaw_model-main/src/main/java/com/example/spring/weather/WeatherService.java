package com.example.spring.weather;

import com.example.spring.weather.WeatherModels.CityLookupResponse;
import com.example.spring.weather.WeatherModels.Daily;
import com.example.spring.weather.WeatherModels.DailyForecastReport;
import com.example.spring.weather.WeatherModels.DailyForecastResponse;
import com.example.spring.weather.WeatherModels.Location;
import com.example.spring.weather.WeatherModels.Now;
import com.example.spring.weather.WeatherModels.WeatherNowResponse;
import com.example.spring.weather.WeatherModels.WeatherReport;
import com.example.spring.weather.WeatherModels.WeatherAnswer;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import java.util.zip.GZIPInputStream;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class WeatherService {
    private static final String SUCCESS_CODE = "200";
    private final WeatherProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    @Autowired
    public WeatherService(WeatherProperties properties) {
        this(properties, new ObjectMapper(), HttpClient.newBuilder()
            .connectTimeout(properties.getConnectTimeout()).build());
    }

    WeatherService(
        WeatherProperties properties,
        ObjectMapper objectMapper,
        HttpClient httpClient) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = httpClient;
    }

    public WeatherReport currentWeather(String region)
        throws IOException, InterruptedException {
        if (!properties.isConfigured()) {
            throw new WeatherException("未配置和风天气 API Key，请设置 WEATHER_API_KEY");
        }
        if (region == null || region.isBlank()) {
            throw new WeatherException("请提供城市、区或县名称，例如：无锡滨湖区天气");
        }

        Location location = resolveLocation(region.trim());
        WeatherNowResponse response = get(
            "/v7/weather/now?location=" + encode(location.id())
                + "&lang=zh&unit=m",
            WeatherNowResponse.class);
        if (response == null || !SUCCESS_CODE.equals(response.code()) || response.now() == null) {
            throw apiError(response == null ? null : response.code());
        }
        return toReport(location, response);
    }

    public WeatherAnswer weather(
        String region,
        LocalDate currentDate,
        LocalDate targetDate,
        String dateExpression,
        boolean currentMoment) throws IOException, InterruptedException {
        if (targetDate.isBefore(currentDate)) {
            throw new WeatherException("暂不支持查询历史天气：" + targetDate);
        }
        long days = java.time.temporal.ChronoUnit.DAYS.between(currentDate, targetDate);
        if (days > 6) {
            throw new WeatherException(
                "当前天气接口只能查询未来 7 天，目标日期为 " + targetDate);
        }
        Location location = resolveLocation(region.trim());
        WeatherReport current = null;
        DailyForecastReport forecast = null;
        if (currentMoment || targetDate.equals(currentDate)) {
            current = currentWeather(location);
        }
        if (!currentMoment || !targetDate.equals(currentDate)) {
            forecast = forecast(location, targetDate);
        }
        return new WeatherAnswer(
            currentDate, targetDate, dateExpression, current, forecast);
    }

    private WeatherReport currentWeather(Location location)
        throws IOException, InterruptedException {
        WeatherNowResponse response = get(
            "/v7/weather/now?location=" + encode(location.id())
                + "&lang=zh&unit=m",
            WeatherNowResponse.class);
        if (response == null || !SUCCESS_CODE.equals(response.code()) || response.now() == null) {
            throw apiError(response == null ? null : response.code());
        }
        return toReport(location, response);
    }

    private DailyForecastReport forecast(Location location, LocalDate targetDate)
        throws IOException, InterruptedException {
        DailyForecastResponse response = get(
            "/v7/weather/7d?location=" + encode(location.id())
                + "&lang=zh&unit=m",
            DailyForecastResponse.class);
        if (response == null || !SUCCESS_CODE.equals(response.code())
            || response.daily() == null) {
            throw apiError(response == null ? null : response.code());
        }
        Daily daily = response.daily().stream()
            .filter(item -> targetDate.toString().equals(item.fxDate()))
            .findFirst()
            .orElseThrow(() -> new WeatherException("天气接口没有返回日期 " + targetDate + " 的预报"));
        try {
            return new DailyForecastReport(
                location,
                targetDate,
                response.updateTime(),
                Double.parseDouble(daily.tempMax()),
                Double.parseDouble(daily.tempMin()),
                daily.textDay(),
                daily.textNight(),
                Integer.parseInt(daily.humidity()),
                daily.windDirDay(),
                daily.windScaleDay(),
                Double.parseDouble(daily.windSpeedDay()),
                parseDouble(daily.precip()),
                daily.uvIndex());
        } catch (NullPointerException | NumberFormatException exception) {
            throw new WeatherException("天气预报数据不完整", exception);
        }
    }

    Location resolveLocation(String query) throws IOException, InterruptedException {
        CityLookupResponse response = get(
            "/geo/v2/city/lookup?location=" + encode(query)
                + "&number=10&lang=zh",
            CityLookupResponse.class);
        if (response == null || !SUCCESS_CODE.equals(response.code())) {
            throw apiError(response == null ? null : response.code());
        }
        if (response.location() == null || response.location().isEmpty()) {
            throw new WeatherException("未找到地区：" + query);
        }

        List<Location> ranked = response.location().stream()
            .sorted(Comparator.comparingInt((Location value) -> score(query, value)).reversed())
            .toList();
        int bestScore = score(query, ranked.get(0));
        List<Location> best = ranked.stream()
            .filter(location -> score(query, location) == bestScore)
            .toList();
        if (best.size() > 1 && isAmbiguousShortQuery(query, best)) {
            String choices = best.stream().limit(5)
                .map(Location::displayName)
                .collect(Collectors.joining("、"));
            throw new WeatherException("地区名称存在重复，请提供省市信息。候选地区：" + choices);
        }
        return ranked.get(0);
    }

    private <T> T get(String pathAndQuery, Class<T> responseType)
        throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl() + pathAndQuery))
            .timeout(properties.getRequestTimeout())
            .header("X-QW-Api-Key", properties.getApiKey().trim())
            .header("Accept", "application/json")
            .GET()
            .build();
        HttpResponse<byte[]> response = httpClient.send(
            request, HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() == 401 || response.statusCode() == 403) {
            throw new WeatherException("和风天气鉴权失败，请检查 WEATHER_API_KEY、API Host 和接口权限");
        }
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new WeatherException("天气服务返回 HTTP " + response.statusCode());
        }
        return objectMapper.readValue(decodeBody(response), responseType);
    }

    private static byte[] decodeBody(HttpResponse<byte[]> response) throws IOException {
        String encoding = response.headers().firstValue("Content-Encoding").orElse("");
        if (!"gzip".equalsIgnoreCase(encoding)) {
            return response.body();
        }
        try (GZIPInputStream input = new GZIPInputStream(
            new ByteArrayInputStream(response.body()))) {
            return input.readAllBytes();
        }
    }

    private WeatherReport toReport(Location location, WeatherNowResponse response) {
        Now now = response.now();
        try {
            return new WeatherReport(
                location,
                response.updateTime(),
                now.text(),
                Double.parseDouble(now.temp()),
                Double.parseDouble(now.feelsLike()),
                Integer.parseInt(now.humidity()),
                now.windDir(),
                now.windScale(),
                Double.parseDouble(now.windSpeed()));
        } catch (NullPointerException | NumberFormatException exception) {
            throw new WeatherException("天气服务返回的数据不完整", exception);
        }
    }

    private static double parseDouble(String value) {
        return value == null || value.isBlank() ? 0 : Double.parseDouble(value);
    }

    private String baseUrl() {
        String host = properties.getApiHost();
        if (host == null || host.isBlank()) {
            throw new WeatherException("WEATHER_API_HOST 不能为空");
        }
        String normalized = host.trim();
        if (!normalized.contains("://")) {
            normalized = "https://" + normalized;
        }
        return normalized.endsWith("/")
            ? normalized.substring(0, normalized.length() - 1)
            : normalized;
    }

    private static int score(String query, Location location) {
        String normalized = query.toLowerCase(Locale.ROOT).replace(" ", "");
        int score = 0;
        if (equalsNormalized(normalized, location.name())) {
            score += 100;
        } else if (contains(normalized, location.name())) {
            score += 50;
        }
        if (contains(normalized, location.adm2())) {
            score += 30;
        }
        if (contains(normalized, location.adm1())) {
            score += 20;
        }
        if (contains(normalized, location.country())) {
            score += 5;
        }
        return score;
    }

    private static boolean isAmbiguousShortQuery(String query, List<Location> candidates) {
        String normalized = query.replace(" ", "");
        return candidates.stream().noneMatch(location ->
            contains(normalized, location.adm1()) || contains(normalized, location.adm2()));
    }

    private static boolean equalsNormalized(String left, String right) {
        return right != null && left.equals(right.toLowerCase(Locale.ROOT).replace(" ", ""));
    }

    private static boolean contains(String text, String value) {
        return value != null && !value.isBlank()
            && text.toLowerCase(Locale.ROOT).contains(value.toLowerCase(Locale.ROOT).replace(" ", ""));
    }

    private static WeatherException apiError(String code) {
        if ("404".equals(code)) {
            return new WeatherException("未找到对应地区或实时天气数据");
        }
        if ("401".equals(code) || "403".equals(code)) {
            return new WeatherException("和风天气鉴权失败，请检查 API Key 和 API Host");
        }
        return new WeatherException("天气服务返回错误" + (code == null ? "" : "，代码：" + code));
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
