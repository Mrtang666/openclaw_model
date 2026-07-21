package com.youkeda.exercise.shared;


import com.github.wechat.ilink.sdk.ILinkClient;
import com.github.wechat.ilink.sdk.core.config.ILinkConfig;
import com.github.wechat.ilink.sdk.core.listener.OnLoginListener;
import com.github.wechat.ilink.sdk.core.listener.OnMessageListener;
import com.github.wechat.ilink.sdk.core.login.LoginContext;
import com.github.wechat.ilink.sdk.core.model.WeixinMessage;
import com.youkeda.exercise.shared.deepseek.DeepSeekService;
import com.youkeda.exercise.shared.picture.ImageGenerationService;
import com.youkeda.exercise.shared.picture.QwenVisionService;
import com.youkeda.exercise.shared.weather.WeatherService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import java.util.List;
import java.util.concurrent.CountDownLatch;

/**
 * 微信机器人主程序
 * 整合了微信消息接收、天气查询和AI对话功能
 */
@SpringBootApplication
public class WeichatdemoApplication {

    // 登录等待锁
    private static final CountDownLatch loginLatch = new CountDownLatch(1);

    // 微信客户端
    private static ILinkClient client;

    // 消息处理器
    private static MessageHandler messageHandler;

    public static void main(String[] args) {

        // 先测试服务是否可以正常初始化
        try {
            ImageGenerationService test = new ImageGenerationService();
            System.out.println("✅ ImageGenerationService 初始化成功");
        } catch (Exception e) {
            System.err.println("❌ ImageGenerationService 初始化失败: " + e.getMessage());
            e.printStackTrace();
            return;  // 停止启动
        }

        System.out.println("==========================================");
        System.out.println("   🤖 微信机器人 v3.0（AI版）");
        System.out.println("   🌤️  支持天气查询 + AI智能对话");
        System.out.println("   🧠  基于DeepSeek大模型");
        System.out.println("==========================================");

        // 启动Spring Boot应用
        SpringApplication.run(WeichatdemoApplication.class, args);
    }

    @Bean
    public CommandLineRunner startBot(WeatherService weatherService, DeepSeekService deepSeekService, QwenVisionService qwenVisionService, ImageGenerationService imageGenerationService) {
        return args -> {
            System.out.println("🚀 正在启动微信机器人...");
            System.out.println();

            // 检查DeepSeek配置
            if (deepSeekService.isAvailable()) {
                System.out.println("🧠 DeepSeek API ✅ 已配置");
                System.out.println("💡 AI模式默认开启，发送任意文字即可获得智能回复");
            } else {
                System.out.println("⚠️ DeepSeek API ❌ 未配置");
                System.out.println("💡 如需AI功能，请在 application.properties 中设置 deepseek.api.key");
                System.out.println("💡 天气查询功能不受影响，发送「天气 城市名」即可");
            }
            System.out.println();
            System.out.println("💡 发送「帮助」查看所有命令");
            System.out.println();

            // 初始化消息处理器
            messageHandler = new MessageHandler(weatherService, deepSeekService,qwenVisionService,imageGenerationService);

            // ========== 1. 创建配置 ==========
            ILinkConfig config = ILinkConfig.builder()
                    .connectTimeoutMs(30000)
                    .readTimeoutMs(35000)
                    .writeTimeoutMs(30000)
                    .heartbeatEnabled(true)
                    .heartbeatIntervalMs(5000)
                    .autoReconnectEnabled(true)
                    .loginTimeoutMs(180000)
                    .build();

            // ========== 2. 创建微信客户端 ==========
            client = ILinkClient.builder()
                    .config(config)
                    .onLogin(new OnLoginListener() {
                        @Override
                        public void onLoginSuccess(LoginContext context) {
                            System.out.println();
                            System.out.println("✅✅✅ 登录成功！✅✅✅");
                            System.out.println("📋 用户信息: " + context);
                            System.out.println();
                            System.out.println("🤖 机器人已就绪，等待接收消息...");
                            System.out.println("💡 发送「天气 城市名」查询天气");
                            System.out.println("💡 发送任意文字，AI智能回复你！");
                            System.out.println();
                            loginLatch.countDown();
                        }

                        @Override
                        public void onLoginFailure(Throwable throwable) {
                            System.err.println();
                            System.err.println("❌❌❌ 登录失败 ❌❌❌");
                            System.err.println("错误信息: " + throwable.getMessage());
                            throwable.printStackTrace();
                            System.err.println();
                            System.err.println("💡 请检查网络连接，然后重新启动程序");
                            loginLatch.countDown();
                        }
                    })
                    .onMessage(new OnMessageListener() {
                        @Override
                        public void onMessages(List<WeixinMessage> messages) {
                            if (messages == null || messages.isEmpty()) {
                                return;
                            }
                            messageHandler.setClient(client);
                            for (WeixinMessage msg : messages) {
                                messageHandler.handleMessage(msg);
                            }
                        }
                    })
                    .build();

            // ========== 3. 启动登录 ==========
            System.out.println("📱 正在获取登录二维码...");
            System.out.println("💡 请用微信扫描二维码登录");
            System.out.println();

            try {
                String qrResult = client.executeLogin();
                if (qrResult != null && !qrResult.isEmpty()) {
                    if (qrResult.startsWith("http://") || qrResult.startsWith("https://")) {
                        System.out.println("🔗 扫码链接: " + qrResult);
                        System.out.println("💡 如果二维码未自动显示，请复制链接到浏览器打开");
                    } else {
                        System.out.println("📱 二维码数据已生成");
                        System.out.println("💡 如果二维码未自动显示，请检查控制台输出");
                    }
                    System.out.println();
                } else {
                    System.out.println("♻️ 可能正在使用缓存的登录凭证...");
                    System.out.println();
                }
            } catch (Exception e) {
                System.err.println("⚠️ 启动登录异常: " + e.getMessage());
                e.printStackTrace();
            }

            // ========== 4. 等待登录完成 ==========
            loginLatch.await();

            // ========== 5. 注册关闭钩子 ==========
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                System.out.println();
                System.out.println("🛑 正在关闭机器人...");
                if (client != null) {
                    try {
                        client.close();
                        System.out.println("✅ 资源已释放");
                    } catch (Exception e) {
                        System.err.println("⚠️ 释放资源异常: " + e.getMessage());
                    }
                }
                System.out.println("👋 机器人已关闭");
            }));

            // ========== 6. 保持程序运行 ==========
            System.out.println("==========================================");
            System.out.println("   🤖 机器人运行中...");
            System.out.println("   🌤️  「天气 城市名」查询天气");
            System.out.println("   🧠  任意文字触发AI智能回复");
            System.out.println("   💡 发送「帮助」查看所有命令");
            System.out.println("   💡 按 Ctrl+C 退出程序");
            System.out.println("==========================================");
            System.out.println();

            Thread.currentThread().join();
        };
    }
}