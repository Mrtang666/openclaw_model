package com.example.wechat.listener;

import com.example.wechat.handler.WeChatMessageHandler;
import com.github.wechat.ilink.sdk.ILinkClient;
import com.github.wechat.ilink.sdk.core.listener.OnMessageListener;
import com.github.wechat.ilink.sdk.core.model.FileItem;
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

                    // 提取文本内容
                    String content = extractTextContent(msg);

                    // 提取语音转文字内容
                    String voiceText = extractVoiceText(msg);
                    if (voiceText != null && !voiceText.isEmpty()) {
                        content = voiceText;
                        log.info("收到语音消息，转文字结果: {}", content);
                    }

                    // 提取图片数据
                    byte[] imageData = extractImageData(msg);

                    // 提取PDF文件数据
                    byte[] pdfData = extractPdfData(msg);
                    String fileName = extractFileName(msg);

                    log.info("收到消息 from={}, content={}, hasImage={}, hasPdf={}",
                            fromUserId, content, imageData != null, pdfData != null);

                    // =============================================
                    // 核心：如果检测到PDF文件，自动调用OCR识别
                    // =============================================
                    if (pdfData != null) {
                        log.info("📄 检测到PDF文件，自动调用OCR识别: {}", fileName);
                        String ocrMessage = "识别这个PDF文件";
                        String response = messageHandler.handleMessage(fromUserId, ocrMessage, imageData, pdfData, fileName);
                        if (response != null && !response.isEmpty()) {
                            client.sendText(fromUserId, response);
                            log.info("PDF识别结果已发送");
                        }
                        return;
                    }

                    // 处理普通消息（无PDF）
                    String response = messageHandler.handleMessage(fromUserId, content, imageData, null, null);

                    if (response != null && !response.isEmpty()) {
                        // =============================================
                        // 处理 PDF 文件回复
                        // =============================================
                        if (response.startsWith("PDF:")) {
                            handlePdfResponse(fromUserId, response);
                            return;
                        }

                        // =============================================
                        // 处理音频回复
                        // =============================================
                        if (response.contains("AUDIO:")) {
                            handleAudioResponse(fromUserId, response);
                            return;
                        }

                        // =============================================
                        // 处理图片回复
                        // =============================================
                        if (response.startsWith("IMG:")) {
                            handleImageResponse(fromUserId, response);
                            return;
                        }

                        // =============================================
                        // 处理纯文本回复
                        // =============================================
                        if (response.length() > 4000) {
                            response = response.substring(0, 3900) + "...\n(消息过长已截断)";
                        }
                        client.sendText(fromUserId, response);
                        log.info("文本回复成功");
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

    // =============================================
    // PDF文件回复处理
    // =============================================

    private void handlePdfResponse(String fromUserId, String response) throws Exception {
        // 格式: PDF:base64:filename.pdf
        String[] parts = response.split(":", 3);
        if (parts.length < 3) {
            client.sendText(fromUserId, "❌ PDF格式错误");
            return;
        }

        String base64Data = parts[1];
        String fileName = parts[2];

        try {
            byte[] pdfBytes = Base64.getDecoder().decode(base64Data);
            log.info("PDF解码成功，大小: {} bytes", pdfBytes.length);

            // 发送PDF文件
            client.sendFile(fromUserId, pdfBytes, fileName, "📄 点击查看PDF文档");
            log.info("PDF文件发送成功: {}", fileName);
        } catch (IllegalArgumentException e) {
            log.error("PDF解码失败", e);
            client.sendText(fromUserId, "❌ PDF文件格式错误");
        }
    }

    // =============================================
    // 音频回复处理
    // =============================================

    private void handleAudioResponse(String fromUserId, String response) throws Exception {
        int audioStart = response.indexOf("AUDIO:");
        String textPart = "";
        if (audioStart > 0) {
            textPart = response.substring(0, audioStart);
            textPart = textPart.replaceAll("🎵\\s*语音已生成，请点击播放\\s*", "");
            textPart = textPart.replaceAll("\n+$", "").trim();
        }

        String audioSection = response.substring(audioStart + 6);
        String format = "mp3";
        String audioBase64 = audioSection;

        if (audioSection.startsWith("MP3:")) {
            format = "mp3";
            audioBase64 = audioSection.substring(4);
        } else if (audioSection.startsWith("PCM:")) {
            format = "pcm";
            audioBase64 = audioSection.substring(4);
        }

        audioBase64 = audioBase64.replaceAll("\\s+", "").trim();
        log.info("音频格式: {}, Base64长度: {}", format, audioBase64.length());

        byte[] audioBytes = Base64.getDecoder().decode(audioBase64);
        log.info("音频解码成功，大小: {} bytes", audioBytes.length);

        if (!textPart.isEmpty()) {
            client.sendText(fromUserId, textPart);
            Thread.sleep(300);
        }

        String fileName = "语音回复." + format;
        client.sendFile(fromUserId, audioBytes, fileName, "🎵 点击播放语音");
        log.info("音频文件发送成功！格式: {}", format);
    }

    // =============================================
    // 图片回复处理
    // =============================================

    private void handleImageResponse(String fromUserId, String response) throws Exception {
        String base64Data = response.substring(4);
        byte[] imageBytes = Base64.getDecoder().decode(base64Data);
        client.sendImage(fromUserId, imageBytes, "image.jpg", "生成的图片");
    }

    // =============================================
    // 内容提取方法
    // =============================================

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

    /**
     * 提取PDF文件数据
     */
    private byte[] extractPdfData(WeixinMessage msg) {
        if (msg.getItem_list() == null) return null;
        for (MessageItem item : msg.getItem_list()) {
            if (item.getType() == 4 && item.getFile_item() != null) {
                FileItem fileItem = item.getFile_item();
                String fileName = fileItem.getFile_name();
                if (fileName != null && (fileName.toLowerCase().endsWith(".pdf") || fileName.toLowerCase().endsWith(".PDF"))) {
                    try {
                        log.info("📄 检测到PDF文件: {}", fileName);
                        return client.downloadFileFromMessageItem(item);
                    } catch (Exception e) {
                        log.error("下载PDF文件失败", e);
                        return null;
                    }
                }
            }
        }
        return null;
    }

    private String extractFileName(WeixinMessage msg) {
        if (msg.getItem_list() == null) return null;
        for (MessageItem item : msg.getItem_list()) {
            if (item.getType() == 4 && item.getFile_item() != null) {
                return item.getFile_item().getFile_name();
            }
        }
        return null;
    }
}