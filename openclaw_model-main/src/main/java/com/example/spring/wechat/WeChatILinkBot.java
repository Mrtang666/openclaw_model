package com.example.spring.wechat;

import com.example.spring.agent.AgentCoordinator;
import com.example.spring.agent.AgentPlan;
import com.example.spring.agent.AgentRequest;
import com.example.spring.agent.AgentResponse;
import com.example.spring.agent.AgentRouter;
import com.example.spring.agent.ImageAsset;
import com.example.spring.bailian.BailianProperties;
import com.example.spring.memory.ConversationMemoryService;
import com.github.wechat.ilink.sdk.ILinkClient;
import com.github.wechat.ilink.sdk.ILinkClientBuilder;
import com.github.wechat.ilink.sdk.core.config.ILinkConfig;
import com.github.wechat.ilink.sdk.core.context.ResumeContext;
import com.github.wechat.ilink.sdk.core.exception.SessionExpiredException;
import com.github.wechat.ilink.sdk.core.login.LoginContext;
import com.github.wechat.ilink.sdk.core.model.MessageItem;
import com.github.wechat.ilink.sdk.core.model.WeixinMessage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

@Component
public class WeChatILinkBot implements SmartLifecycle {
    private static final Logger log = LoggerFactory.getLogger(WeChatILinkBot.class);
    private static final int MAX_REMEMBERED_MESSAGES = 10_000;

    private final WeChatBotProperties properties;
    private final BailianProperties bailianProperties;
    private final AgentRouter agentRouter;
    private final AgentCoordinator agentCoordinator;
    private final ConversationMemoryService memoryService;
    private final BotInstanceLock instanceLock;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicBoolean everLoggedIn = new AtomicBoolean(false);
    private final Object clientMonitor = new Object();
    private final Object sendMonitor = new Object();
    private final Set<Long> processedMessageIds = ConcurrentHashMap.newKeySet();

    private volatile Thread botThread;
    private volatile ILinkClient client;
    private volatile ResumeContext resumeContext;

    public WeChatILinkBot(
        WeChatBotProperties properties,
        BailianProperties bailianProperties,
        AgentRouter agentRouter,
        AgentCoordinator agentCoordinator,
        ConversationMemoryService memoryService,
        BotInstanceLock instanceLock) {
        this.properties = properties;
        this.bailianProperties = bailianProperties;
        this.agentRouter = agentRouter;
        this.agentCoordinator = agentCoordinator;
        this.memoryService = memoryService;
        this.instanceLock = instanceLock;
    }

    @Override
    public void start() {
        if (!properties.isEnabled()) {
            log.info("微信 iLink 机器人已禁用");
            return;
        }
        if (!bailianProperties.isConfigured()) {
            log.warn("BAILIAN_API_KEY 未配置，对话、图片识别和图片生成暂不可用，天气模块仍可独立使用");
        }
        if (!running.compareAndSet(false, true)) {
            return;
        }
        if (!instanceLock.tryAcquire()) {
            running.set(false);
            log.error(
                "检测到另一个微信机器人进程正在运行。请先停止旧的 DemoApplication 或 JAR 进程，避免 iLink 会话互相挤掉");
            return;
        }

        Thread thread = new Thread(this::runBot, "wechat-ilink-bot");
        thread.setDaemon(false);
        botThread = thread;
        thread.start();
    }

