package com.example.spring.wechat;

import com.example.spring.agent.AgentService;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.function.Supplier;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

@Service
public class WechatBotService {

    private final WechatClientFactory clientFactory;
    private final Supplier<AgentService> agentServiceSupplier;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "wechat-bot-worker");
        thread.setDaemon(true);
        return thread;
    });

    private volatile WechatBotState state = WechatBotState.STOPPED;
    private volatile WechatClient client;
    private volatile Future<?> worker;
    private volatile boolean stopRequested;
    private volatile String botId;
    private volatile String lastError;

    @Autowired
    public WechatBotService(WechatClientFactory clientFactory, ObjectProvider<AgentService> agentServiceProvider) {
        this(clientFactory, agentServiceProvider::getObject);
    }

    WechatBotService(WechatClientFactory clientFactory, AgentService agentService) {
        this(clientFactory, () -> agentService);
    }

    private WechatBotService(WechatClientFactory clientFactory, Supplier<AgentService> agentServiceSupplier) {
        this.clientFactory = clientFactory;
        this.agentServiceSupplier = agentServiceSupplier;
    }

    public synchronized WechatStartResult start() {
        if (state == WechatBotState.WAITING_FOR_SCAN || state == WechatBotState.RUNNING) {
            return new WechatStartResult(false, "微信 Bot 已在运行", null);
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

            while (!stopRequested && client == activeClient) {
                List<WechatIncomingMessage> messages = activeClient.getUpdates();
                for (WechatIncomingMessage message : messages) {
                    handleMessage(activeClient, message);
                }
            }
        } catch (CancellationException exception) {
            if (!stopRequested) {
                moveToError(activeClient, "登录已取消");
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
        if (message.text() == null || message.text().isBlank()) {
            return;
        }

        String reply = agentServiceSupplier.get().handle(message.text());
        if (reply == null || reply.isBlank()) {
            return;
        }

        try {
            activeClient.sendText(message.fromUserId(), reply);
        } catch (Exception exception) {
            lastError = rootMessage(exception);
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
}
