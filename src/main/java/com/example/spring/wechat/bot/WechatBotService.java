package com.example.spring.wechat.bot;

import com.example.spring.agent.AgentService;
import com.example.spring.agent.ReplyEmitter;
import com.example.spring.wechat.image.generation.model.ImageGenerationResult;
import com.example.spring.wechat.adapter.WechatClient;
import com.example.spring.wechat.adapter.WechatClientFactory;
import com.example.spring.wechat.model.WechatIncomingMessage;
import com.example.spring.wechat.model.WechatLoginInfo;
import com.example.spring.wechat.conversation.WechatConversationService;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;

/**
 * 微信 Bot 的生命周期与消息收发入口。
 * 负责启动 iLink 客户端、监听登录状态、接收用户消息、排队交给会话服务处理，
 * 并把文本、图片和语音等回复内容发送回微信用户。
 */
@Service
public class WechatBotService {

    private static final Logger log = LoggerFactory.getLogger(WechatBotService.class);
    private static final String THINKING_MESSAGE = "[自动回复]正在生成中，请耐心等待哦~";
    private static final int MAX_WECHAT_MESSAGE_LENGTH = 800;
    private static final int MEDIA_SEND_MAX_ATTEMPTS = 3;
    private static final long MEDIA_SEND_RETRY_DELAY_MS = 600;
    private static final long TEXT_CHUNK_PAUSE_MS = 50;
    private static final long VOICE_PART_PAUSE_MS = 1_000;

