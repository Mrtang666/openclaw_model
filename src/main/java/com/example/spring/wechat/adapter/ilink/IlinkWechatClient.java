package com.example.spring.wechat.adapter.ilink;


/**
 * 微信 iLink 适配层，负责消息转换、下载和发送。
 */
import com.github.wechat.ilink.sdk.ILinkClient;
import com.github.wechat.ilink.sdk.core.login.LoginContext;
import com.github.wechat.ilink.sdk.core.model.MessageItem;
import com.github.wechat.ilink.sdk.core.model.WeixinMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import com.example.spring.wechat.adapter.WechatClient;
import com.example.spring.wechat.model.ImageSourceType;
import com.example.spring.wechat.model.VoiceSourceType;
import com.example.spring.wechat.model.WechatIncomingFile;
import com.example.spring.wechat.model.WechatIncomingImage;
import com.example.spring.wechat.model.WechatIncomingMessage;
import com.example.spring.wechat.model.WechatIncomingVoice;
import com.example.spring.wechat.model.WechatLoginInfo;

public class IlinkWechatClient implements WechatClient {

    private static final Logger log = LoggerFactory.getLogger(IlinkWechatClient.class);

    private final ILinkClient delegate;

    public IlinkWechatClient() {
        this(ILinkClient.builder().build());
    }

    IlinkWechatClient(ILinkClient delegate) {
        this.delegate = delegate;
    }

    @Override
    public String executeLogin() {
        return delegate.executeLogin();
    }

    @Override
    public CompletableFuture<WechatLoginInfo> loginFuture() {
        return delegate.getLoginFuture().thenApply(this::toLoginInfo);
    }

    @Override
    public List<WechatIncomingMessage> getUpdates() throws IOException {
        List<WeixinMessage> messages = delegate.getUpdates();
        if (messages == null || messages.isEmpty()) {
            log.debug("iLink getUpdates 没有新消息");
            return List.of();
        }

        log.debug("iLink getUpdates 拉取到 {} 条原始消息", messages.size());
        List<WechatIncomingMessage> incomingMessages = new ArrayList<>();
        for (WeixinMessage message : messages) {
            WechatIncomingMessage incoming = toIncomingMessage(message);
            if (incoming != null) {
                incomingMessages.add(incoming);
            }
        }

        log.debug("iLink 本次映射出 {} 条消息", incomingMessages.size());
        return incomingMessages;
    }

    @Override
    public void sendText(String toUserId, String text) throws IOException {
        delegate.sendText(toUserId, text);
    }

    @Override
    public void sendImage(String toUserId, byte[] imageBytes, String fileName, String caption) throws IOException {
        delegate.sendImage(toUserId, imageBytes, fileName, caption);
    }

    @Override
    public void sendFile(String toUserId, byte[] fileBytes, String fileName, String caption) throws IOException {
        delegate.sendFile(toUserId, fileBytes, fileName, caption);
    }

    @Override
    public void sendVoice(
            String toUserId,
            byte[] voiceBytes,
            String fileName,
            Integer playTimeMs,
            Integer sampleRate,
            Integer encodeType,
            Integer bitsPerSample,
            String transcriptText) throws IOException {
        delegate.sendVoice(
                toUserId,
                voiceBytes,
                fileName,
                playTimeMs,
                sampleRate,
                null,
                encodeType,
                bitsPerSample,
                transcriptText);
    }

    @Override
    public void close() {
        delegate.close();
    }

