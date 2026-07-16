package com.example.spring.wechat;

import com.github.wechat.ilink.sdk.ILinkClient;
import com.github.wechat.ilink.sdk.core.config.ILinkConfig;
import com.github.wechat.ilink.sdk.core.login.LoginContext;
import com.github.wechat.ilink.sdk.core.model.MessageItem;
import com.github.wechat.ilink.sdk.core.model.WeixinMessage;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

@Component
public class WeChatILinkBot implements SmartLifecycle {
    private static final Logger log = LoggerFactory.getLogger(WeChatILinkBot.class);

    private final WeChatBotProperties properties;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final Object clientMonitor = new Object();

    private volatile ExecutorService executor;
    private volatile ILinkClient client;

    public WeChatILinkBot(WeChatBotProperties properties) {
        this.properties = properties;
    }

    @Override
    public void start() {
        if (!properties.isEnabled()) {
            log.info("微信 iLink 机器人已禁用");
            return;
        }
        if (!running.compareAndSet(false, true)) {
            return;
        }

        executor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "wechat-ilink-bot");
            thread.setDaemon(false);
            return thread;
        });
        executor.submit(this::runBot);
    }

    private void runBot() {
        while (running.get() && !Thread.currentThread().isInterrupted()) {
            try {
                ILinkClient activeClient = createClient();
                String qrCodeContent = activeClient.executeLogin();
                showLoginQrCode(qrCodeContent);

                LoginContext loginContext = activeClient.getLoginFuture().get();
                log.info("微信登录成功，botId={}", loginContext.getBotId());
                log.info("固定回复机器人已启动，回复内容：{}", properties.getFixedReply());

                while (running.get() && !Thread.currentThread().isInterrupted()) {
                    pollAndReply(activeClient);
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception exception) {
                if (running.get()) {
                    log.warn(
                        "微信登录二维码或会话已失效，{} 后自动生成新二维码",
                        properties.getRetryDelay(),
                        exception);
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
    }

    private ILinkClient createClient() {
        ILinkConfig config = ILinkConfig.builder().heartbeatEnabled(false).build();
        ILinkClient newClient = ILinkClient.builder().config(config).build();
        synchronized (clientMonitor) {
            client = newClient;
        }
        return newClient;
    }

    private void pollAndReply(ILinkClient activeClient) throws InterruptedException {
        try {
            replyToTextMessages(activeClient, activeClient.getUpdates());
        } catch (IOException exception) {
            if (running.get()) {
                log.warn("拉取消息失败，{} 后重试", properties.getRetryDelay(), exception);
                Thread.sleep(properties.getRetryDelay().toMillis());
            }
        }
    }

    void replyToTextMessages(ILinkClient activeClient, List<WeixinMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return;
        }
        for (WeixinMessage message : messages) {
            String fromUserId = message.getFrom_user_id();
            if (fromUserId == null || fromUserId.isBlank() || !containsText(message)) {
                continue;
            }
            try {
                activeClient.sendText(fromUserId, properties.getFixedReply());
                log.info("已回复用户 userId={}，messageId={}", fromUserId, message.getMessage_id());
            } catch (IOException | RuntimeException exception) {
                log.error(
                    "回复失败 userId={}，messageId={}",
                    fromUserId,
                    message.getMessage_id(),
                    exception);
            }
        }
    }

    static boolean containsText(WeixinMessage message) {
        if (message == null || message.getItem_list() == null) {
            return false;
        }
        for (MessageItem item : message.getItem_list()) {
            if (item != null
                && item.getText_item() != null
                && item.getText_item().getText() != null
                && !item.getText_item().getText().isBlank()) {
                return true;
            }
        }
        return false;
    }

    private void showLoginQrCode(String qrCodeContent) {
        Path outputFile = Paths.get(properties.getQrCodeOutput());
        try {
            Path savedFile = QrCodeImage.save(qrCodeContent, outputFile);
            log.info("微信登录二维码已保存到：{}", savedFile);
            if (!QrCodeImage.open(savedFile)) {
                log.info("请手动打开该图片，并使用微信扫码");
            }
        } catch (IOException exception) {
            log.error("二维码生成失败，登录链接：{}", qrCodeContent, exception);
        }
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
        ExecutorService currentExecutor = executor;
        if (currentExecutor != null) {
            currentExecutor.shutdownNow();
        }
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
}
