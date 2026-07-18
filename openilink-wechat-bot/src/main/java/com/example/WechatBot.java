package com.example;

import com.github.wechat.ilink.sdk.ILinkClient;
import com.github.wechat.ilink.sdk.core.config.ILinkConfig;
import com.github.wechat.ilink.sdk.core.model.*;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.example.vision.VisionService;
import com.example.imagegen.ImageGenService;
import com.example.routegen.RouteMapService;
import com.example.speech.SpeechRecognitionService;

public class WechatBot {

    private static final Logger log = LoggerFactory.getLogger(WechatBot.class);

    private static final LocalLLMService llmService = new LocalLLMService();
    private static final WeatherService weatherService = new WeatherService();
    private static final VisionService visionService = new VisionService();
    private static final ImageGenService imageGenService = new ImageGenService();
    private static final RouteMapService routeMapService = new RouteMapService();
    private static final SpeechRecognitionService speechRecognitionService = new SpeechRecognitionService();

    private static final Pattern IMAGE_GEN_MARKER = Pattern.compile("\\[\\s*IMAGE_GEN\\s*[:：]\\s*(.*?)]", Pattern.DOTALL);
    private static final Pattern LOOSE_IMAGE_GEN_MARKER = Pattern.compile("(?is)(?:\\*+\\s*)?IMAGE_GEN\\s*[:：]\\s*(.+)$");
    private static final Pattern OUTGOING_IMAGE_GEN_MARKER = Pattern.compile("(?is)\\[?\\s*\\**\\s*IMAGE_GEN\\s*[:：].*");
    private static final int MAX_SUB_REQUESTS = 5;

    private static final Pattern WEATHER_KEYWORD = Pattern.compile(
            "天气|气温|温度|下雨|下雪|台风|刮风|晴天|阴天|雨天|雪天|有雨|有雪|风力|风速|大雾|霾|沙尘");
    private static final Pattern ROUTE_MAP_KEYWORD = Pattern.compile(
            "路线图|行程图|导览图|攻略图|旅游规划图|旅行规划图|旅游路线|旅行路线|行程规划|旅游规划|旅行规划|三日游|两日游|一日游");
    private static final Pattern ROUTE_MAP_IMAGE_INTENT = Pattern.compile(
            "路线图|行程图|导览图|攻略图|规划图|生成.*图|画.*图|做.*图|出.*图");
    private static final Pattern CITY_HINT = Pattern.compile(
            "(?:在|去|到|查|查询|查的是|查询的是|城市是|目的地是)([\\u4e00-\\u9fa5]{2,6}?)(?:市)?(?:大后天|后天|明天|今天|天气|旅游|旅行|路线|行程|，|,|。|$)");
    private static final Pattern REQUEST_SEPARATOR = Pattern.compile(
            "(?:[，,。；;！!？?]\\s*)?(?:然后|接着|另外|还有|顺便|并且|同时|再帮我|再给我|再查|再画|再说|再讲|再|其次|最后)");
    private static final Pattern CITY_CANDIDATE = Pattern.compile(
            "([\\u4e00-\\u9fa5]{2,6}(?:市|区|县|镇|乡)?)");
    private static final Pattern WEATHER_DATE_WORDS = Pattern.compile(
            "大后天|后天|明天|明早|今天|今晚|现在|目前|当前|昨天|前天|\\d{1,2}[.\\-/]\\d{1,2}|\\d{1,2}月\\d{1,2}日");
    private static final Pattern WEATHER_FILLER_WORDS = Pattern.compile(
            "请问|先帮我|先给我|先查|先看|先|帮我|给我|帮忙|查一下|查下|查询一下|查询|查|看一下|看下|看看|看|告诉我|告诉|问一下|可以|能不能|能|麻烦|我想知道|想知道|一下|的|吗|呢|呀|啊|吧|哈|呗|么|怎么样|如何");
    private static final Set<String> SKIP_WORDS = Set.of(
            "大后天", "今天", "明天", "昨天", "后天", "前天", "早上", "上午", "中午", "下午", "晚上",
            "今晚", "明早", "今早", "昨晚", "现在", "目前", "当前", "这里", "那里", "这边",
            "那边", "什么", "怎么", "如何", "为啥", "为什么", "请问", "一下", "告诉");