    private final WechatClientFactory clientFactory;
    private final WechatMessageHandler messageHandler;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "wechat-bot-worker");
        thread.setDaemon(true);
        return thread;
    });
    private volatile ExecutorService messageExecutor = newMessageExecutor();

    private volatile WechatBotState state = WechatBotState.STOPPED;
    private volatile WechatClient client;
    private volatile Future<?> worker;
    private volatile boolean stopRequested;
    private volatile String botId;
    private volatile String lastError;

    @Autowired
    public WechatBotService(
            WechatClientFactory clientFactory,
            ObjectProvider<WechatConversationService> conversationServiceProvider) {
        this(clientFactory, message -> conversationServiceProvider.getObject().handleWechat(message));
    }

    WechatBotService(WechatClientFactory clientFactory, AgentService agentService) {
        this(clientFactory, message -> {
            StringBuilder reply = new StringBuilder();
            agentService.handleStreaming(message.text() == null ? "" : message.text(), reply::append);
            return WechatReply.text(reply.toString().strip());
        });
    }

    private WechatBotService(
            WechatClientFactory clientFactory,
            WechatMessageHandler messageHandler) {
        this.clientFactory = clientFactory;
        this.messageHandler = messageHandler;
    }

    public synchronized WechatStartResult start() {
        if (state == WechatBotState.WAITING_FOR_SCAN || state == WechatBotState.RUNNING) {
            return new WechatStartResult(false, "微信 Bot 已在运行", null);
        }

        if (messageExecutor == null || messageExecutor.isShutdown() || messageExecutor.isTerminated()) {
            messageExecutor = newMessageExecutor();
        }
        stopRequested = false;
        lastError = null;
        botId = null;
        client = clientFactory.create();

        try {
            String qrCodeContent = client.executeLogin();
            state = WechatBotState.WAITING_FOR_SCAN;
            worker = executor.submit(() -> runMessageLoop(client));
            return new WechatStartResult(true, "请使用微信扫码登录", qrCodeContent);
        } catch (RuntimeException exception) {
            lastError = rootMessage(exception);
            state = WechatBotState.ERROR;
            closeClient(client);
            return new WechatStartResult(false, "微信 Bot 启动失败：" + lastError, null);
        }
    }

    public synchronized String stop() {
        if (state == WechatBotState.STOPPED) {
            return "微信 Bot 未启动";
        }

        stopRequested = true;
        if (worker != null) {
            worker.cancel(true);
            worker = null;
        }
        if (messageExecutor != null) {
            messageExecutor.shutdownNow();
            messageExecutor = null;
        }
        closeClient(client);
        client = null;
        botId = null;
        lastError = null;
        state = WechatBotState.STOPPED;
        return "微信 Bot 已停止";
    }

    public WechatBotStatus status() {
        return new WechatBotStatus(state, botId, lastError);
    }

    @PreDestroy
    public void shutdown() {
        stop();
        executor.shutdownNow();
    }

    private void runMessageLoop(WechatClient activeClient) {
        try {
            WechatLoginInfo loginInfo = activeClient.loginFuture().get();
            botId = loginInfo.botId();
            state = WechatBotState.RUNNING;
            log.info("微信 Bot 已登录，botId={}", botId);

            while (!stopRequested && client == activeClient) {
                List<WechatIncomingMessage> messages = activeClient.getUpdates();
                for (WechatIncomingMessage message : messages) {
                    handleMessage(activeClient, message);
                }
            }
        } catch (CancellationException exception) {
            if (!stopRequested) {
                moveToError(activeClient, "消息循环被取消");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        } catch (Exception exception) {
            if (!stopRequested) {
                moveToError(activeClient, rootMessage(exception));
            }
        }
    }

    private void handleMessage(WechatClient activeClient, WechatIncomingMessage message) {
        if (message == null) {
            return;
        }

        boolean hasText = message.text() != null && !message.text().isBlank();
        boolean hasImages = message.images() != null && !message.images().isEmpty();
        boolean hasVoices = message.voices() != null && !message.voices().isEmpty();
        boolean hasFiles = message.files() != null && !message.files().isEmpty();
        if (!hasText && !hasImages && !hasVoices && !hasFiles) {
            return;
        }

        String text = hasText ? message.text().strip() : "";
        log.info(
                "微信收到消息，fromUserId={}, text={}, imageCount={}, voiceCount={}, fileCount={}",
                message.fromUserId(),
                preview(text),
                hasImages ? message.images().size() : 0,
                hasVoices ? message.voices().size() : 0,
                hasFiles ? message.files().size() : 0);
        try {
            String thinkingMessage = thinkingMessage(text, hasImages, hasVoices, hasFiles);
            if (thinkingMessage != null) {
                sendText(activeClient, message.fromUserId(), thinkingMessage);
            }
            ExecutorService currentExecutor = messageExecutor;
            if (currentExecutor == null || currentExecutor.isShutdown()) {
                log.warn("微信消息处理队列不可用，fromUserId={}", message.fromUserId());
                return;
            }
            log.debug("微信消息进入处理队列，fromUserId={}", message.fromUserId());
            currentExecutor.submit(() -> processMessage(activeClient, message));
        } catch (RejectedExecutionException exception) {
            lastError = rootMessage(exception);
            log.warn("微信消息入队失败，fromUserId={}, error={}", message.fromUserId(), lastError);
        } catch (Exception exception) {
            lastError = rootMessage(exception);
            log.warn("微信消息接收处理失败，fromUserId={}, error={}", message.fromUserId(), lastError);
        }
    }

    private String thinkingMessage(String text, boolean hasImages, boolean hasVoices, boolean hasFiles) {
        if (hasImages || hasVoices || hasFiles) {
            return THINKING_MESSAGE;
        }
        if (text == null || text.isBlank() || text.startsWith("/")) {
            return null;
        }
        return THINKING_MESSAGE;
    }

    private void processMessage(WechatClient activeClient, WechatIncomingMessage message) {
        if (stopRequested || client != activeClient) {
            return;
        }

        try {
            log.debug(
                    "微信开始生成回复，fromUserId={}, text={}, imageCount={}",
                    message.fromUserId(),
                    preview(message.text()),
                    message.images() == null ? 0 : message.images().size());
            WechatReply reply = messageHandler.handle(message);
            String text = reply == null || reply.text() == null ? "" : reply.text().strip();
            if (reply != null && reply.parts() != null && !reply.parts().isEmpty()) {
                sendReplyParts(activeClient, message.fromUserId(), reply.parts());
            } else if (reply != null && reply.hasImage()) {
                sendPreImageTexts(activeClient, message.fromUserId(), reply.preImageTexts());
                sendImage(activeClient, message.fromUserId(), reply.image(), text);
            } else {
                sendReplyChunks(activeClient, message.fromUserId(), text);
            }
            log.info("微信回复发送完成，fromUserId={}, replyLength={}, hasImage={}",
                    message.fromUserId(),
                    text.length(),
                    reply != null && reply.hasImage());
        } catch (Exception exception) {
            lastError = rootMessage(exception);
            log.warn("微信消息处理失败，fromUserId={}, error={}", message.fromUserId(), lastError);
            sendUserFacingError(activeClient, message.fromUserId(), lastError);
        }
    }

    private void sendReplyParts(WechatClient activeClient, String userId, List<WechatReply.Part> parts) {
        for (int index = 0; index < parts.size(); index++) {
            WechatReply.Part part = parts.get(index);
            if (part == null) {
                continue;
            }

            if (part.hasImage()) {
                sendImage(activeClient, userId, part.image(), part.text());
            } else if (part.hasFile()) {
                sendDocumentFile(activeClient, userId, part.file());
            } else {
                sendReplyChunks(activeClient, userId, part.text());
            }

            if (part.hasVoice()) {
                sendVoice(activeClient, userId, part.voice());
            }

            if (index < parts.size() - 1) {
                pauseAfterReplyPart(part);
            }
        }
    }

    private void sendPreImageTexts(WechatClient activeClient, String userId, List<String> preImageTexts) {
        if (preImageTexts == null || preImageTexts.isEmpty()) {
            return;
        }

        for (String preImageText : preImageTexts) {
            sendReplyChunks(activeClient, userId, preImageText);
            pauseBetweenChunks();
        }
    }

    private void sendReplyChunks(WechatClient activeClient, String userId, String reply) {
        List<String> chunks = splitForWechat(reply);
        for (int index = 0; index < chunks.size(); index++) {
            sendText(activeClient, userId, chunks.get(index));
            if (index < chunks.size() - 1) {
                pauseBetweenChunks();
            }
        }
    }

    static List<String> splitForWechat(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }

        if (text.length() <= MAX_WECHAT_MESSAGE_LENGTH) {
            return List.of(text);
        }

        List<String> chunks = new ArrayList<>();
        for (int start = 0; start < text.length(); start += MAX_WECHAT_MESSAGE_LENGTH) {
            int end = Math.min(text.length(), start + MAX_WECHAT_MESSAGE_LENGTH);
            chunks.add(text.substring(start, end));
        }
        return chunks;
    }

    private void sendText(WechatClient activeClient, String userId, String chunk) {
        if (chunk == null || chunk.isBlank()) {
            return;
        }

        try {
            activeClient.sendText(userId, chunk);
        } catch (IOException exception) {
            throw new WechatSendException(exception);
        }
    }

    private void sendImage(WechatClient activeClient, String userId, ImageGenerationResult image, String caption) {
        if (image == null || image.imageBytes() == null || image.imageBytes().length == 0) {
            if (image != null && image.imageUrl() != null && !image.imageUrl().isBlank()) {
                sendText(activeClient, userId, "图片已生成：" + image.imageUrl());
            }
            return;
        }

        String safeCaption = caption == null || caption.isBlank() ? "图片已生成" : caption;
        try {
            sendMediaWithRetry(
                    "图片",
                    userId,
                    image.fileName(),
                    () -> activeClient.sendImage(userId, image.imageBytes(), image.fileName(), safeCaption));
        } catch (IOException | RuntimeException exception) {
            log.warn("微信图片上传失败，已改为发送图片链接，userId={}, fileName={}, error={}",
                    userId,
                    image.fileName(),
                    rootMessage(exception));
            if (image.imageUrl() != null && !image.imageUrl().isBlank()) {
                sendText(activeClient, userId, "图片已生成，但微信图片上传失败，请查看链接：" + image.imageUrl());
            } else {
                sendText(activeClient, userId, "图片已生成，但微信图片上传失败，且图片平台没有返回可访问链接。");
            }
        }
    }

    private void sendVoice(WechatClient activeClient, String userId, WechatReply.Voice voice) {
        if (voice == null || voice.audioBytes() == null || voice.audioBytes().length == 0) {
            return;
        }

        if (!isWechatNativeVoiceFile(voice.fileName())) {
            sendVoiceAsFile(activeClient, userId, voice);
            return;
        }

        try {
            sendMediaWithRetry(
                    "语音气泡",
                    userId,
                    voice.fileName(),
                    () -> activeClient.sendVoice(
                            userId,
                            voice.audioBytes(),
                            voice.fileName(),
                            voice.durationMs(),
                            voice.sampleRate(),
                            voice.encodeType(),
                            voice.bitsPerSample(),
                            voice.transcriptText()));
        } catch (IOException | UnsupportedOperationException exception) {
            sendVoiceAsFile(activeClient, userId, voice);
        }
    }

    private boolean isWechatNativeVoiceFile(String fileName) {
        return fileName != null && fileName.toLowerCase(java.util.Locale.ROOT).endsWith(".silk");
    }

    private void sendVoiceAsFile(WechatClient activeClient, String userId, WechatReply.Voice voice) {
        try {
            sendMediaWithRetry(
                    "语音文件",
                    userId,
                    voice.fileName(),
                    () -> activeClient.sendFile(userId, voice.audioBytes(), voice.fileName(), "语音已生成，请点击文件播放"));
        } catch (IOException | UnsupportedOperationException exception) {
            if (voice.transcriptText() != null && !voice.transcriptText().isBlank()) {
                sendText(activeClient, userId, "语音文件发送失败，文字内容如下：\n" + voice.transcriptText());
            } else {
                throw new WechatSendException(exception);
            }
        }
    }

    private void sendDocumentFile(WechatClient activeClient, String userId, WechatReply.FileAttachment file) {
        if (file == null || file.fileBytes() == null || file.fileBytes().length == 0) {
            return;
        }

        try {
            sendMediaWithRetry(
                    "文档文件",
                    userId,
                    file.fileName(),
                    () -> activeClient.sendFile(userId, file.fileBytes(), file.fileName(), file.caption()));
        } catch (IOException | UnsupportedOperationException exception) {
            throw new WechatSendException(exception);
        }
    }

    private void sendMediaWithRetry(String mediaType, String userId, String fileName, IoSendOperation operation) throws IOException {
        Exception lastException = null;
        for (int attempt = 1; attempt <= MEDIA_SEND_MAX_ATTEMPTS; attempt++) {
            try {
                operation.send();
                return;
            } catch (IOException | RuntimeException exception) {
                lastException = exception;
                log.warn(
                        "微信{}发送失败，准备重试，userId={}, fileName={}, attempt={}/{}, error={}",
                        mediaType,
                        userId,
                        fileName,
                        attempt,
                        MEDIA_SEND_MAX_ATTEMPTS,
                        rootMessage(exception));
                if (attempt < MEDIA_SEND_MAX_ATTEMPTS) {
                    pauseBeforeMediaRetry();
                }
            }
        }

        if (lastException instanceof IOException ioException) {
            throw ioException;
        }
        if (lastException instanceof RuntimeException runtimeException) {
            throw runtimeException;
        }
        throw new IOException(mediaType + "发送失败");
    }

    private void pauseAfterReplyPart(WechatReply.Part part) {
        if (part != null && part.hasVoice()) {
            pause(VOICE_PART_PAUSE_MS);
            return;
        }
        pauseBetweenChunks();
    }

    private void sendUserFacingError(WechatClient activeClient, String userId, String errorMessage) {
        String message = "抱歉，刚刚处理消息时出了点问题：" + errorMessage;
        try {
            sendText(activeClient, userId, message);
        } catch (Exception ignored) {
            // If even the fallback message fails, keep the internal error state only.
        }
    }

    private void pauseBetweenChunks() {
        pause(TEXT_CHUNK_PAUSE_MS);
    }

    private void pauseBeforeMediaRetry() {
        pause(MEDIA_SEND_RETRY_DELAY_MS);
    }

    private void pause(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    private synchronized void moveToError(WechatClient activeClient, String message) {
        if (client != activeClient) {
            return;
        }
        lastError = message;
        state = WechatBotState.ERROR;
        closeClient(activeClient);
        client = null;
        botId = null;
    }

    private void closeClient(WechatClient wechatClient) {
        if (wechatClient != null) {
            wechatClient.close();
        }
    }

    private String rootMessage(Throwable exception) {
        Throwable current = exception;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }

    private ExecutorService newMessageExecutor() {
        return Executors.newSingleThreadExecutor(task -> {
            Thread thread = new Thread(task, "wechat-message-worker");
            thread.setDaemon(true);
            return thread;
        });
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

    @FunctionalInterface
    private interface WechatMessageHandler {

        WechatReply handle(WechatIncomingMessage message);
    }

    private static final class WechatSendException extends RuntimeException {

        private WechatSendException(Throwable cause) {
            super(cause);
        }
    }

    @FunctionalInterface
    private interface IoSendOperation {

        void send() throws IOException;
    }
}

