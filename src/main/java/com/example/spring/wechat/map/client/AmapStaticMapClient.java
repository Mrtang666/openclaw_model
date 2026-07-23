package com.example.spring.wechat.map.client;

import com.example.spring.wechat.image.generation.model.ImageGenerationResult;
import com.example.spring.wechat.map.model.MapPlace;
import com.example.spring.wechat.map.model.MapRouteLeg;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Component
public class AmapStaticMapClient implements MapStaticImageClient {

    private static final Logger log = LoggerFactory.getLogger(AmapStaticMapClient.class);
    private static final int MAP_WIDTH = 800;
    private static final int MAP_HEIGHT = 600;
    private static final int MAX_PATH_POINTS = 60;

    private final RestClient restClient;
    private final String key;
    private final boolean enabled;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AmapStaticMapClient(
            RestClient.Builder builder,
            @Value("${amap.map.key:}") String key,
            @Value("${amap.map.base-url:https://restapi.amap.com}") String baseUrl,
            @Value("${amap.map.static-image-enabled:true}") boolean enabled) {
        this.restClient = builder.baseUrl(baseUrl).build();
        this.key = key == null ? "" : key.strip();
        this.enabled = enabled;
    }

    @Override
    public Optional<ImageGenerationResult> renderRoute(
            String title,
            List<MapPlace> orderedPlaces,
            List<MapRouteLeg> legs) {
        if (!enabled || key.isBlank() || orderedPlaces == null || orderedPlaces.size() < 2) {
            return Optional.empty();
        }

        List<String> path = routePath(orderedPlaces, legs);
        if (path.size() < 2) {
            return Optional.empty();
        }

        BufferedImage baseMap;
        try {
            baseMap = fetchBaseMap(orderedPlaces, path);
        } catch (Exception exception) {
            log.warn("高德静态地图获取失败，改用本地路线示意图：{}", rootMessage(exception));
            baseMap = schematicMap(path, orderedPlaces);
        }

        try {
            BufferedImage composed = addLegend(baseMap, title, orderedPlaces, legs);
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            ImageIO.write(composed, "png", output);
            return Optional.of(new ImageGenerationResult(
                    "route-map",
                    "",
                    output.toByteArray(),
                    "route-map.png",
                    "image/png",
                    composed.getWidth(),
                    composed.getHeight()));
        } catch (Exception exception) {
            log.warn("合成路线图片失败：{}", rootMessage(exception));
            return Optional.empty();
        }
    }

