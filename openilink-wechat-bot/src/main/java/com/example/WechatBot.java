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
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.example.vision.VisionService;
import com.example.imagegen.ImageGenService;

public class WechatBot {

    private static final Logger log = LoggerFactory.getLogger(WechatBot.class);

    private static final LocalLLMService llmService = new LocalLLMService();
    private static final WeatherService weatherService = new WeatherService();
    private static final VisionService visionService = new VisionService();
    private static final ImageGenService imageGenService = new ImageGenService();

    private static final Pattern IMAGE_GEN_PATTERN = Pattern.compile(
            "^(可以|能不能|能|请|我想)?(帮我|给我)?(画|绘制|生成|创造|做一张|给我画|帮我画|帮我生成|帮我绘制|画个|画一张|画一幅|生成一张|生成一幅|绘制一张|绘制一幅)的?\\s*(.+)");

    private static final Pattern WEATHER_KEYWORD = Pattern.compile(
            "天气|气温|温度|下雨|下雪|台风|刮风|晴天|阴天|雨天|雪天|有雨|有雪|风力|风速|大雾|霾|沙尘");
    private static final Pattern CITY_CANDIDATE = Pattern.compile(
            "([\\u4e00-\\u9fa5]{2,6}(?:市|区|县|镇|乡)?)");
    private static final Set<String> SKIP_WORDS = Set.of(
            "今天", "明天", "昨天", "后天", "前天", "早上", "上午", "中午", "下午", "晚上",
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
                log.info("收到用户 [{}] 的语音消息", userId);
                safeReply(client, userId, "收到一条语音消息，暂不支持语音识别。请发送文字消息。");
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

                    Matcher genMatcher = IMAGE_GEN_PATTERN.matcher(text.trim());
                    if (genMatcher.matches()) {
                        String prompt = genMatcher.group(4);
                        log.info("检测到图片生成请求: prompt={}", prompt);
                        safeReply(client, userId, "正在生成图片，请稍候...");
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
                        return;
                    }

                    String reply;
                    String weatherReply = tryWeatherQuery(text);
                    if (weatherReply != null) {
                        reply = weatherReply;
                    } else {
                        reply = llmService.chat(userId, text);
                    }
                    log.info("回复 [{}]: {}", userId, reply);
                    safeReply(client, userId, reply);
                }
                return;
            }
        }
    }

    private static String tryWeatherQuery(String text) {
        Matcher kwMatcher = WEATHER_KEYWORD.matcher(text);
        if (kwMatcher.find()) {
            int kwStart = kwMatcher.start();
            int searchFrom = Math.max(0, kwStart - 20);
            String beforeText = text.substring(searchFrom, kwStart);
            Matcher cityMatcher = CITY_CANDIDATE.matcher(beforeText);
            String city = null;
            while (cityMatcher.find()) {
                String c = cityMatcher.group(1);
                if (!SKIP_WORDS.contains(c)) {
                    city = c;
                }
            }
            if (city == null || city.isEmpty()) {
                city = "北京";
            }
            log.info("检测到天气查询: text={}, city={}", text, city);
            String weather = weatherService.queryWeather(city);
            if (weather != null) {
                return weather;
            }
            return "暂时查不到 " + city + " 的天气信息，请检查城市名是否正确。";
        }
        return null;
    }

    private static void safeReply(ILinkClient client, String userId, String text) {
        if (userId == null || text == null) return;
        try {
            client.sendText(userId, text);
            log.info("已回复用户 [{}]: {}", userId, text);
        } catch (Exception e) {
            log.warn("发送消息失败: {}", e.getMessage());
        }
    }
}
