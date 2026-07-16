package com.example;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.openilink.ILinkClient;
import com.openilink.auth.LoginCallbacks;
import com.openilink.model.MessageItemType;
import com.openilink.model.WeixinMessage;
import com.openilink.model.response.LoginResult;
import com.openilink.monitor.MonitorOptions;
import com.openilink.util.MessageHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

public class WechatBot {

    private static final Logger log = LoggerFactory.getLogger(WechatBot.class);

    public static void main(String[] args) {
        ILinkClient client = ILinkClient.builder()
                .token("")
                .build();

        LoginResult result = client.loginWithQR(new LoginCallbacks() {
            @Override
            public void onQRCode(String url) {
                log.info("请扫码登录微信: {}", url);
                if (url != null && !url.isEmpty()) {
                    try {
                        QRCodeWriter writer = new QRCodeWriter();
                        BitMatrix matrix = writer.encode(url, BarcodeFormat.QR_CODE, 400, 400);
                        Path qrFile = Paths.get("wechat_qrcode.png");
                        MatrixToImageWriter.writeToPath(matrix, "PNG", qrFile);
                        log.info("二维码已保存至: {}", qrFile.toAbsolutePath());
                    } catch (Exception e) {
                        log.warn("生成二维码图片失败: {}", e.getMessage());
                    }
                }
            }

            @Override
            public void onScanned() {
                log.info("已扫码，请在微信上确认登录...");
            }

            @Override
            public void onExpired(int attempt, int maxAttempts) {
                log.warn("二维码已过期，正在刷新 ({}/{})", attempt, maxAttempts);
            }
        });

        if (!result.isConnected()) {
            log.error("登录失败: {}", result.getMessage());
            return;
        }
        log.info("已连接成功! BotID={}", result.getBotId());

        AtomicBoolean stopFlag = new AtomicBoolean(false);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("正在停止机器人...");
            stopFlag.set(true);
        }));

        MonitorOptions options = MonitorOptions.builder()
                .onBufUpdate(buf -> {
                    try (FileOutputStream fos = new FileOutputStream("sync_buf.dat")) {
                        fos.write(buf.getBytes(StandardCharsets.UTF_8));
                    } catch (IOException e) {
                        log.warn("保存 sync_buf 失败", e);
                    }
                })
                .onError(e -> log.warn("监听错误: {}", e.getMessage()))
                .onSessionExpired(() -> {
                    log.error("会话已过期，请重新登录");
                    stopFlag.set(true);
                })
                .build();

        client.monitor(msg -> {
            String userId = msg.getFromUserId();
            String msgId = msg.getMessageId() != null ? String.valueOf(msg.getMessageId()) : null;

            MediaHelper.MediaInfo media = MediaHelper.extractMedia(msg);
            if (media != null) {
                String typeName;
                switch (media.type) {
                    case IMAGE: typeName = "图片"; break;
                    case VOICE: typeName = "语音"; break;
                    case VIDEO: typeName = "视频"; break;
                    case FILE: typeName = "文件"; break;
                    default: typeName = "媒体";
                }
                log.info("收到用户 [{}] 的{}(扩展名: {})", userId, typeName, media.extension);

                try {
                    Path savedPath = MediaHelper.downloadMedia(media, userId, msgId);
                    StringBuilder reply = new StringBuilder();
                    reply.append("收到一条").append(typeName).append("，已保存到: ").append(savedPath.getFileName());
                    if (media.voiceText != null && !media.voiceText.isEmpty()) {
                        reply.append("\n语音转文字: ").append(media.voiceText);
                    }
                    if (media.fileName != null) {
                        reply.append("\n文件名: ").append(media.fileName);
                    }
                    safeReply(client, userId, reply.toString());
                } catch (Exception e) {
                    log.error("下载媒体失败", e);
                    safeReply(client, userId, "收到" + typeName + "，但下载失败: " + e.getMessage());
                }
                return;
            }

            String text = MessageHelper.extractText(msg);
            if (text != null && !text.isEmpty()) {
                log.info("收到用户 [{}]: {}", userId, text);
                String reply = getFixedReply(text);
                safeReply(client, userId, reply);
            }
        }, options, stopFlag);
    }

    private static void safeReply(ILinkClient client, String userId, String text) {
        if (userId == null) return;
        if (!client.getContextToken(userId).isPresent()) {
            try {
                com.openilink.model.response.GetConfigResp resp = client.getConfig(userId, "");
                if (resp.getContextToken() != null && !resp.getContextToken().isEmpty()) {
                    client.setContextToken(userId, resp.getContextToken());
                    log.info("已获取 userId={} 的 contextToken", userId);
                } else {
                    log.warn("getConfig 返回空的 contextToken");
                }
            } catch (Exception e) {
                log.warn("获取 contextToken 失败: {}", e.getMessage());
            }
        }
        Optional<String> token = client.getContextToken(userId);
        if (!token.isPresent()) {
            log.warn("userId={} 没有 contextToken，无法回复", userId);
            return;
        }
        try {
            client.push(userId, text);
            log.info("已回复用户 [{}]: {}", userId, text);
        } catch (Exception e) {
            log.warn("发送消息失败: {}", e.getMessage());
        }
    }

    private static String getFixedReply(String message) {
        if (message.contains("你好") || message.contains("hi") || message.contains("hello")) {
            return "你好！我是智能助手，很高兴为您服务！";
        }
        if (message.contains("天气")) {
            return "今天天气不错，适合出门走走！";
        }
        if (message.contains("时间")) {
            return "现在时间请查看您的手机屏幕哦~";
        }
        if (message.contains("帮助") || message.contains("help")) {
            return "我是固定回复机器人，您可以问我关于天气、时间等问题。";
        }
        if (message.contains("名字") || message.contains("是谁")) {
            return "我是基于 OpenILink SDK 的微信机器人！";
        }
        if (message.contains("谢谢") || message.contains("感谢")) {
            return "不客气，有需要随时找我！";
        }
        if (message.contains("再见") || message.contains("拜拜") || message.contains("bye")) {
            return "再见！祝您生活愉快！";
        }
        return "收到您的消息了，我还在学习中，暂时只能回复固定内容哦~";
    }
}
