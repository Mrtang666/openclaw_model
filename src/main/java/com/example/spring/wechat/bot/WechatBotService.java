package com.example.spring.wechat.bot;

import com.example.spring.agent.AgentService;
import com.example.spring.wechat.image.generation.model.ImageGenerationResult;
import com.example.spring.wechat.adapter.WechatClient;
import com.example.spring.wechat.adapter.WechatClientFactory;
import com.example.spring.wechat.model.WechatIncomingMessage;
import com.example.spring.wechat.model.WechatLoginInfo;
import com.example.spring.wechat.conversation.WechatConversationService;
import com.example.spring.wechat.login.WechatLoginPageSession;
import com.example.spring.wechat.login.WechatLoginPageSessionService;
import com.example.spring.wechat.login.WechatLoginPageUrlService;
import com.example.spring.wechat.bot.concurrency.ConversationKey;
import com.example.spring.wechat.bot.concurrency.WechatConcurrencyProperties;
import com.example.spring.wechat.bot.concurrency.WechatMessageDispatcher;
import com.example.spring.wechat.bot.multiclient.ClawBotConnectionProperties;
import com.example.spring.wechat.bot.multiclient.ClawBotConnectionSnapshot;
import com.example.spring.wechat.bot.multiclient.ClawBotManagerSnapshot;
import com.example.spring.wechat.bot.multiclient.ClawBotProcessingState;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.time.Instant;
import java.util.Comparator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;

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
    private final WechatLoginPageSessionService loginPageSessionService;
    private final WechatLoginPageUrlService loginPageUrlService;
    private final ClawBotConnectionProperties connectionProperties;
    private final WechatConcurrencyProperties concurrencyProperties;
    private final ExecutorService receiverExecutor = Executors.newCachedThreadPool(task -> {
        Thread thread = new Thread(task, "wechat-receiver-" + UUID.randomUUID());
        thread.setDaemon(true);
        return thread;
    });
    private final Map<String, ClientRuntime> runtimes = new ConcurrentHashMap<>();
    private final Semaphore modelSlots;
    private volatile WechatMessageDispatcher messageDispatcher;
    private volatile String activeLoginPageUrl;

    public WechatBotService(
            WechatClientFactory clientFactory,
            ObjectProvider<WechatConversationService> conversationServiceProvider) {
        this(clientFactory,
                (sessionKey, message) -> conversationServiceProvider.getObject().handleWechat(message),
                null,
                null,
                new ClawBotConnectionProperties(),
                new WechatConcurrencyProperties());
    }

    public WechatBotService(
            WechatClientFactory clientFactory,
            ObjectProvider<WechatConversationService> conversationServiceProvider,
            WechatLoginPageSessionService loginPageSessionService,
            WechatLoginPageUrlService loginPageUrlService) {
        this(
                clientFactory,
                (sessionKey, message) -> conversationServiceProvider.getObject().handleWechat(message),
                loginPageSessionService,
                loginPageUrlService,
                new ClawBotConnectionProperties(),
                new WechatConcurrencyProperties());
    }

    @Autowired
    public WechatBotService(
            WechatClientFactory clientFactory,
            ObjectProvider<WechatConversationService> conversationServiceProvider,
            WechatLoginPageSessionService loginPageSessionService,
            WechatLoginPageUrlService loginPageUrlService,
            ClawBotConnectionProperties connectionProperties,
            WechatConcurrencyProperties concurrencyProperties) {
        this(
                clientFactory,
                (sessionKey, message) -> conversationServiceProvider.getObject().handleWechat(sessionKey, message),
                loginPageSessionService,
                loginPageUrlService,
                connectionProperties,
                concurrencyProperties);
    }

    WechatBotService(WechatClientFactory clientFactory, AgentService agentService) {
        this(clientFactory, (sessionKey, message) -> {
            StringBuilder reply = new StringBuilder();
            agentService.handleStreaming(message.text() == null ? "" : message.text(), reply::append);
            return WechatReply.text(reply.toString().strip());
        }, null, null, new ClawBotConnectionProperties(), new WechatConcurrencyProperties());
    }

    private WechatBotService(
            WechatClientFactory clientFactory,
            WechatMessageHandler messageHandler,
            WechatLoginPageSessionService loginPageSessionService,
            WechatLoginPageUrlService loginPageUrlService,
            ClawBotConnectionProperties connectionProperties,
            WechatConcurrencyProperties concurrencyProperties) {
        this.clientFactory = clientFactory;
        this.messageHandler = messageHandler;
        this.loginPageSessionService = loginPageSessionService;
        this.loginPageUrlService = loginPageUrlService;
        this.connectionProperties = connectionProperties;
        this.concurrencyProperties = concurrencyProperties;
        this.messageDispatcher = new WechatMessageDispatcher(concurrencyProperties);
        this.modelSlots = new Semaphore(concurrencyProperties.getModelMaxConcurrency(), true);
    }

    public synchronized WechatStartResult start() {
        cleanupExpiredPendingConnections();
        if (!runtimes.isEmpty()) {
            return new WechatStartResult(false, "微信 Bot 已在运行", activeLoginPageUrl);
        }
        try {
            ensureDispatcher();
            ClientRuntime runtime = createConnectionInternal();
            activeLoginPageUrl = pageUrl(runtime.loginSessionId);
            return new WechatStartResult(true, "请打开登录页面并使用微信扫码", activeLoginPageUrl);
        } catch (RuntimeException exception) {
            activeLoginPageUrl = null;
            return new WechatStartResult(false, "微信 Bot 启动失败：" + rootMessage(exception), null);
        }
    }

    public synchronized String stop() {
        if (runtimes.isEmpty()) {
            return "微信 Bot 未启动";
        }
        List.copyOf(runtimes.values()).forEach(this::stopRuntime);
        runtimes.clear();
        messageDispatcher.close();
        messageDispatcher = null;
        activeLoginPageUrl = null;
        return "微信 Bot 已停止";
    }

    public WechatBotStatus status() {
        ClawBotManagerSnapshot snapshot = managerSnapshot();
        WechatBotState state = aggregateState(snapshot.connections());
        String botId = snapshot.connections().stream()
                .filter(item -> item.botId() != null)
                .map(ClawBotConnectionSnapshot::botId)
                .findFirst().orElse(null);
        String lastError = snapshot.connections().stream()
                .filter(item -> item.lastError() != null)
                .map(ClawBotConnectionSnapshot::lastError)
                .findFirst().orElse(null);
        return new WechatBotStatus(state, botId, lastError,
                snapshot.connectedCount(), snapshot.pendingCount(), snapshot.totalConnections());
    }

    public synchronized ClawBotConnectionSnapshot addConnection() {
        cleanupExpiredPendingConnections();
        ensureDispatcher();
        return createConnectionInternal().snapshot();
    }

    public synchronized boolean stopConnection(String connectionId) {
        ClientRuntime runtime = runtimes.remove(connectionId);
        if (runtime == null) {
            return false;
        }
        stopRuntime(runtime);
        return true;
    }

    public synchronized ClawBotConnectionSnapshot reconnectConnection(String connectionId) {
        ClientRuntime runtime = runtimes.remove(connectionId);
        if (runtime == null) {
            throw new IllegalArgumentException("未找到微信连接：" + connectionId);
        }
        stopRuntime(runtime);
        cleanupExpiredPendingConnections();
        ensureDispatcher();
        return createConnectionInternal().snapshot();
    }

    public ClawBotManagerSnapshot managerSnapshot() {
        cleanupExpiredPendingConnections();
        List<ClawBotConnectionSnapshot> connections = runtimes.values().stream()
                .map(ClientRuntime::snapshot)
                .sorted(Comparator.comparing(ClawBotConnectionSnapshot::createdAt))
                .toList();
        int connected = (int) connections.stream().filter(item -> item.state() == WechatBotState.RUNNING).count();
        int pending = (int) connections.stream().filter(item -> item.state() == WechatBotState.WAITING_FOR_SCAN).count();
        WechatMessageDispatcher dispatcher = messageDispatcher;
        return new ClawBotManagerSnapshot(
                connected,
                pending,
                connections.size(),
                connectionProperties.getMaxConnections(),
                connectionProperties.getMaxPendingLogins(),
                concurrencyProperties.getWorkerThreads(),
                concurrencyProperties.getModelMaxConcurrency(),
                dispatcher == null ? 0 : dispatcher.activeTasks(),
                dispatcher == null ? 0 : dispatcher.queuedTasks(),
                connections);
    }

    @PreDestroy
    public void shutdown() {
        stop();
        receiverExecutor.shutdownNow();
    }

    private void runMessageLoop(ClientRuntime runtime) {
        try {
            WechatLoginInfo loginInfo = runtime.client.loginFuture().get();
            runtime.botId = loginInfo.botId();
            runtime.state = WechatBotState.RUNNING;
            runtime.lastActivityAt = Instant.now();
            log.info("微信 ClawBot 已登录，connectionId={}, botId={}", runtime.connectionId, runtime.botId);

            while (!runtime.stopRequested && runtimes.get(runtime.connectionId) == runtime) {
                runtime.processingState = ClawBotProcessingState.RECEIVING;
                List<WechatIncomingMessage> messages = runtime.client.getUpdates();
                runtime.processingState = ClawBotProcessingState.IDLE;
                for (WechatIncomingMessage message : messages) {
                    handleMessage(runtime, message);
                }
            }
        } catch (CancellationException exception) {
            if (!runtime.stopRequested) {
                moveToError(runtime, "消息循环被取消");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        } catch (Exception exception) {
            if (!runtime.stopRequested) {
                moveToError(runtime, rootMessage(exception));
            }
        }
    }

    private void handleMessage(ClientRuntime runtime, WechatIncomingMessage message) {
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
                sendText(runtime.client, message.fromUserId(), thinkingMessage);
            }
            ConversationKey key = new ConversationKey(runtime.connectionId, message.fromUserId());
            runtime.queuedMessages.incrementAndGet();
            messageDispatcher.submit(key, () -> {
                runtime.queuedMessages.decrementAndGet();
                processMessage(runtime, key, message);
            });
        } catch (RejectedExecutionException exception) {
            runtime.queuedMessages.updateAndGet(value -> Math.max(0, value - 1));
            runtime.lastError = rootMessage(exception);
            log.warn("微信消息入队失败，connectionId={}, fromUserId={}, error={}",
                    runtime.connectionId, message.fromUserId(), runtime.lastError);
        } catch (Exception exception) {
            runtime.lastError = rootMessage(exception);
            log.warn("微信消息接收处理失败，connectionId={}, fromUserId={}, error={}",
                    runtime.connectionId, message.fromUserId(), runtime.lastError);
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

    private void processMessage(ClientRuntime runtime, ConversationKey key, WechatIncomingMessage message) {
        if (runtime.stopRequested || runtimes.get(runtime.connectionId) != runtime) {
            return;
        }

        boolean acquired = false;
        try {
            modelSlots.acquire();
            acquired = true;
            runtime.activeMessages.incrementAndGet();
            runtime.processingState = ClawBotProcessingState.PROCESSING;
            runtime.lastActivityAt = Instant.now();
            log.debug(
                    "微信开始生成回复，connectionId={}, fromUserId={}, text={}, imageCount={}",
                    runtime.connectionId,
                    message.fromUserId(),
                    preview(message.text()),
                    message.images() == null ? 0 : message.images().size());
            WechatReply reply = messageHandler.handle(key.sessionKey(), message);
            String text = reply == null || reply.text() == null ? "" : reply.text().strip();
            if (reply != null && reply.parts() != null && !reply.parts().isEmpty()) {
                sendReplyParts(runtime.client, message.fromUserId(), reply.parts());
            } else if (reply != null && reply.hasImage()) {
                sendPreImageTexts(runtime.client, message.fromUserId(), reply.preImageTexts());
                sendImage(runtime.client, message.fromUserId(), reply.image(), text);
            } else {
                sendReplyChunks(runtime.client, message.fromUserId(), text);
            }
            log.info("微信回复发送完成，fromUserId={}, replyLength={}, hasImage={}",
                    message.fromUserId(),
                    text.length(),
                    reply != null && reply.hasImage());
        } catch (Exception exception) {
            runtime.lastError = rootMessage(exception);
            log.warn("微信消息处理失败，connectionId={}, fromUserId={}, error={}",
                    runtime.connectionId, message.fromUserId(), runtime.lastError);
            sendUserFacingError(runtime.client, message.fromUserId(), runtime.lastError);
        } finally {
            if (acquired) {
                runtime.activeMessages.decrementAndGet();
                modelSlots.release();
            }
            runtime.processingState = runtime.activeMessages.get() > 0
                    ? ClawBotProcessingState.PROCESSING
                    : ClawBotProcessingState.IDLE;
            runtime.lastActivityAt = Instant.now();
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

    private synchronized void moveToError(ClientRuntime runtime, String message) {
        if (runtimes.get(runtime.connectionId) != runtime) {
            return;
        }
        runtime.lastError = message;
        runtime.state = WechatBotState.ERROR;
        runtime.processingState = ClawBotProcessingState.ERROR;
        runtime.lastActivityAt = Instant.now();
        closeClient(runtime.client);
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

    private synchronized ClientRuntime createConnectionInternal() {
        if (runtimes.size() >= connectionProperties.getMaxConnections()) {
            throw new IllegalStateException("已达到最大连接数 " + connectionProperties.getMaxConnections());
        }
        long pending = runtimes.values().stream()
                .filter(runtime -> runtime.state == WechatBotState.WAITING_FOR_SCAN)
                .count();
        if (pending >= connectionProperties.getMaxPendingLogins()) {
            throw new IllegalStateException("待扫码登录数已达到上限 " + connectionProperties.getMaxPendingLogins());
        }

        String connectionId = UUID.randomUUID().toString();
        WechatClient newClient = clientFactory.create();
        try {
            String loginUrl = newClient.executeLogin();
            WechatLoginPageSession session = loginPageSessionService == null
                    ? null
                    : loginPageSessionService.create(loginUrl, newClient::loginState);
            ClientRuntime runtime = new ClientRuntime(
                    connectionId,
                    "用户 " + (runtimes.size() + 1),
                    newClient,
                    session == null ? null : session.id());
            runtimes.put(connectionId, runtime);
            runtime.worker = receiverExecutor.submit(() -> runMessageLoop(runtime));
            if (session == null) {
                activeLoginPageUrl = loginUrl;
            } else if (activeLoginPageUrl == null || activeLoginPageUrl.isBlank()) {
                activeLoginPageUrl = pageUrl(session.id());
            }
            return runtime;
        } catch (RuntimeException exception) {
            closeClient(newClient);
            throw exception;
        }
    }

    private String pageUrl(String loginSessionId) {
        if (loginSessionId == null || loginPageUrlService == null) {
            return activeLoginPageUrl;
        }
        return loginPageUrlService.pageUrl(loginSessionId);
    }

    private synchronized void cleanupExpiredPendingConnections() {
        if (loginPageSessionService == null) {
            return;
        }
        List<ClientRuntime> expired = runtimes.values().stream()
                .filter(runtime -> runtime.state == WechatBotState.WAITING_FOR_SCAN)
                .filter(runtime -> {
                    WechatLoginPageSession session = loginPageSessionService.find(runtime.loginSessionId);
                    return session == null || loginPageSessionService.status(session)
                            == com.example.spring.wechat.model.WechatLoginState.EXPIRED;
                })
                .toList();
        expired.forEach(runtime -> {
            runtimes.remove(runtime.connectionId, runtime);
            stopRuntime(runtime);
        });
    }

    private void stopRuntime(ClientRuntime runtime) {
        runtime.stopRequested = true;
        runtime.state = WechatBotState.STOPPED;
        runtime.processingState = ClawBotProcessingState.IDLE;
        if (runtime.worker != null) {
            runtime.worker.cancel(true);
        }
        closeClient(runtime.client);
    }

    private void ensureDispatcher() {
        if (messageDispatcher == null) {
            messageDispatcher = new WechatMessageDispatcher(concurrencyProperties);
        }
    }

    private WechatBotState aggregateState(List<ClawBotConnectionSnapshot> connections) {
        if (connections.isEmpty()) {
            return WechatBotState.STOPPED;
        }
        if (connections.stream().anyMatch(item -> item.state() == WechatBotState.RUNNING)) {
            return WechatBotState.RUNNING;
        }
        if (connections.stream().anyMatch(item -> item.state() == WechatBotState.WAITING_FOR_SCAN)) {
            return WechatBotState.WAITING_FOR_SCAN;
        }
        return WechatBotState.ERROR;
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

        WechatReply handle(String sessionKey, WechatIncomingMessage message);
    }

    private static final class ClientRuntime {
        private final String connectionId;
        private final String displayName;
        private final WechatClient client;
        private final String loginSessionId;
        private final Instant createdAt = Instant.now();
        private final AtomicInteger queuedMessages = new AtomicInteger();
        private final AtomicInteger activeMessages = new AtomicInteger();
        private volatile WechatBotState state = WechatBotState.WAITING_FOR_SCAN;
        private volatile ClawBotProcessingState processingState = ClawBotProcessingState.IDLE;
        private volatile Instant lastActivityAt = createdAt;
        private volatile String botId;
        private volatile String lastError;
        private volatile boolean stopRequested;
        private volatile Future<?> worker;

        private ClientRuntime(String connectionId, String displayName, WechatClient client, String loginSessionId) {
            this.connectionId = connectionId;
            this.displayName = displayName;
            this.client = client;
            this.loginSessionId = loginSessionId;
        }

        private ClawBotConnectionSnapshot snapshot() {
            return new ClawBotConnectionSnapshot(
                    connectionId,
                    displayName,
                    state,
                    processingState,
                    botId,
                    loginSessionId,
                    createdAt,
                    lastActivityAt,
                    queuedMessages.get(),
                    activeMessages.get(),
                    lastError);
        }
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

