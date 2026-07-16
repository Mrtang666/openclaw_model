# openclaw_model

Spring Boot 应用已接入微信 iLink SDK 2.3.3，当前对收到的文本消息回复固定内容。

## 运行

在 IDEA 中运行 `com.example.spring.DemoApplication`，或执行：

```shell
mvn spring-boot:run
```

启动后会生成并尝试自动打开微信登录二维码。如果没有自动打开，请手动打开：

```text
target/ilink-login-qr.png
```

使用微信扫码确认后，程序会持续接收文本消息并回复：

```text
你好，我已收到你的消息。
```

## 配置

配置位于 `src/main/resources/application.properties`：

```properties
ilink.bot.enabled=true
ilink.bot.fixed-reply=你好，我已收到你的消息。
ilink.bot.retry-delay=2s
ilink.bot.qr-code-output=target/ilink-login-qr.png
```

后续接入 OpenClaw 模型时，可在 `WeChatILinkBot.replyToTextMessages` 中将固定回复替换为模型输出。