    private WechatIncomingMessage toIncomingMessage(WeixinMessage message) throws IOException {
        if (message == null || message.getFrom_user_id() == null || message.getItem_list() == null) {
            log.debug("iLink 消息被跳过：fromUserId 或 itemList 为空，messageId={}, messageType={}",
                    valueOrUnknown(message == null ? null : String.valueOf(message.getMessage_id())),
                    valueOrUnknown(message == null ? null : String.valueOf(message.getMessage_type())));
            return null;
        }

        StringBuilder text = new StringBuilder();
        List<WechatIncomingImage> images = new ArrayList<>();
        List<WechatIncomingVoice> voices = new ArrayList<>();
        List<WechatIncomingFile> files = new ArrayList<>();
        int imageIndex = 0;
        int voiceIndex = 0;
        int fileIndex = 0;
        for (MessageItem item : message.getItem_list()) {
            if (item == null) {
                continue;
            }

            if (item.getText_item() != null && item.getText_item().getText() != null) {
                String chunk = item.getText_item().getText().strip();
                if (!chunk.isBlank()) {
                    if (text.length() > 0) {
                        text.append(System.lineSeparator());
                    }
                    text.append(chunk);
                }
            }

            if (item.getImage_item() != null) {
                try {
                    byte[] imageBytes = delegate.downloadImageFromMessageItem(item);
                    String sourceReference = buildSourceReference(message, imageIndex);
                    images.add(new WechatIncomingImage(
                            ImageSourceType.WECHAT_ATTACHMENT,
                            sourceReference,
                            imageBytes,
                            null,
                            buildFileName(message, imageIndex),
                            null,
                            null,
                            null));
                    imageIndex++;
                } catch (Exception exception) {
                    log.warn("iLink 图片下载失败，messageId={}, fromUserId={}, error={}",
                            valueOrUnknown(String.valueOf(message.getMessage_id())),
                            valueOrUnknown(message.getFrom_user_id()),
                            rootMessage(exception));
                }
            }

            if (item.getVoice_item() != null) {
                String sourceReference = buildVoiceSourceReference(message, voiceIndex);
                String fileName = buildVoiceFileName(message, voiceIndex, item);
                Integer durationMs = item.getVoice_item().getPlaytime();
                Integer sampleRate = item.getVoice_item().getSample_rate();
                String format = formatForVoice(item);
                String mimeType = mimeTypeForVoice(item);
                String embeddedText = item.getVoice_item().getText();
                try {
                    byte[] voiceBytes = delegate.downloadVoiceFromMessageItem(item);
                    voices.add(new WechatIncomingVoice(
                            VoiceSourceType.WECHAT_ATTACHMENT,
                            sourceReference,
                            voiceBytes,
                            mimeType,
                            fileName,
                            durationMs,
                            sampleRate,
                            format,
                            embeddedText));
                } catch (Exception exception) {
                    log.warn("iLink 语音下载失败，messageId={}, fromUserId={}, error={}",
                            valueOrUnknown(String.valueOf(message.getMessage_id())),
                            valueOrUnknown(message.getFrom_user_id()),
                            rootMessage(exception));
                    voices.add(new WechatIncomingVoice(
                            VoiceSourceType.WECHAT_ATTACHMENT,
                            sourceReference,
                            null,
                            mimeType,
                            fileName,
                            durationMs,
                            sampleRate,
                            format,
                            embeddedText));
                }
                voiceIndex++;
            }

            if (item.getFile_item() != null) {
                String sourceReference = buildFileSourceReference(message, fileIndex);
                String fileName = item.getFile_item().getFile_name();
                Long size = parseLong(item.getFile_item().getLen());
                String md5 = item.getFile_item().getMd5();
                try {
                    byte[] fileBytes = delegate.downloadFileFromMessageItem(item);
                    files.add(new WechatIncomingFile(
                            sourceReference,
                            fileName,
                            mimeTypeForFile(fileName),
                            fileBytes,
                            size,
                            md5,
                            null));
                } catch (Exception exception) {
                    log.warn("iLink 文件下载失败，messageId={}, fromUserId={}, fileName={}, error={}",
                            valueOrUnknown(String.valueOf(message.getMessage_id())),
                            valueOrUnknown(message.getFrom_user_id()),
                            valueOrUnknown(fileName),
                            rootMessage(exception));
                    files.add(new WechatIncomingFile(
                            sourceReference,
                            fileName,
                            mimeTypeForFile(fileName),
                            null,
                            size,
                            md5,
                            null));
                }
                fileIndex++;
            }
        }

        String normalizedText = text.toString().strip();
        if (normalizedText.isBlank() && images.isEmpty() && voices.isEmpty() && files.isEmpty()) {
            log.debug("iLink 消息没有可用文本、图片、语音或文件，messageId={}", valueOrUnknown(String.valueOf(message.getMessage_id())));
            return null;
        }

        log.info(
                "iLink 收到消息，messageId={}, fromUserId={}, contextToken={}, text={}, imageCount={}, voiceCount={}, fileCount={}",
                valueOrUnknown(String.valueOf(message.getMessage_id())),
                valueOrUnknown(message.getFrom_user_id()),
                valueOrUnknown(message.getContext_token()),
                preview(normalizedText),
                images.size(),
                voices.size(),
                files.size());

        return new WechatIncomingMessage(
                message.getMessage_id() == null ? null : String.valueOf(message.getMessage_id()),
                message.getFrom_user_id(),
                message.getContext_token(),
                normalizedText,
                images,
                voices,
                files);
    }

