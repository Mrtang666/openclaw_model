package com.example.routegen;

import com.example.LocalLLMService;
import com.example.map.BaiduMapService;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class RouteMapService {

    private static final Logger log = LoggerFactory.getLogger(RouteMapService.class);
    private static final int WIDTH = 1600;
    private static final int HEIGHT = 1000;
    private static final int MAX_DAYS = 3;
    private static final int MAX_STOPS_PER_DAY = 5;

    private final String apiKey;
    private final String apiUrl;
    private final String model;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final BaiduMapService baiduMapService;

    public RouteMapService() {
        LocalLLMService.Config cfg = LocalLLMService.getConfig();
        this.apiKey = envOrDefault("LLM_API_KEY", cfg.getApiKey());
        this.apiUrl = envOrDefault("LLM_API_URL", cfg.getApiUrl());
        this.model = envOrDefault("LLM_MODEL", cfg.getModel());
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();
        this.objectMapper = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        this.baiduMapService = new BaiduMapService();
    }

    public RouteMapResult generate(String userRequest, String weatherContext) {
        try {
            RoutePlan plan = createPlan(userRequest, weatherContext);
            enrichWithMapApi(plan);
            Path file = render(plan);
            return new RouteMapResult(true, "路线图生成成功", file);
        } catch (Exception e) {
            log.error("路线图生成失败", e);
            return new RouteMapResult(false, e.getMessage(), null);
        }
    }

    private RoutePlan createPlan(String userRequest, String weatherContext) {
        try {
            String reply = callPlanner(userRequest, weatherContext);
            RoutePlan plan = parsePlan(reply);
            if (plan != null) {
                return plan;
            }
        } catch (Exception e) {
            log.warn("路线规划 JSON 生成失败，使用兜底路线图: {}", e.getMessage());
        }
        return fallbackPlan(userRequest, weatherContext);
    }

    private void enrichWithMapApi(RoutePlan plan) {
        if (plan == null || !baiduMapService.isEnabled()) {
            return;
        }
        int updated = 0;
        for (RouteDay day : plan.days) {
            for (int i = 1; i < day.stops.size(); i++) {
                RouteStop previous = day.stops.get(i - 1);
                RouteStop current = day.stops.get(i);
                Optional<BaiduMapService.RouteLeg> leg = baiduMapService.queryBestLeg(plan.city, previous.place, current.place);
                if (leg.isPresent()) {
                    BaiduMapService.RouteLeg routeLeg = leg.get();
                    current.transport = routeLeg.getMode() + " " + routeLeg.durationText();
                    current.distance = routeLeg.distanceText();
                    updated++;
                }
            }
        }
        if (updated > 0) {
            plan.tips.add(0, "相邻景点距离和耗时已接入百度地图 API 估算。");
            log.info("百度地图路线信息已写入路线图: city={}, legs={}", plan.city, updated);
        }
    }

    private String callPlanner(String userRequest, String weatherContext) throws Exception {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", model);
        body.put("temperature", 0.2);
        body.put("max_tokens", 2400);
        body.put("stream", false);

        ArrayNode messages = body.putArray("messages");
        ObjectNode system = messages.addObject();
        system.put("role", "system");
        system.put("content",
                "你是严谨的中文旅行路线规划师。只输出一个 JSON 对象，不要 Markdown，不要解释。"
                        + "JSON 字段: title, city, summary, days, tips。"
                        + "days 是数组，每项包含 day, title, weather, stops。"
                        + "stops 是数组，每项包含 time, place, activity, transport, distance。"
                        + "最多 3 天，每天最多 5 个景点。距离和交通方式用保守估计，文字短一些，适合画进路线图。");

        ObjectNode user = messages.addObject();
        user.put("role", "user");
        StringBuilder prompt = new StringBuilder();
        prompt.append("用户需求：").append(userRequest == null ? "" : userRequest);
        if (weatherContext != null && !weatherContext.isBlank()) {
            prompt.append("\n已查询到的天气参考：").append(weatherContext);
        }
        prompt.append("\n请输出可直接解析的 JSON。");
        user.put("content", prompt.toString());

        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(apiUrl))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(90));
        if (apiKey != null && !apiKey.isBlank()) {
            builder.header("Authorization", "Bearer " + apiKey);
        }

        HttpResponse<String> response = httpClient.send(
                builder.POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body), StandardCharsets.UTF_8)).build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        if (response.statusCode() != 200) {
            throw new IllegalStateException("路线规划 API 错误: " + response.statusCode());
        }

        JsonNode root = objectMapper.readTree(response.body());
        return root.path("choices").path(0).path("message").path("content").asText();
    }

    private RoutePlan parsePlan(String reply) throws Exception {
        String json = extractJson(reply);
        if (json == null || json.isBlank()) {
            return null;
        }
        JsonNode root = objectMapper.readTree(json);
        RoutePlan plan = new RoutePlan();
        plan.title = text(root, "title", "旅行路线图");
        plan.city = text(root, "city", "目的地");
        plan.summary = text(root, "summary", "按景点顺序规划路线，兼顾交通与游览节奏。");
        plan.tips.addAll(readStringList(root.path("tips"), 4));

        JsonNode days = root.path("days");
        if (days.isArray()) {
            for (JsonNode dayNode : days) {
                if (plan.days.size() >= MAX_DAYS) break;
                RouteDay day = new RouteDay();
                day.day = text(dayNode, "day", "Day " + (plan.days.size() + 1));
                day.title = text(dayNode, "title", "行程安排");
                day.weather = text(dayNode, "weather", "");
                JsonNode stops = dayNode.path("stops");
                if (stops.isArray()) {
                    for (JsonNode stopNode : stops) {
                        if (day.stops.size() >= MAX_STOPS_PER_DAY) break;
                        RouteStop stop = new RouteStop();
                        stop.time = text(stopNode, "time", "");
                        stop.place = text(stopNode, "place", "景点");
                        stop.activity = text(stopNode, "activity", "游览");
                        stop.transport = text(stopNode, "transport", "步行/打车");
                        stop.distance = text(stopNode, "distance", "约数公里");
                        day.stops.add(stop);
                    }
                }
                if (!day.stops.isEmpty()) {
                    plan.days.add(day);
                }
            }
        }
        return plan.days.isEmpty() ? null : plan;
    }

    private String extractJson(String text) {
        if (text == null) return null;
        String cleaned = text.trim()
                .replaceAll("^```json\\s*", "")
                .replaceAll("^```\\s*", "")
                .replaceAll("\\s*```$", "");
        int start = cleaned.indexOf('{');
        int end = cleaned.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return cleaned.substring(start, end + 1);
        }
        return cleaned;
    }

    private Path render(RoutePlan plan) throws Exception {
        Files.createDirectories(Paths.get("downloads", "routegen"));
        Path output = Paths.get("downloads", "routegen", "route_map_" + UUID.randomUUID() + ".png");

        BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            paintBackground(g);
            paintHeader(g, plan);
            paintDayCards(g, plan);
            paintTips(g, plan);
        } finally {
            g.dispose();
        }

        ImageIO.write(image, "png", output.toFile());
        log.info("路线图已生成: {}", output.toAbsolutePath());
        return output;
    }

    private void paintBackground(Graphics2D g) {
        g.setColor(new Color(246, 248, 244));
        g.fillRect(0, 0, WIDTH, HEIGHT);
        g.setColor(new Color(22, 96, 90));
        g.fillRoundRect(44, 36, WIDTH - 88, 170, 34, 34);
        g.setColor(new Color(232, 103, 81));
        g.fillOval(WIDTH - 190, 62, 88, 88);
        g.setColor(new Color(250, 197, 89));
        g.fillOval(WIDTH - 118, 112, 42, 42);
    }

    private void paintHeader(Graphics2D g, RoutePlan plan) {
        g.setColor(Color.WHITE);
        g.setFont(font(Font.BOLD, 46));
        drawStringFit(g, nonBlank(plan.title, plan.city + "旅行路线图"), 88, 100, 980);
        g.setFont(font(Font.PLAIN, 24));
        List<String> lines = wrapText(g, nonBlank(plan.summary, "路线按时间顺序排列，距离与交通方式供出行参考。"), 980);
        int y = 144;
        for (int i = 0; i < Math.min(lines.size(), 2); i++) {
            g.drawString(lines.get(i), 90, y);
            y += 34;
        }
        g.setFont(font(Font.BOLD, 28));
        g.drawString(nonBlank(plan.city, "目的地"), WIDTH - 352, 106);
        g.setFont(font(Font.PLAIN, 20));
        g.drawString("结构化路线图", WIDTH - 352, 146);
    }

    private void paintDayCards(Graphics2D g, RoutePlan plan) {
        int cardCount = Math.max(1, plan.days.size());
        int gap = 28;
        int cardW = (WIDTH - 88 - gap * (cardCount - 1)) / cardCount;
        int cardH = 610;
        int startX = 44;
        int y = 238;

        for (int i = 0; i < plan.days.size(); i++) {
            int x = startX + i * (cardW + gap);
            paintDayCard(g, plan.days.get(i), x, y, cardW, cardH, i);
        }
    }

    private void paintDayCard(Graphics2D g, RouteDay day, int x, int y, int w, int h, int index) {
        g.setColor(new Color(255, 255, 255));
        g.fill(new RoundRectangle2D.Double(x, y, w, h, 24, 24));
        g.setColor(new Color(213, 221, 215));
        g.setStroke(new BasicStroke(2f));
        g.draw(new RoundRectangle2D.Double(x, y, w, h, 24, 24));

        Color accent = accent(index);
        g.setColor(accent);
        g.fillRoundRect(x + 24, y + 22, 104, 42, 20, 20);
        g.setColor(Color.WHITE);
        g.setFont(font(Font.BOLD, 22));
        g.drawString(nonBlank(day.day, "Day " + (index + 1)), x + 42, y + 50);

        g.setColor(new Color(28, 42, 48));
        g.setFont(font(Font.BOLD, 27));
        drawStringFit(g, nonBlank(day.title, "行程安排"), x + 24, y + 98, w - 48);

        if (day.weather != null && !day.weather.isBlank()) {
            g.setFont(font(Font.PLAIN, 18));
            g.setColor(new Color(72, 89, 95));
            drawStringFit(g, "天气参考：" + day.weather, x + 24, y + 132, w - 48);
        }

        int lineX = x + 46;
        int stopY = y + 178;
        int stopGap = Math.max(88, (h - 210) / Math.max(1, day.stops.size()));
        g.setStroke(new BasicStroke(4f));
        g.setColor(new Color(218, 225, 220));
        g.drawLine(lineX, stopY - 24, lineX, y + h - 42);

        for (int i = 0; i < day.stops.size(); i++) {
            RouteStop stop = day.stops.get(i);
            int cy = stopY + i * stopGap;
            g.setColor(accent);
            g.fillOval(lineX - 14, cy - 14, 28, 28);
            g.setColor(Color.WHITE);
            g.setFont(font(Font.BOLD, 16));
            g.drawString(String.valueOf(i + 1), lineX - 5, cy + 6);

            int textX = x + 82;
            g.setColor(new Color(28, 42, 48));
            g.setFont(font(Font.BOLD, 22));
            drawStringFit(g, nonBlank(stop.place, "景点"), textX, cy - 4, w - 106);

            g.setFont(font(Font.PLAIN, 17));
            g.setColor(new Color(92, 101, 106));
            String meta = joinNonBlank(" | ", stop.time, stop.transport, stop.distance);
            drawStringFit(g, meta, textX, cy + 24, w - 106);

            g.setFont(font(Font.PLAIN, 17));
            List<String> acts = wrapText(g, nonBlank(stop.activity, "游览"), w - 106);
            if (!acts.isEmpty()) {
                drawStringFit(g, acts.get(0), textX, cy + 50, w - 106);
            }
        }
    }

    private void paintTips(Graphics2D g, RoutePlan plan) {
        int x = 44;
        int y = 880;
        int w = WIDTH - 88;
        g.setColor(new Color(33, 44, 49));
        g.setFont(font(Font.BOLD, 24));
        g.drawString("出行提示", x, y);
        g.setFont(font(Font.PLAIN, 19));
        g.setColor(new Color(75, 86, 91));
        List<String> tips = plan.tips.isEmpty() ? List.of("距离与交通为参考估计，出发前建议用地图 App 复核实时路况。") : plan.tips;
        int ty = y + 36;
        for (String tip : tips) {
            List<String> lines = wrapText(g, "• " + tip, w);
            for (String line : lines) {
                if (ty > HEIGHT - 34) return;
                g.drawString(line, x, ty);
                ty += 28;
            }
        }
    }

    private RoutePlan fallbackPlan(String userRequest, String weatherContext) {
        RoutePlan plan = new RoutePlan();
        plan.city = guessCity(userRequest);
        plan.title = plan.city + "旅行路线图";
        plan.summary = "根据你的需求生成示意行程图，适合先看整体节奏，再用地图 App 复核实时路线。";
        if (weatherContext != null && !weatherContext.isBlank()) {
            plan.tips.add("已参考天气信息，建议根据降雨和气温调整室内外景点顺序。");
        }
        plan.tips.add("热门景点建议提前预约，跨城区移动优先地铁或打车。");

        RouteDay day = new RouteDay();
        day.day = "Day 1";
        day.title = "核心景点串联";
        day.weather = weatherContext == null ? "" : "按已查询天气调整节奏";
        day.stops.add(stop("09:00", "城市地标", "从代表性景点开始游览", "地铁/打车", "约 3-6 km"));
        day.stops.add(stop("11:00", "经典景区", "安排主要游览和拍照", "步行", "约 1-2 km"));
        day.stops.add(stop("14:00", "文化街区", "午餐后逛街区和小店", "地铁/打车", "约 4-8 km"));
        day.stops.add(stop("17:30", "夜景区域", "晚餐和夜景收尾", "步行/打车", "约 2-5 km"));
        plan.days.add(day);
        return plan;
    }

    private RouteStop stop(String time, String place, String activity, String transport, String distance) {
        RouteStop stop = new RouteStop();
        stop.time = time;
        stop.place = place;
        stop.activity = activity;
        stop.transport = transport;
        stop.distance = distance;
        return stop;
    }

    private String guessCity(String text) {
        if (text == null) return "目的地";
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("([\\u4e00-\\u9fa5]{2,6})(?:三日游|两日游|一日游|旅游|旅行|路线|行程)").matcher(text);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return "目的地";
    }

    private String text(JsonNode node, String field, String defaultValue) {
        String value = node.path(field).asText(null);
        return value == null || value.isBlank() ? defaultValue : value.trim();
    }

    private List<String> readStringList(JsonNode node, int max) {
        List<String> values = new ArrayList<>();
        if (node.isArray()) {
            for (JsonNode item : node) {
                if (values.size() >= max) break;
                String value = item.asText("");
                if (!value.isBlank()) values.add(value.trim());
            }
        }
        return values;
    }

    private List<String> wrapText(Graphics2D g, String text, int maxWidth) {
        List<String> lines = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return lines;
        }
        FontMetrics fm = g.getFontMetrics();
        StringBuilder line = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            String next = line.toString() + ch;
            if (fm.stringWidth(next) > maxWidth && line.length() > 0) {
                lines.add(line.toString());
                line.setLength(0);
            }
            line.append(ch);
        }
        if (line.length() > 0) lines.add(line.toString());
        return lines;
    }

    private void drawStringFit(Graphics2D g, String text, int x, int y, int maxWidth) {
        if (text == null) return;
        FontMetrics fm = g.getFontMetrics();
        String value = text;
        while (value.length() > 1 && fm.stringWidth(value) > maxWidth) {
            value = value.substring(0, value.length() - 2) + "...";
        }
        g.drawString(value, x, y);
    }

    private Font font(int style, int size) {
        return new Font("Microsoft YaHei", style, size);
    }

    private Color accent(int index) {
        Color[] colors = {
                new Color(22, 132, 118),
                new Color(220, 93, 73),
                new Color(73, 105, 167)
        };
        return colors[index % colors.length];
    }

    private String nonBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private String joinNonBlank(String separator, String... values) {
        List<String> parts = new ArrayList<>();
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                parts.add(value.trim());
            }
        }
        return String.join(separator, parts);
    }

    private String envOrDefault(String key, String defaultValue) {
        String value = System.getenv(key);
        return value == null || value.isBlank() ? defaultValue : value;
    }

    private static class RoutePlan {
        private String title;
        private String city;
        private String summary;
        private final List<RouteDay> days = new ArrayList<>();
        private final List<String> tips = new ArrayList<>();
    }

    private static class RouteDay {
        private String day;
        private String title;
        private String weather;
        private final List<RouteStop> stops = new ArrayList<>();
    }

    private static class RouteStop {
        private String time;
        private String place;
        private String activity;
        private String transport;
        private String distance;
    }

    public static class RouteMapResult {
        private final boolean success;
        private final String message;
        private final Path filePath;

        public RouteMapResult(boolean success, String message, Path filePath) {
            this.success = success;
            this.message = message;
            this.filePath = filePath;
        }

        public boolean isSuccess() {
            return success;
        }

        public String getMessage() {
            return message;
        }

        public Path getFilePath() {
            return filePath;
        }
    }
}
