package com.example.wechat;

import com.example.wechat.handler.WeChatMessageHandler;
import com.example.wechat.listener.WeChatMessageListener;
import com.github.wechat.ilink.sdk.ILinkClient;
import com.github.wechat.ilink.sdk.ILinkClientBuilder;
import com.github.wechat.ilink.sdk.core.config.ILinkConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@Slf4j
@SpringBootApplication
@EnableConfigurationProperties
public class WeChatAiAgentApplication implements CommandLineRunner {

    @Autowired
    private WeChatMessageHandler messageHandler;

    @Autowired
    private WeChatMessageListener messageListener;

    private ILinkClient client;

    public static void main(String[] args) {
        SpringApplication.run(WeChatAiAgentApplication.class, args);
    }

    @Override
    public void run(String... args) throws Exception {
        log.info("正在启动微信AI机器人...");

        // 1. 构建ILinkConfig
        ILinkConfig config = ILinkConfig.builder()
                .heartbeatEnabled(true)
                .heartbeatIntervalMs(30000L)
                .connectTimeoutMs(35000L)
                .readTimeoutMs(35000L)
                .writeTimeoutMs(35000L)
                .build();

        // 2. 构建ILinkClient
        client = new ILinkClientBuilder()
                .config(config)
                .onMessage(messageListener)
                .build();

        // 3. 将client注入到listener中
        messageListener.setClient(client);

        // 4. 登录 - 生成二维码
        String qrCodeImage = client.executeLogin();
        log.info("=== 请扫描二维码登录 ===");
        log.info("二维码图片(Base64): {}", qrCodeImage.substring(0, Math.min(100, qrCodeImage.length())) + "...");

        // 5. 等待登录完成
        client.getLoginFuture().join();
        log.info("✅ 登录成功！开始监听消息...");

        // 6. 启动一个线程来轮询消息
        Thread pollThread = new Thread(() -> {
            while (true) {
                try {
                    // 消息会通过listener自动处理
                    client.getUpdates();
                    Thread.sleep(1000);
                } catch (Exception e) {
                    log.error("获取消息异常", e);
                    try {
                        Thread.sleep(5000);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        });
        pollThread.setDaemon(true);
        pollThread.start();

        // 7. 保持主线程运行
        log.info("机器人已启动，按 Ctrl+C 停止");
        Thread.currentThread().join();
    }
}