    private void runBot() {
        while (running.get() && !Thread.currentThread().isInterrupted()) {
            try {
                ResumeContext currentResumeContext = resumeContext;
                ILinkClient activeClient = createClient(currentResumeContext);
                if (currentResumeContext == null) {
                    String loginUrl = activeClient.executeLogin();
                    openLoginUrl(loginUrl, !everLoggedIn.get());

                    LoginContext loginContext = activeClient.getLoginFuture().get();
                    everLoggedIn.set(true);
                    resumeContext = activeClient.exportResumeContext();
                    log.info("微信登录成功，botId={}", loginContext.getBotId());
                    log.info("微信多 Agent 机器人已启动");
                } else {
                    log.info("正在使用已有微信会话恢复连接，不需要重新扫码");
                }

                while (running.get() && !Thread.currentThread().isInterrupted()) {
                    pollAndReply(activeClient);
                    resumeContext = activeClient.exportResumeContext();
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception exception) {
                if (running.get()) {
                    if (containsSessionExpired(exception)) {
                        resumeContext = null;
                        if (everLoggedIn.get()) {
                            log.warn(
                                "微信会话凭证已失效，将生成新的登录链接，但登录成功后不会再自动弹出浏览器",
                                exception);
                        } else {
                            log.warn("微信登录会话已失效，准备重新生成登录链接", exception);
                        }
                    } else {
                        log.warn("微信连接异常，{} 后尝试恢复已有会话", properties.getRetryDelay(), exception);
                    }
                }
            } finally {
                closeClient();
            }

            if (!sleepBeforeRetry()) {
                break;
            }
        }
        running.set(false);
        closeClient();
        instanceLock.close();
    }

    private ILinkClient createClient(ResumeContext currentResumeContext) {
        ILinkClientBuilder builder = ILinkClient.builder().config(createILinkConfig());
        if (currentResumeContext != null) {
            builder.resumeContext(currentResumeContext);
        }
        ILinkClient newClient = builder.build();
        synchronized (clientMonitor) {
            client = newClient;
        }
        return newClient;
    }

    static ILinkConfig createILinkConfig() {
        return ILinkConfig.builder()
            .heartbeatEnabled(true)
            .heartbeatIntervalMs(30_000)
            .autoReconnectEnabled(true)
            .reconnectMaxAttempts(5)
            .reconnectBaseDelayMs(1_000)
            .reconnectMaxDelayMs(30_000)
            .build();
    }

    private void pollAndReply(ILinkClient activeClient) throws InterruptedException {
        try {
            replyToMessages(activeClient, activeClient.getUpdates());
        } catch (IOException exception) {
            if (running.get()) {
                log.warn("拉取微信消息失败，{} 后重试", properties.getRetryDelay(), exception);
                Thread.sleep(properties.getRetryDelay().toMillis());
            }
        }
    }

    void replyToMessages(ILinkClient activeClient, List<WeixinMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return;
        }
        for (WeixinMessage message : messages) {
            String userId = message == null ? null : message.getFrom_user_id();
            if (userId == null || userId.isBlank() || isDuplicate(message.getMessage_id())) {
                continue;
            }

            MessageSummary summary = summarize(message);
            log.info(
                "收到微信消息，userId={}，messageId={}，textLength={}，imageCount={}，unsupportedTypes={}",
                userId,
                message.getMessage_id(),
                summary.text().length(),
                summary.imageItems().size(),
                summary.unsupportedTypes());
            if (!summary.hasProcessableContent()) {
                sendTextSafely(activeClient, userId, unsupportedReply(summary), message.getMessage_id());
                continue;
            }

            AgentRequest previewRequest = memoryService.prepare(new AgentRequest(
                userId,
                message.getMessage_id(),
                summary.text(),
                List.of(),
                summary.imageItems().size()));
            AgentPlan plan = agentRouter.route(previewRequest);
            log.info(
                "微信消息已路由，userId={}，messageId={}，steps={}",
                userId,
                message.getMessage_id(),
                plan.steps());
            sendTextSafely(
                activeClient,
                userId,
                agentRouter.processingMessage(plan),
                message.getMessage_id());

            processMessage(activeClient, message, summary, previewRequest, plan);
        }
    }

