# OpenClaw Model

仓库根目录是 Maven 聚合项目，主应用位于 `openclaw_model-main` 模块。

项目通过微信 iLink SDK 接收消息，并使用四个独立模块完成处理：

- 百炼对话 Agent：普通文本对话和多轮上下文
- 和风天气 Agent：城市、区、县实时天气
- 百炼视觉 Agent：微信图片及图片 URL 识别
- 百炼图片生成 Agent：生成图片并直接发送到微信

## 快速开始

1. 复制 `openclaw_model-main/.evn.example` 为 `openclaw_model-main/.evn`。
2. 在 `.evn` 中填写百炼和和风天气配置。
3. 在 IDEA 中运行共享配置 `DemoApplication`，或执行：

```powershell
mvn clean package
java -jar openclaw_model-main\target\spring-startup-logging-0.0.1-SNAPSHOT.jar
```

`.evn` 已被 Git 忽略，不会提交到仓库。详细配置和使用说明见
`openclaw_model-main/README.md`。
