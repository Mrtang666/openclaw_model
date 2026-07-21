package com.example.wechat.listener;

import com.example.wechat.handler.WeChatMessageHandler;
import com.github.wechat.ilink.sdk.ILinkClient;
import com.github.wechat.ilink.sdk.core.listener.OnMessageListener;
import com.github.wechat.ilink.sdk.core.model.MessageItem;
import com.github.wechat.ilink.sdk.core.model.VoiceItem;
import com.github.wechat.ilink.sdk.core.model.WeixinMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Base64;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
@Component
public class WeChatMessageListener implements OnMessageListener {

    private final WeChatMessageHandler messageHandler;
    private ILinkClient client;
    private final ExecutorService executor = Executors.newCachedThreadPool();

    public WeChatMessageListener(WeChatMessageHandler messageHandler) {
        this.messageHandler = messageHandler;
    }

    public void setClient(ILinkClient client) {
        this.client = client;
    }

    @Override
    public void onMessages(List<WeixinMessage> messages) {
        if (client == null) {
            log.error("ILinkClient未初始化");
            return;
        }

        for (WeixinMessage msg : messages) {
            executor.submit(() -> {
                try {
                    String fromUserId = msg.getFrom_user_id();

                    String content = extractTextContent(msg);
                    String voiceText = extractVoiceText(msg);
                    if (voiceText != null && !voiceText.isEmpty()) {
                        content = voiceText;
                        log.info("收到语音消息，转文字结果: {}", content);
                    }
                    byte[] imageData = extractImageData(msg);

                    log.info("收到消息 from={}, content={}, hasImage={}",
                            fromUserId, content, imageData != null);

                    String response = messageHandler.handleMessage(fromUserId, content, imageData);
                    log.info("handler返回的response长度: {}", response != null ? response.length() : 0);

                    if (response != null && !response.isEmpty()) {
                        // =============================================
                        // 处理音频回复 - 支持 MP3 和 PCM
                        // =============================================
                        if (response.contains("AUDIO:")) {
                            log.info("检测到音频回复，开始处理...");

                            int audioStart = response.indexOf("AUDIO:");
                            String textPart = "";
                            if (audioStart > 0) {
                                textPart = response.substring(0, audioStart);
                                textPart = textPart.replaceAll("🎵\\s*语音已生成，请点击播放\\s*", "");
                                textPart = textPart.replaceAll("\n+$", "").trim();
                            }

                            String audioSection = response.substring(audioStart + 6);
                            String format = "mp3"; // 默认
                            String audioBase64 = audioSection;

                            // 解析格式标记: AUDIO:MP3:xxx 或 AUDIO:PCM:xxx
                            if (audioSection.startsWith("MP3:")) {
                                format = "mp3";
                                audioBase64 = audioSection.substring(4);
                            } else if (audioSection.startsWith("PCM:")) {
                                format = "pcm";
                                audioBase64 = audioSection.substring(4);
                            }

                            audioBase64 = audioBase64.replaceAll("\\s+", "").trim();
                            log.info("音频格式: {}, Base64长度: {}", format, audioBase64.length());

                            try {
                                byte[] audioBytes = Base64.getDecoder().decode(audioBase64);
                                log.info("音频解码成功，大小: {} bytes", audioBytes.length);

                                // 先发送文本
                                if (!textPart.isEmpty()) {
                                    log.info("发送文本: {}", textPart.substring(0, Math.min(50, textPart.length())) + "...");
                                    client.sendText(fromUserId, textPart);
                                    Thread.sleep(300);
                                }

                                // =============================================
                                // 发送音频文件 - 根据格式选择扩展名
                                // =============================================
                                String fileName = "语音回复." + format;
                                String fileCaption = "🎵 点击播放语音 (" + format.toUpperCase() + ")";

                                // PCM格式需要特殊处理：先转为WAV再发送（微信客户端无法直接播放纯PCM）
                                if ("pcm".equals(format)) {
                                    log.info("⚠️ PCM格式，尝试转换为WAV...");
                                    byte[] wavBytes = convertPcmToWav(audioBytes, 16000, 16, 1);
                                    if (wavBytes != null) {
                                        fileName = "语音回复.wav";
                                        audioBytes = wavBytes;
                                        log.info("✅ PCM转WAV成功，大小: {} bytes", audioBytes.length);
                                    } else {
                                        log.warn("⚠️ PCM转WAV失败，尝试直接发送PCM文件");
                                        // 某些微信客户端可能支持PCM，尝试发送
                                    }
                                }

                                client.sendFile(fromUserId, audioBytes, fileName, fileCaption);
                                log.info("音频文件发送成功！格式: {}", format);
                                return;

                            } catch (Exception e) {
                                log.error("发送音频失败", e);
                                String textOnly = response.replaceAll("AUDIO:(MP3:|PCM:)?[A-Za-z0-9+/=]+", "").trim();
                                textOnly = textOnly.replaceAll("🎵\\s*语音已生成，请点击播放\\s*", "").trim();
                                if (!textOnly.isEmpty()) {
                                    client.sendText(fromUserId, textOnly);
                                } else {
                                    client.sendText(fromUserId, "❌ 语音发送失败，请重试");
                                }
                                return;
                            }
                        }

                        // =============================================
                        // 处理图片回复
                        // =============================================
                        if (response.startsWith("IMG:")) {
                            String base64Data = response.substring(4);
                            try {
                                byte[] imageBytes = Base64.getDecoder().decode(base64Data);
                                client.sendImage(fromUserId, imageBytes, "image.jpg", "生成的图片");
                                return;
                            } catch (Exception e) {
                                log.error("发送图片失败", e);
                                client.sendText(fromUserId, "❌ 图片发送失败，请重试");
                                return;
                            }
                        }

                        // =============================================
                        // 处理纯文本回复
                        // =============================================
                        log.info("发送纯文本回复，长度: {}", response.length());
                        if (response.length() > 4000) {
                            response = response.substring(0, 3900) + "...\n(消息过长已截断)";
                        }
                        client.sendText(fromUserId, response);
                        log.info("文本回复成功");
                    } else {
                        log.warn("response为空，不发送任何消息");
                    }

                } catch (Exception e) {
                    log.error("处理消息异常", e);
                    try {
                        String errorMsg = "❌ 处理失败: " + e.getMessage();
                        if (errorMsg.length() > 500) {
                            errorMsg = errorMsg.substring(0, 450) + "...";
                        }
                        client.sendText(msg.getFrom_user_id(), errorMsg);
                    } catch (Exception ex) {
                        log.error("发送错误消息失败", ex);
                    }
                }
            });
        }
    }