    private void processMessage(
        ILinkClient activeClient,
        WeixinMessage message,
        MessageSummary summary,
        AgentRequest previewRequest,
        AgentPlan plan) {
        String userId = previewRequest.userId();
        try {
            List<ImageAsset> images = downloadImages(activeClient, summary.imageItems());
            AgentRequest request = memoryService.prepare(new AgentRequest(
                userId,
                message.getMessage_id(),
                summary.text(),
                images,
                summary.imageItems().size()));
            AgentResponse response = agentCoordinator.execute(plan, request);
            log.info(
                "Agent 处理完成，userId={}，messageId={}，textReplyLength={}，imageReplyCount={}",
                userId,
                message.getMessage_id(),
                response.text().length(),
                response.images().size());
            sendResponse(activeClient, userId, response, message.getMessage_id());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        } catch (Exception exception) {
            log.error("消息处理失败，userId={}，messageId={}",
                userId, message.getMessage_id(), exception);
            sendTextSafely(
                activeClient,
                userId,
                properties.getModelErrorReply(),
                message.getMessage_id());
        }
    }

    private List<ImageAsset> downloadImages(
        ILinkClient activeClient,
        List<MessageItem> imageItems) throws IOException {
        List<ImageAsset> images = new ArrayList<>();
        int index = 1;
        for (MessageItem item : imageItems) {
            byte[] data = activeClient.downloadImageFromMessageItem(item);
            String mediaType = detectImageMediaType(data);
            String extension = switch (mediaType) {
                case "image/png" -> ".png";
                case "image/gif" -> ".gif";
                case "image/webp" -> ".webp";
                default -> ".jpg";
            };
            images.add(new ImageAsset(data, mediaType, "wechat-image-" + index + extension));
            index++;
        }
        return images;
    }

    private void sendResponse(
        ILinkClient activeClient,
        String userId,
        AgentResponse response,
        Long messageId) {
        if (!response.text().isBlank()) {
            sendTextSafely(activeClient, userId, response.text(), messageId);
        }
        for (ImageAsset image : response.images()) {
            try {
                synchronized (sendMonitor) {
                    activeClient.sendImage(
                        userId, image.data(), image.fileName(), "");
                }
                log.info("已向用户发送图片，userId={}，messageId={}", userId, messageId);
            } catch (IOException | RuntimeException exception) {
                log.error("微信图片发送失败，userId={}，messageId={}",
                    userId, messageId, exception);
                sendTextSafely(activeClient, userId, "图片已经生成，但发送失败，请稍后重试。", messageId);
            }
        }
    }

    private void sendTextSafely(
        ILinkClient activeClient,
        String userId,
        String text,
        Long messageId) {
        try {
            synchronized (sendMonitor) {
                activeClient.sendText(userId, text);
            }
            log.info("已回复用户，userId={}，messageId={}", userId, messageId);
        } catch (IOException | RuntimeException exception) {
            log.error("微信文字消息发送失败，userId={}，messageId={}",
                userId, messageId, exception);
        }
    }

    static MessageSummary summarize(WeixinMessage message) {
        if (message == null || message.getItem_list() == null) {
            return new MessageSummary("", List.of(), List.of());
        }
        StringBuilder text = new StringBuilder();
        List<MessageItem> images = new ArrayList<>();
        List<String> unsupported = new ArrayList<>();
        for (MessageItem item : message.getItem_list()) {
            if (item == null) {
                continue;
            }
            appendText(text, item);
            if (item.getImage_item() != null) {
                images.add(item);
            }
            if (item.getFile_item() != null) {
                unsupported.add("文件");
            }
            if (item.getVideo_item() != null) {
                unsupported.add("视频");
            }
            if (item.getVoice_item() != null
                && (item.getVoice_item().getText() == null
                    || item.getVoice_item().getText().isBlank())) {
                unsupported.add("未转写的语音");
            }
        }
        return new MessageSummary(text.toString(), images, unsupported);
    }

    static String extractText(WeixinMessage message) {
        return summarize(message).text();
    }

    static boolean containsText(WeixinMessage message) {
        return !extractText(message).isBlank();
    }

