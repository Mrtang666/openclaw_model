# 微信 iLink 多 Agent 助手

Spring Boot 应用通过微信 iLink SDK 接收消息，根据内容自动调用对话、天气、
图片识别或图片生成模块。项目只允许启动一个微信客户端和一个机器人，收到消息后
会先回复处理状态，再由同一个机器人线程严格按照接收顺序逐条处理。

## 功能

- 普通文本：调用阿里云百炼 `qwen-plus` 多轮对话
- 微信图片：下载原图并调用百炼 `qwen3-vl-plus` 识别
- 图片 URL：安全下载图片后调用视觉模型识别
- 实时天气：通过和风天气查询城市、区、县
- 图片生成：调用百炼图像生成任务，完成后直接发送微信图片
- 历史图片编辑：使用 `qwen-image-2.0` 修改上一张或之前生成的图片
- 持久化记忆：SQLite 保存受限文本历史，图片文件使用数量和容量限制
- 其他消息：文件、视频和无法转写的语音也会收到明确反馈

## 外部配置

复制配置模板：

```powershell
Copy-Item .evn.example .evn
```

在本地 `.evn` 中填写：

```properties
BAILIAN_API_KEY=你的百炼APIKey
BAILIAN_COMPATIBLE_BASE_URL=https://你的WorkspaceId.cn-beijing.maas.aliyuncs.com/compatible-mode/v1
BAILIAN_CHAT_MODEL=qwen-plus
BAILIAN_VISION_MODEL=qwen3-vl-plus
BAILIAN_IMAGE_MODEL=wanx-v1
BAILIAN_IMAGE_EDIT_MODEL=qwen-image-2.0

WEATHER_API_KEY=你的和风天气APIKey
WEATHER_API_HOST=https://你的和风天气专属APIHost
```

百炼的 API Key 与 Base URL 必须属于同一地域。北京地域的新接口通常使用带
`WorkspaceId` 的工作空间专属地址，请以百炼控制台展示的地址为准。

和风天气的 API Key 与专属 API Host 也必须配套使用，并开通 GeoAPI 与实时天气权限。

真实 `.evn` 已加入 `.gitignore`。日志只输出模型名称、接口主机和 Key 是否存在，
不会输出 Key 内容。

## 记忆与存储限制

默认配置针对本地快速运行：

- SQLite 只保存最近 40 条结构化消息和图片索引
- 发送给模型时只读取最近 12 条
- 每个用户最多保存 12 张图片
- 每个用户图片总量最多 50MB
- 图片保存在 `runtime-data/images`，数据库位于 `runtime-data/memory.db`
- `runtime-data` 已被 Git 忽略

可以通过 `MEMORY_MAX_ENTRIES_PER_USER`、`MEMORY_PROMPT_ENTRIES`、
`MEMORY_MAX_IMAGES_PER_USER` 和 `MEMORY_MAX_IMAGE_BYTES_PER_USER` 调整。

收到“上一张图片是什么”时会读取历史图片进行识别；收到“把上一张改成夜景”时
会调用千问图像编辑模型，并把编辑结果保存为新的历史图片。

## IDEA 运行

使用项目中的共享运行配置 `DemoApplication`。程序会输出微信扫码登录入口，并调用
系统默认浏览器打开入口。扫码成功后即可在微信中发送消息。

共享运行配置已包含 `--enable-native-access=ALL-UNNAMED`，用于允许 SQLite JDBC
在 Java 25 中加载本地库。请使用该配置启动，避免出现 restricted method WARNING。

机器人使用用户目录下的单实例文件锁。不要同时运行 IDEA 中的 `DemoApplication`
和命令行 JAR；第二个实例会检测到已有机器人并停止微信连接，避免两个 iLink 客户端
互相挤掉登录会话。锁文件默认位于：

```text
%USERPROFILE%\.openclaw-model\wechat-ilink.lock
```

如果从 `DemoApplication.java` 左侧绿色三角直接运行，配置也会从以下位置自动查找：

- 当前工作目录的 `.evn`
- 上级目录的 `.evn`
- 外层项目中的 `openclaw_model-main/.evn`

## 命令行运行

在仓库根目录执行：

```powershell
mvn clean package
java -jar openclaw_model-main\target\spring-startup-logging-0.0.1-SNAPSHOT.jar
```

只验证配置和 Spring 上下文，不连接微信：

```powershell
java -jar openclaw_model-main\target\spring-startup-logging-0.0.1-SNAPSHOT.jar --ilink.bot.enabled=false
```

## 对话示例

```text
你好，请介绍一下你自己
查询江苏无锡滨湖区的实时天气
帮我识别这张图片：https://example.com/photo.png
画一张雨夜江南古镇的水彩插画
按照我发的图片内容生成一张插画
```

## 测试

测试使用本地模拟服务，不调用真实百炼或和风天气 API：

```powershell
mvn test
```

配置好本地 `.evn` 后，可手动执行真实接口冒烟测试。该命令会实际调用一次对话、
天气、视觉、图片生成和图片编辑接口，可能产生少量 API 费用：

```powershell
mvn "-Dlive.api.tests=true" "-Dtest=LiveApiIntegrationTests" test
```