    /**
     * 将PCM数据转换为WAV格式
     * @param pcmData PCM音频数据
     * @param sampleRate 采样率 (Hz)
     * @param bitsPerSample 位深度
     * @param channels 声道数
     * @return WAV格式字节数组
     */
    private byte[] convertPcmToWav(byte[] pcmData, int sampleRate, int bitsPerSample, int channels) {
        try {
            int byteRate = sampleRate * channels * bitsPerSample / 8;
            int dataSize = pcmData.length;
            int totalSize = 44 + dataSize;

            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream(totalSize);

            // RIFF头
            out.write("RIFF".getBytes());
            out.write(intToBytes(totalSize - 8));
            out.write("WAVE".getBytes());

            // fmt块
            out.write("fmt ".getBytes());
            out.write(intToBytes(16)); // fmt块大小
            out.write(shortToBytes((short)1)); // 音频格式 (1 = PCM)
            out.write(shortToBytes((short)channels));
            out.write(intToBytes(sampleRate));
            out.write(intToBytes(byteRate));
            out.write(shortToBytes((short)(channels * bitsPerSample / 8))); // 块对齐
            out.write(shortToBytes((short)bitsPerSample));

            // data块
            out.write("data".getBytes());
            out.write(intToBytes(dataSize));
            out.write(pcmData);

            return out.toByteArray();

        } catch (Exception e) {
            log.error("PCM转WAV失败", e);
            return null;
        }
    }

    private byte[] intToBytes(int value) {
        return new byte[]{
                (byte)(value & 0xff),
                (byte)((value >> 8) & 0xff),
                (byte)((value >> 16) & 0xff),
                (byte)((value >> 24) & 0xff)
        };
    }

    private byte[] shortToBytes(short value) {
        return new byte[]{
                (byte)(value & 0xff),
                (byte)((value >> 8) & 0xff)
        };
    }

    private String extractTextContent(WeixinMessage msg) {
        if (msg.getItem_list() == null) return "";
        for (MessageItem item : msg.getItem_list()) {
            if (item.getType() == 1 && item.getText_item() != null) {
                return item.getText_item().getText();
            }
        }
        return "";
    }

    private String extractVoiceText(WeixinMessage msg) {
        if (msg.getItem_list() == null) return null;
        for (MessageItem item : msg.getItem_list()) {
            if (item.getType() == 3 && item.getVoice_item() != null) {
                VoiceItem voiceItem = item.getVoice_item();
                String text = voiceItem.getText();
                if (text != null && !text.isEmpty()) {
                    return text;
                }
                return "[语音消息，识别失败]";
            }
        }
        return null;
    }

    private byte[] extractImageData(WeixinMessage msg) {
        if (msg.getItem_list() == null) return null;
        for (MessageItem item : msg.getItem_list()) {
            if (item.getType() == 2 && item.getImage_item() != null) {
                try {
                    return client.downloadImageFromMessageItem(item);
                } catch (Exception e) {
                    log.error("下载图片失败", e);
                    return null;
                }
            }
        }
        return null;
    }
}