    public static void main(String[] args) throws Exception {
        ILinkConfig config = ILinkConfig.builder()
                .heartbeatEnabled(false)
                .build();

        ILinkClient client = ILinkClient.builder()
                .config(config)
                .build();

        String qrUrl = client.executeLogin();
        log.info("请扫码登录微信: {}", qrUrl);
        try {
            QRCodeWriter writer = new QRCodeWriter();
            BitMatrix matrix = writer.encode(qrUrl, BarcodeFormat.QR_CODE, 400, 400);
            Path qrFile = Paths.get("wechat_qrcode.png");
            MatrixToImageWriter.writeToPath(matrix, "PNG", qrFile);
            log.info("二维码已保存至: {}", qrFile.toAbsolutePath());
        } catch (Exception e) {
            log.warn("生成二维码图片失败: {}", e.getMessage());
        }

        while (!client.isLoggedIn()) {
            Thread.sleep(1000);
        }
        log.info("已连接成功!");

        AtomicBoolean stopFlag = new AtomicBoolean(false);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("正在停止机器人...");
            stopFlag.set(true);
            client.close();
        }));

        while (!stopFlag.get()) {
            try {
                List<WeixinMessage> messages = client.getUpdates();
                if (messages != null) {
                    for (WeixinMessage msg : messages) {
                        handleMessage(client, msg);
                    }
                }
            } catch (Exception e) {
                if (!stopFlag.get()) {
                    log.warn("处理消息失败: {}", e.getMessage());
                }
            }
        }
    }

    private static void handleMessage(ILinkClient client, WeixinMessage msg) {
        String userId = msg.getFrom_user_id();
        String msgId = msg.getMessage_id() != null ? String.valueOf(msg.getMessage_id()) : null;

        if (msg.getItem_list() == null) return;

        for (MessageItem item : msg.getItem_list()) {
            if (item.getType() == 2 && item.getImage_item() != null) {
                log.info("收到用户 [{}] 的图片，正在识别...", userId);
                safeReply(client, userId, "正在识别图片，请稍候...");
                try {
                    byte[] imageBytes = client.downloadImageFromMessageItem(item);
                    MediaHelper.downloadAndSave(imageBytes, userId, msgId, ".jpg", "image");
                    String mimeType = "image/jpeg";
                    String description = visionService.describeImage(imageBytes, mimeType);
                    if (description != null && !description.isEmpty()) {
                        safeReply(client, userId, description);
                    } else {
                        safeReply(client, userId, "识别图片失败，请稍后重试。");
                    }
                } catch (Exception e) {
                    log.error("处理图片失败", e);
                    safeReply(client, userId, "图片识别出错了：" + e.getMessage());
                }
                return;
            }
            if (item.getType() == 3 && item.getVoice_item() != null) {
                log.info("收到用户 [{}] 的语音消息，正在识别...", userId);
                try {
                    byte[] voiceBytes = client.downloadVoiceFromMessageItem(item);
                    String voiceFileName = (msgId != null ? msgId : String.valueOf(System.currentTimeMillis())) + ".silk";
                    MediaHelper.downloadAndSave(voiceBytes, userId, msgId, ".silk", "voice");

                    String text = speechRecognitionService.transcribe(voiceBytes, voiceFileName);
                    if ((text == null || text.isBlank()) && item.getVoice_item().getText() != null) {
                        text = item.getVoice_item().getText();
                    }

                    if (text == null || text.isBlank()) {
                        safeReply(client, userId, "这条语音暂时没听清，可以再发一次或换成文字。");
                        return;
                    }

                    text = text.trim();
                    handleTextContent(client, userId, text);
                } catch (Exception e) {
                    log.error("处理语音消息失败", e);
                    safeReply(client, userId, "语音识别出错了：" + e.getMessage());
                }
                return;
            }
            if (item.getType() == 5 && item.getVideo_item() != null) {
                log.info("收到用户 [{}] 的视频", userId);
                safeReply(client, userId, "收到视频，暂不支持处理。");
                return;
            }
            if (item.getType() == 4 && item.getFile_item() != null) {
                FileItem f = item.getFile_item();
                log.info("收到用户 [{}] 的文件", userId);
                safeReply(client, userId, "收到文件，暂不支持处理。");
                return;
            }
            if (item.getType() == 1 && item.getText_item() != null) {
                String text = item.getText_item().getText();
                if (text != null && !text.isEmpty()) {
                    log.info("收到用户 [{}]: {}", userId, text);
                    handleTextContent(client, userId, text);
                }
                return;
            }
        }
    }

    private static void handleTextContent(ILinkClient client, String userId, String text) {
        List<String> requests = splitUserRequests(text);
        if (requests.size() > 1) {
            log.info("检测到复合需求: user={}, count={}, requests={}", userId, requests.size(), requests);
            for (String request : requests) {
                handleSingleTextContent(client, userId, request);
            }
            return;
        }

        handleSingleTextContent(client, userId, text);
    }

    private static void handleSingleTextContent(ILinkClient client, String userId, String text) {
        if (isRouteMapRequest(text)) {
            handleRouteMapRequest(client, userId, text);
            return;
        }

        String weatherReply = tryWeatherQuery(text);
        if (weatherReply != null) {
            log.info("回复 [{}]: {}", userId, weatherReply);
            safeReply(client, userId, weatherReply);
            return;
        }

        String reply = llmService.chat(userId, text);
        ImageGenRequest imageGenRequest = extractImageGenRequest(reply);
        if (imageGenRequest != null) {
            String prompt = imageGenRequest.prompt;
            String textPart = imageGenRequest.visibleText;
            log.info("检测到 LLM 请求图片生成: prompt={}", prompt);
            safeReply(client, userId, textPart.isEmpty() ? "正在生成图片，请稍候..." : textPart);
            generateAndSendImage(client, userId, prompt);
        } else {
            log.info("回复 [{}]: {}", userId, reply);
            safeReply(client, userId, reply);
        }
    }

    private static boolean isRouteMapRequest(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        return ROUTE_MAP_KEYWORD.matcher(text).find() && ROUTE_MAP_IMAGE_INTENT.matcher(text).find();
    }

    private static void handleRouteMapRequest(ILinkClient client, String userId, String text) {
        log.info("检测到路线图请求: user={}, text={}", userId, text);
        safeReply(client, userId, "正在生成路线图，请稍候...");
        String weatherContext = WEATHER_KEYWORD.matcher(text).find() ? tryWeatherQuery(text) : null;
        if (weatherContext != null && !weatherContext.isBlank()) {
            safeReply(client, userId, weatherContext);
        }
        RouteMapService.RouteMapResult result = routeMapService.generate(text, weatherContext);
        if (!result.isSuccess()) {
            safeReply(client, userId, "路线图生成失败: " + result.getMessage());
            return;
        }
        try {
            byte[] imgBytes = Files.readAllBytes(result.getFilePath());
            client.sendImage(userId, imgBytes, result.getFilePath().getFileName().toString(), null);
            log.info("已发送路线图给用户 [{}]: {}", userId, result.getFilePath());
        } catch (Exception e) {
            log.warn("发送路线图失败: {}", e.getMessage());
            safeReply(client, userId, "路线图已生成到本地，但发送失败，请查看 downloads/routegen 目录。");
        }
    }

    private static ImageGenRequest extractImageGenRequest(String reply) {
        if (reply == null || reply.isBlank()) {
            return null;
        }

        Matcher strictMarker = IMAGE_GEN_MARKER.matcher(reply);
        if (strictMarker.find()) {
            String prompt = cleanupImagePrompt(strictMarker.group(1));
            String visibleText = reply.replace(strictMarker.group(0), "").trim();
            return prompt.isBlank() ? null : new ImageGenRequest(prompt, cleanupVisibleReply(visibleText));
        }

        Matcher looseMarker = LOOSE_IMAGE_GEN_MARKER.matcher(reply);
        if (looseMarker.find()) {
            String prompt = cleanupImagePrompt(looseMarker.group(1));
            String visibleText = reply.substring(0, looseMarker.start()).trim();
            return prompt.isBlank() ? null : new ImageGenRequest(prompt, cleanupVisibleReply(visibleText));
        }

        return null;
    }

    private static String cleanupImagePrompt(String prompt) {
        if (prompt == null) {
            return "";
        }
        return prompt
                .replaceAll("^\\[+", "")
                .replaceAll("]+$", "")
                .replaceAll("^\\*+", "")
                .replaceAll("\\*+$", "")
                .replaceAll("[\\r\\n]+", " ")
                .trim();
    }

    private static String cleanupVisibleReply(String text) {
        return sanitizeOutgoingText(text);
    }

    private static void generateAndSendImage(ILinkClient client, String userId, String prompt) {
        ImageGenService.ImageGenResult genResult = imageGenService.generate(prompt);
        if (genResult.isSuccess()) {
            try {
                byte[] imgBytes = Files.readAllBytes(genResult.getFilePath());
                client.sendImage(userId, imgBytes, genResult.getFilePath().getFileName().toString(), null);
                log.info("已通过 CDN 发送图片给用户 [{}]", userId);
            } catch (Exception e) {
                log.warn("发送图片失败: {}", e.getMessage());
                safeReply(client, userId, "图片已保存到本地，但发送失败，请在服务器查看 downloads/imagegen 目录。");
            }
        } else {
            safeReply(client, userId, "图片生成失败: " + genResult.getMessage());
        }
    }

    private static class ImageGenRequest {
        private final String prompt;
        private final String visibleText;

        private ImageGenRequest(String prompt, String visibleText) {
            this.prompt = prompt;
            this.visibleText = visibleText;
        }
    }

    private static List<String> splitUserRequests(String text) {
        List<String> requests = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return requests;
        }

        Matcher matcher = REQUEST_SEPARATOR.matcher(text);
        int start = 0;
        while (matcher.find() && requests.size() < MAX_SUB_REQUESTS - 1) {
            addRequestPart(requests, text.substring(start, matcher.start()));
            start = matcher.end();
        }
        addRequestPart(requests, text.substring(start));

        if (requests.isEmpty()) {
            requests.add(text.trim());
        }
        return requests;
    }

    private static void addRequestPart(List<String> requests, String part) {
        if (part == null) {
            return;
        }
        String cleaned = part.trim();
        cleaned = cleaned.replaceAll("^[，,。；;！!？?、\\s]+", "");
        cleaned = cleaned.replaceAll("[，,。；;！!？?、\\s]+$", "");
        if (!cleaned.isBlank()) {
            requests.add(cleaned);
        }
    }

    private static String tryWeatherQuery(String text) {
        Matcher kwMatcher = WEATHER_KEYWORD.matcher(text);
        if (kwMatcher.find()) {
            int kwStart = kwMatcher.start();
            int searchFrom = Math.max(0, kwStart - 20);
            String beforeText = text.substring(searchFrom, kwStart);

            String city = extractWeatherCity(beforeText);
            if (city == null || city.isEmpty()) {
                city = "北京";
            }

            int dayOffset = detectDayOffset(text);
            int parsed = parseDateOffset(text);
            if (parsed >= 0) dayOffset = parsed;

            log.info("检测到天气查询: text={}, city={}, dayOffset={}", text, city, dayOffset);
            String weather = weatherService.queryWeather(city, dayOffset);
            if (weather != null) {
                return weather;
            }
            return "暂时查不到 " + city + " 的天气信息，请检查城市名是否正确。";
        }
        return null;
    }

    private static String extractWeatherCity(String beforeText) {
        if (beforeText == null || beforeText.isBlank()) {
            return null;
        }

        Matcher hintMatcher = CITY_HINT.matcher(beforeText);
        String hintedCity = null;
        while (hintMatcher.find()) {
            hintedCity = hintMatcher.group(1);
        }
        if (hintedCity != null && !hintedCity.isBlank()) {
            return hintedCity;
        }

        String cleaned = beforeText.replaceAll("[\\s，。！？、,.?；;：:]+", "");
        cleaned = WEATHER_DATE_WORDS.matcher(cleaned).replaceAll("");
        cleaned = WEATHER_FILLER_WORDS.matcher(cleaned).replaceAll("");

        String[] stripWords = SKIP_WORDS.stream()
                .sorted((a, b) -> Integer.compare(b.length(), a.length()))
                .toArray(String[]::new);
        for (String w : stripWords) {
            cleaned = cleaned.replace(w, "");
        }

        cleaned = cleaned.replaceAll("^(这边|那边|这里|那里)+", "");
        cleaned = cleaned.replaceAll("(这边|那边|这里|那里|附近|周边)$", "");
        if (cleaned.isBlank()) {
            return null;
        }

        Matcher cityMatcher = CITY_CANDIDATE.matcher(cleaned);
        String city = null;
        while (cityMatcher.find()) {
            city = cityMatcher.group(1);
        }
        return city;
    }

    private static int detectDayOffset(String text) {
        if (text.contains("大后天")) return 3;
        if (text.contains("后天")) return 2;
        if (text.contains("明天") || text.contains("明早")) return 1;
        if (text.contains("今天") || text.contains("今晚") || text.contains("现在")) return 0;

        String[] dayNames = {"", "一", "二", "三", "四", "五", "六", "日", "天"};
        java.time.DayOfWeek today = java.time.LocalDate.now().getDayOfWeek();
        int todayVal = today.getValue();
        for (int i = 1; i <= 7; i++) {
            if (text.contains("星期" + dayNames[i]) || text.contains("周" + dayNames[i])) {
                int offset = i - todayVal;
                if (offset < 0) offset += 7;
                return offset;
            }
        }
        return 0;
    }

    private static int parseDateOffset(String text) {
        java.util.regex.Matcher m;
        m = java.util.regex.Pattern.compile("(\\d{1,2})[.\\-/](\\d{1,2})(?:[^\\d]|$)").matcher(text);
        if (m.find()) {
            int month = Integer.parseInt(m.group(1));
            int day = Integer.parseInt(m.group(2));
            if (month >= 1 && month <= 12 && day >= 1 && day <= 31) {
                java.time.LocalDate target = java.time.LocalDate.of(java.time.LocalDate.now().getYear(), month, day);
                long diff = java.time.temporal.ChronoUnit.DAYS.between(java.time.LocalDate.now(), target);
                if (diff >= 0 && diff <= 14) return (int) diff;
            }
        }
        m = java.util.regex.Pattern.compile("(\\d{1,2})月(\\d{1,2})日").matcher(text);
        if (m.find()) {
            int month = Integer.parseInt(m.group(1));
            int day = Integer.parseInt(m.group(2));
            java.time.LocalDate target = java.time.LocalDate.of(java.time.LocalDate.now().getYear(), month, day);
            long diff = java.time.temporal.ChronoUnit.DAYS.between(java.time.LocalDate.now(), target);
            if (diff >= 0 && diff <= 14) return (int) diff;
        }
        return -1;
    }

    private static void safeReply(ILinkClient client, String userId, String text) {
        if (userId == null || text == null) return;
        String safeText = sanitizeOutgoingText(text);
        if (safeText.isBlank()) {
            log.info("跳过空回复: user={}", userId);
            return;
        }
        try {
            client.sendText(userId, safeText);
            log.info("已回复用户 [{}]: {}", userId, safeText);
        } catch (Exception e) {
            log.warn("发送消息失败: {}", e.getMessage());
        }
    }

    private static String sanitizeOutgoingText(String text) {
        if (text == null) {
            return "";
        }
        return OUTGOING_IMAGE_GEN_MARKER.matcher(text)
                .replaceAll("")
                .replaceAll("\\*+$", "")
                .trim();
    }
}
