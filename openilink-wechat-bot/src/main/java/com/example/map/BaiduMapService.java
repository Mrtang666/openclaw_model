package com.example.map;

import com.example.LocalLLMService;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;

public class BaiduMapService {

    private static final Logger log = LoggerFactory.getLogger(BaiduMapService.class);

    private final String ak;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public BaiduMapService() {
        this.ak = envOrDefault("BAIDU_MAP_AK", LocalLLMService.getConfig().getBaiduMapAk());
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.objectMapper = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    public boolean isEnabled() {
        return ak != null && !ak.isBlank();
    }

    public Optional<RouteLeg> queryBestLeg(String city, String from, String to) {
        if (!isEnabled() || isBlank(city) || isBlank(from) || isBlank(to)) {
            return Optional.empty();
        }
        try {
            Optional<Place> origin = searchPlace(city, from);
            Optional<Place> destination = searchPlace(city, to);
            if (origin.isEmpty() || destination.isEmpty()) {
                log.info("百度地图地点解析不足: city={}, from={}, to={}, origin={}, destination={}",
                        city, from, to, origin.isPresent(), destination.isPresent());
                return Optional.empty();
            }

            Optional<RouteLeg> walking = queryDirection("walking", origin.get(), destination.get());
            if (walking.isPresent() && walking.get().distanceMeters <= 1600) {
                return walking;
            }

            Optional<RouteLeg> driving = queryDirection("driving", origin.get(), destination.get());
            if (driving.isPresent()) {
                return driving;
            }
            return walking;
        } catch (Exception e) {
            log.warn("百度地图路线查询失败: {} -> {}, error={}", from, to, e.getMessage());
            return Optional.empty();
        }
    }

    private Optional<Place> searchPlace(String city, String keyword) throws Exception {
        String url = "https://api.map.baidu.com/place/v2/search"
                + "?query=" + encode(keyword)
                + "&region=" + encode(city)
                + "&city_limit=true"
                + "&output=json"
                + "&ak=" + encode(ak);
        JsonNode root = getJson(url);
        if (root.path("status").asInt(-1) != 0) {
            log.warn("百度地点检索失败: keyword={}, status={}, message={}",
                    keyword, root.path("status").asText(), root.path("message").asText());
            return Optional.empty();
        }
        JsonNode results = root.path("results");
        if (!results.isArray() || results.isEmpty()) {
            return Optional.empty();
        }
        JsonNode first = results.get(0);
        JsonNode location = first.path("location");
        double lat = location.path("lat").asDouble(Double.NaN);
        double lng = location.path("lng").asDouble(Double.NaN);
        if (Double.isNaN(lat) || Double.isNaN(lng)) {
            return Optional.empty();
        }
        return Optional.of(new Place(first.path("name").asText(keyword), lat, lng));
    }

    private Optional<RouteLeg> queryDirection(String mode, Place origin, Place destination) throws Exception {
        String url = "https://api.map.baidu.com/directionlite/v1/" + mode
                + "?origin=" + origin.lat + "," + origin.lng
                + "&destination=" + destination.lat + "," + destination.lng
                + "&ak=" + encode(ak);
        JsonNode root = getJson(url);
        if (root.path("status").asInt(-1) != 0) {
            log.warn("百度路线规划失败: mode={}, status={}, message={}",
                    mode, root.path("status").asText(), root.path("message").asText());
            return Optional.empty();
        }

        JsonNode routes = root.path("result").path("routes");
        JsonNode route = null;
        if (routes.isArray() && !routes.isEmpty()) {
            route = routes.get(0);
        } else if (routes.isObject()) {
            route = routes;
        }
        if (route == null || route.isMissingNode()) {
            return Optional.empty();
        }

        int distance = route.path("distance").asInt(-1);
        int duration = route.path("duration").asInt(-1);
        if (distance < 0 || duration < 0) {
            return Optional.empty();
        }

        String modeName = "walking".equals(mode) ? "步行" : "打车/驾车";
        return Optional.of(new RouteLeg(modeName, distance, duration));
    }

    private JsonNode getJson(String url) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(15))
                .GET()
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() != 200) {
            throw new IllegalStateException("HTTP " + response.statusCode());
        }
        return objectMapper.readTree(response.body());
    }

    private String encode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String envOrDefault(String key, String defaultValue) {
        String value = System.getenv(key);
        return value == null || value.isBlank() ? defaultValue : value;
    }

    private static class Place {
        private final String name;
        private final double lat;
        private final double lng;

        private Place(String name, double lat, double lng) {
            this.name = name;
            this.lat = lat;
            this.lng = lng;
        }
    }

    public static class RouteLeg {
        private final String mode;
        private final int distanceMeters;
        private final int durationSeconds;

        public RouteLeg(String mode, int distanceMeters, int durationSeconds) {
            this.mode = mode;
            this.distanceMeters = distanceMeters;
            this.durationSeconds = durationSeconds;
        }

        public String getMode() {
            return mode;
        }

        public int getDistanceMeters() {
            return distanceMeters;
        }

        public int getDurationSeconds() {
            return durationSeconds;
        }

        public String distanceText() {
            if (distanceMeters >= 1000) {
                return String.format("约 %.1f km", distanceMeters / 1000.0);
            }
            return "约 " + distanceMeters + " m";
        }

        public String durationText() {
            int minutes = Math.max(1, (int) Math.round(durationSeconds / 60.0));
            if (minutes >= 60) {
                return String.format("约 %d小时%d分钟", minutes / 60, minutes % 60);
            }
            return "约 " + minutes + "分钟";
        }
    }
}