    private BufferedImage fetchBaseMap(List<MapPlace> orderedPlaces, List<String> path) throws Exception {
        ResponseEntity<byte[]> response = restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/v3/staticmap")
                        .queryParam("key", key)
                        .queryParam("size", MAP_WIDTH + "*" + MAP_HEIGHT)
                        .queryParam("scale", 1)
                        .queryParam("markers", markers(orderedPlaces))
                        .queryParam("paths", pathParameter(path))
                        .build())
                .retrieve()
                .toEntity(byte[].class);
        byte[] mapBytes = response.getBody();
        String contentType = response.getHeaders().getFirst("Content-Type");
        if (mapBytes == null || mapBytes.length == 0) {
            throw new IllegalStateException("高德静态地图返回空内容");
        }
        if (contentType == null || !contentType.toLowerCase(java.util.Locale.ROOT).startsWith("image/")) {
            throw new IllegalStateException(amapError(mapBytes));
        }
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(mapBytes));
        if (image == null) {
            throw new IllegalStateException("高德静态地图返回了无法识别的图片");
        }
        return image;
    }

    private List<String> routePath(List<MapPlace> places, List<MapRouteLeg> legs) {
        List<String> coordinates = new ArrayList<>();
        if (legs != null) {
            for (MapRouteLeg leg : legs) {
                for (String coordinate : leg.route().polyline()) {
                    addCoordinate(coordinates, coordinate);
                }
            }
        }
        if (coordinates.size() < 2) {
            for (MapPlace place : places) {
                addCoordinate(coordinates, place.location());
            }
        }
        return downsample(coordinates, MAX_PATH_POINTS);
    }

    private String markers(List<MapPlace> places) {
        List<String> markers = new ArrayList<>();
        int limit = Math.min(places.size(), 26);
        for (int index = 0; index < limit; index++) {
            MapPlace place = places.get(index);
            if (!place.hasLocation()) {
                continue;
            }
            String color = index == 0
                    ? "0x1B8A5A"
                    : index == limit - 1 ? "0xD64545" : "0x2F80ED";
            char label = (char) ('A' + index);
            markers.add("mid," + color + "," + label + ":" + place.location());
        }
        return String.join("|", markers);
    }

    private String pathParameter(List<String> path) {
        return "6,0x2F80ED,0.9,,:" + String.join(";", path);
    }

    private BufferedImage schematicMap(List<String> path, List<MapPlace> places) {
        BufferedImage image = new BufferedImage(MAP_WIDTH, MAP_HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setColor(new Color(246, 248, 251));
            graphics.fillRect(0, 0, MAP_WIDTH, MAP_HEIGHT);
            graphics.setColor(new Color(226, 232, 240));
            graphics.setStroke(new BasicStroke(1));
            for (int x = 40; x < MAP_WIDTH; x += 80) {
                graphics.drawLine(x, 0, x, MAP_HEIGHT);
            }
            for (int y = 40; y < MAP_HEIGHT; y += 80) {
                graphics.drawLine(0, y, MAP_WIDTH, y);
            }

            Bounds bounds = bounds(path, places);
            List<java.awt.Point> points = path.stream()
                    .map(coordinate -> project(coordinate, bounds))
                    .toList();
            graphics.setColor(new Color(47, 128, 237));
            graphics.setStroke(new BasicStroke(7, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            for (int index = 1; index < points.size(); index++) {
                java.awt.Point previous = points.get(index - 1);
                java.awt.Point current = points.get(index);
                graphics.drawLine(previous.x, previous.y, current.x, current.y);
            }

            for (int index = 0; index < places.size(); index++) {
                MapPlace place = places.get(index);
                java.awt.Point point = project(place.location(), bounds);
                Color color = index == 0
                        ? new Color(27, 138, 90)
                        : index == places.size() - 1 ? new Color(214, 69, 69) : new Color(47, 128, 237);
                graphics.setColor(Color.WHITE);
                graphics.fillOval(point.x - 15, point.y - 15, 30, 30);
                graphics.setColor(color);
                graphics.fillOval(point.x - 12, point.y - 12, 24, 24);
                graphics.setColor(Color.WHITE);
                graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
                graphics.drawString(String.valueOf((char) ('A' + Math.min(index, 25))), point.x - 5, point.y + 5);
            }

            graphics.setColor(new Color(75, 85, 99));
            graphics.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));
            graphics.drawString("路线示意图（静态底图暂不可用）", 20, 28);
        } finally {
            graphics.dispose();
        }
        return image;
    }

    private Bounds bounds(List<String> path, List<MapPlace> places) {
        List<String> coordinates = new ArrayList<>(path);
        for (MapPlace place : places) {
            addCoordinate(coordinates, place.location());
        }
        double minLongitude = Double.POSITIVE_INFINITY;
        double maxLongitude = Double.NEGATIVE_INFINITY;
        double minLatitude = Double.POSITIVE_INFINITY;
        double maxLatitude = Double.NEGATIVE_INFINITY;
        for (String coordinate : coordinates) {
            double[] values = coordinate(coordinate);
            minLongitude = Math.min(minLongitude, values[0]);
            maxLongitude = Math.max(maxLongitude, values[0]);
            minLatitude = Math.min(minLatitude, values[1]);
            maxLatitude = Math.max(maxLatitude, values[1]);
        }
        if (maxLongitude - minLongitude < 0.0001) {
            minLongitude -= 0.005;
            maxLongitude += 0.005;
        }
        if (maxLatitude - minLatitude < 0.0001) {
            minLatitude -= 0.005;
            maxLatitude += 0.005;
        }
        return new Bounds(minLongitude, maxLongitude, minLatitude, maxLatitude);
    }

    private java.awt.Point project(String coordinate, Bounds bounds) {
        double[] values = coordinate(coordinate);
        int margin = 55;
        int x = margin + (int) Math.round((values[0] - bounds.minLongitude())
                / (bounds.maxLongitude() - bounds.minLongitude()) * (MAP_WIDTH - margin * 2.0));
        int y = margin + (int) Math.round((bounds.maxLatitude() - values[1])
                / (bounds.maxLatitude() - bounds.minLatitude()) * (MAP_HEIGHT - margin * 2.0));
        return new java.awt.Point(x, y);
    }

    private double[] coordinate(String value) {
        String[] parts = value.split(",", -1);
        return new double[]{Double.parseDouble(parts[0]), Double.parseDouble(parts[1])};
    }

    private String amapError(byte[] responseBody) {
        try {
            JsonNode response = objectMapper.readTree(responseBody);
            String info = response.path("info").asText("未知错误");
            String infocode = response.path("infocode").asText("");
            return "高德静态地图错误：" + info + (infocode.isBlank() ? "" : " (" + infocode + ")");
        } catch (Exception ignored) {
            return "高德静态地图返回了非图片内容";
        }
    }

    private BufferedImage addLegend(
            BufferedImage baseMap,
            String title,
            List<MapPlace> places,
            List<MapRouteLeg> legs) {
        int visiblePlaces = Math.min(places.size(), 20);
        int legendHeight = 82 + visiblePlaces * 28;
        BufferedImage image = new BufferedImage(
                baseMap.getWidth(),
                baseMap.getHeight() + legendHeight,
                BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
            graphics.drawImage(baseMap, 0, 0, null);

            int top = baseMap.getHeight();
            graphics.setColor(new Color(238, 241, 245));
            graphics.fillRect(0, top, image.getWidth(), 1);
            graphics.setColor(new Color(31, 41, 55));
            graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 22));
            graphics.drawString(cleanTitle(title), 24, top + 34);

            graphics.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 15));
            graphics.setColor(new Color(75, 85, 99));
            graphics.drawString(summary(legs), 24, top + 60);

            int y = top + 91;
            for (int index = 0; index < visiblePlaces; index++) {
                MapPlace place = places.get(index);
                Color markerColor = index == 0
                        ? new Color(27, 138, 90)
                        : index == visiblePlaces - 1 ? new Color(214, 69, 69) : new Color(47, 128, 237);
                graphics.setColor(markerColor);
                graphics.fillOval(24, y - 15, 20, 20);
                graphics.setColor(Color.WHITE);
                graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
                graphics.drawString(String.valueOf((char) ('A' + index)), 30, y);

                graphics.setColor(new Color(31, 41, 55));
                graphics.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 16));
                graphics.drawString(truncate(place.name(), 30), 55, y);
                if (index < legs.size()) {
                    MapRouteLeg leg = legs.get(index);
                    graphics.setColor(new Color(107, 114, 128));
                    graphics.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));
                    graphics.drawString(legSummary(leg), 390, y);
                }
                y += 28;
            }

            graphics.setStroke(new BasicStroke(1));
        } finally {
            graphics.dispose();
        }
        return image;
    }

    private String cleanTitle(String title) {
        return title == null || title.isBlank() ? "完整路线图" : truncate(title.strip(), 42);
    }

    private String summary(List<MapRouteLeg> legs) {
        int distance = legs.stream()
                .map(MapRouteLeg::route)
                .mapToInt(route -> value(route.distanceMeters()))
                .sum();
        int duration = legs.stream()
                .map(MapRouteLeg::route)
                .mapToInt(route -> value(route.durationSeconds()))
                .sum();
        return "共 " + legs.size() + " 段 · " + distance(distance) + " · 约 " + duration(duration);
    }

    private String legSummary(MapRouteLeg leg) {
        return distance(value(leg.route().distanceMeters())) + " · "
                + duration(value(leg.route().durationSeconds()));
    }

    private String distance(int meters) {
        if (meters < 1000) {
            return meters + " 米";
        }
        return String.format(java.util.Locale.ROOT, "%.1f 公里", meters / 1000.0);
    }

    private String duration(int seconds) {
        long minutes = Math.max(1, Math.round(seconds / 60.0));
        if (minutes < 60) {
            return minutes + " 分钟";
        }
        return (minutes / 60) + " 小时 " + (minutes % 60) + " 分钟";
    }

    private List<String> downsample(List<String> coordinates, int limit) {
        if (coordinates.size() <= limit) {
            return List.copyOf(coordinates);
        }
        List<String> sampled = new ArrayList<>(limit);
        for (int index = 0; index < limit; index++) {
            int sourceIndex = (int) Math.round(index * (coordinates.size() - 1.0) / (limit - 1.0));
            String coordinate = coordinates.get(sourceIndex);
            if (sampled.isEmpty() || !sampled.get(sampled.size() - 1).equals(coordinate)) {
                sampled.add(coordinate);
            }
        }
        return List.copyOf(sampled);
    }

    private void addCoordinate(List<String> coordinates, String coordinate) {
        if (coordinate == null || !coordinate.matches("-?\\d+(?:\\.\\d+)?,-?\\d+(?:\\.\\d+)?")) {
            return;
        }
        String clean = coordinate.strip();
        if (coordinates.isEmpty() || !coordinates.get(coordinates.size() - 1).equals(clean)) {
            coordinates.add(clean);
        }
    }

    private String truncate(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        String clean = value.strip();
        return clean.length() <= maxLength ? clean : clean.substring(0, maxLength - 1) + "…";
    }

    private int value(Integer number) {
        return number == null || number < 0 ? 0 : number;
    }

    private String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }

    private record Bounds(
            double minLongitude,
            double maxLongitude,
            double minLatitude,
            double maxLatitude) {
    }
}