    private WechatLoginInfo toLoginInfo(LoginContext context) {
        return new WechatLoginInfo(context.getBotId());
    }

    private String buildSourceReference(WeixinMessage message, int imageIndex) {
        return "wechat://" + valueOrUnknown(String.valueOf(message.getMessage_id())) + "/image/" + (imageIndex + 1);
    }

    private String buildVoiceSourceReference(WeixinMessage message, int voiceIndex) {
        return "wechat://" + valueOrUnknown(String.valueOf(message.getMessage_id())) + "/voice/" + (voiceIndex + 1);
    }

    private String buildFileSourceReference(WeixinMessage message, int fileIndex) {
        return "wechat://" + valueOrUnknown(String.valueOf(message.getMessage_id())) + "/file/" + (fileIndex + 1);
    }

    private String buildFileName(WeixinMessage message, int imageIndex) {
        return "wechat-" + valueOrUnknown(String.valueOf(message.getMessage_id())) + "-image-" + (imageIndex + 1) + ".png";
    }

    private String buildVoiceFileName(WeixinMessage message, int voiceIndex, MessageItem item) {
        return "wechat-"
                + valueOrUnknown(String.valueOf(message.getMessage_id()))
                + "-voice-"
                + (voiceIndex + 1)
                + "."
                + formatForVoice(item);
    }

    private String formatForVoice(MessageItem item) {
        if (item == null || item.getVoice_item() == null || item.getVoice_item().getEncode_type() == null) {
            return "silk";
        }
        return switch (item.getVoice_item().getEncode_type()) {
            case 6 -> "silk";
            default -> "voice";
        };
    }

    private String mimeTypeForVoice(MessageItem item) {
        String format = formatForVoice(item);
        return switch (format) {
            case "silk" -> "audio/silk";
            default -> "application/octet-stream";
        };
    }

    private String mimeTypeForFile(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return "application/octet-stream";
        }
        String lower = fileName.toLowerCase(java.util.Locale.ROOT);
        if (lower.endsWith(".pdf")) {
            return "application/pdf";
        }
        if (lower.endsWith(".docx")) {
            return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
        }
        if (lower.endsWith(".xlsx")) {
            return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
        }
        if (lower.endsWith(".pptx")) {
            return "application/vnd.openxmlformats-officedocument.presentationml.presentation";
        }
        if (lower.endsWith(".md")) {
            return "text/markdown";
        }
        if (lower.endsWith(".txt")) {
            return "text/plain";
        }
        return "application/octet-stream";
    }

    private Long parseLong(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(value.strip());
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private String preview(String value) {
        if (value == null) {
            return "null";
        }
        String text = value.strip();
        if (text.length() <= 80) {
            return text;
        }
        return text.substring(0, 77) + "...";
    }

    private String rootMessage(Throwable exception) {
        Throwable current = exception;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }

    private String valueOrUnknown(String value) {
        return value == null || value.isBlank() ? "null" : value;
    }
}