    private static void appendText(StringBuilder text, MessageItem item) {
        String value = null;
        if (item.getText_item() != null) {
            value = item.getText_item().getText();
        } else if (item.getVoice_item() != null) {
            value = item.getVoice_item().getText();
        }
        if (value != null && !value.isBlank()) {
            if (!text.isEmpty()) {
                text.append('\n');
            }
            text.append(value.trim());
        }
    }

    private boolean isDuplicate(Long messageId) {
        if (messageId == null) {
            return false;
        }
        if (processedMessageIds.size() >= MAX_REMEMBERED_MESSAGES) {
            processedMessageIds.clear();
        }
        return !processedMessageIds.add(messageId);
    }

    private static String unsupportedReply(MessageSummary summary) {
        if (!summary.unsupportedTypes().isEmpty()) {
            return "已收到" + String.join("、", summary.unsupportedTypes())
                + "消息。当前版本支持文字、带转写文字的语音、图片和图片 URL；该内容暂时无法进一步识别。";
        }
        return "已收到消息，但没有识别到可处理的文字或图片内容。";
    }

    private static String detectImageMediaType(byte[] data) {
        if (data != null && data.length >= 8
            && data[0] == (byte) 0x89 && data[1] == 0x50
            && data[2] == 0x4E && data[3] == 0x47) {
            return "image/png";
        }
        if (data != null && data.length >= 6
            && data[0] == 'G' && data[1] == 'I' && data[2] == 'F') {
            return "image/gif";
        }
        if (data != null && data.length >= 12
            && data[0] == 'R' && data[1] == 'I' && data[2] == 'F' && data[3] == 'F'
            && data[8] == 'W' && data[9] == 'E' && data[10] == 'B' && data[11] == 'P') {
            return "image/webp";
        }
        return "image/jpeg";
    }

    private void openLoginUrl(String loginUrl, boolean openBrowser) {
        log.info("微信 iLink 扫码登录入口：{}", loginUrl);
        if (!openBrowser) {
            log.warn("已登录过微信，本次不再自动打开浏览器；需要重新登录时请手动打开上面的链接");
            return;
        }
        try {
            BrowserLauncher.open(loginUrl);
            log.info("已调用系统默认浏览器打开微信登录入口");
        } catch (IOException | RuntimeException exception) {
            log.warn("浏览器自动打开失败，请复制上面的登录入口到浏览器", exception);
        }
    }

    private static boolean containsSessionExpired(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof SessionExpiredException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private boolean sleepBeforeRetry() {
        if (!running.get() || Thread.currentThread().isInterrupted()) {
            return false;
        }
        try {
            Thread.sleep(properties.getRetryDelay().toMillis());
            return true;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    @Override
    public void stop() {
        running.set(false);
        ILinkClient currentClient = client;
        if (currentClient != null) {
            currentClient.cancelLogin();
        }
        closeClient();
        Thread currentThread = botThread;
        botThread = null;
        if (currentThread != null && currentThread != Thread.currentThread()) {
            currentThread.interrupt();
        }
        instanceLock.close();
    }

    @Override
    public void stop(Runnable callback) {
        stop();
        callback.run();
    }

    private void closeClient() {
        ILinkClient currentClient;
        synchronized (clientMonitor) {
            currentClient = client;
            client = null;
        }
        if (currentClient != null) {
            currentClient.close();
        }
    }

    @Override
    public boolean isRunning() {
        return running.get();
    }

    @Override
    public boolean isAutoStartup() {
        return true;
    }

    record MessageSummary(
        String text,
        List<MessageItem> imageItems,
        List<String> unsupportedTypes) {
        MessageSummary {
            text = text == null ? "" : text.trim();
            imageItems = List.copyOf(imageItems);
            unsupportedTypes = List.copyOf(unsupportedTypes);
        }

        boolean hasProcessableContent() {
            return !text.isBlank() || !imageItems.isEmpty();
        }
    }
}
