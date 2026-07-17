# OpenClaw CLI

一个基于 Java 17 和 Spring Boot 的 CLI + 微信 Agent 项目。

当前能力：

- CLI 普通文本默认调用阿里百炼大模型
- `/help`、`/version`、`/status`、`/weather 城市`、`/wechat` 等本地命令
- 高德天气 API 查询，并由大模型整理成自然回复和出门建议
- 微信图片识别：支持微信附件图片、图片链接和 data URI，先描述图片内容，再继续对话
- 图片生成：支持 CLI `/image` 命令和微信自然语言生成图片，生成后会下载图片并发回微信
- 微信 iLink 接入，支持扫码登录、接收消息、发送回复
- 微信端按 `from_user_id` 保存最近 6 轮上下文，支持连续对话
- iLink 接收日志，可排查消息是否进入 `getUpdates()`
- CLI 流式输出；微信端先发送“我在整理答案，请稍等一下～”，最终回复自动分段发送

## 快速理解

```text
用户输入
  |-- CLI 终端
  |     `-- AgentService -> CommandDispatcher -> 命令 / 大模型
  |
  `-- 微信 iLink
        `-- IlinkWechatClient -> WechatBotService -> WechatConversationService
              |-- 天气问题 -> 高德天气 API -> 阿里百炼整理回复
              `-- 普通对话 -> 上下文历史 -> 阿里百炼回复
```

## 项目结构

```text
src/main/java/com/example/spring
|-- AgentClawApplication.java          # Spring Boot 启动类
|-- agent                              # Agent 统一入口
|   |-- AgentService.java
|   `-- ReplyEmitter.java
|-- chat                               # 阿里百炼大模型接入
|   |-- ChatClient.java
|   |-- ChatReply.java
|   |-- ChatService.java
|   |-- ChatServiceException.java
|   `-- DashScopeChatClient.java
|-- cli                                # CLI 输入输出
|   `-- ConsoleRunner.java
|-- command                            # 本地命令系统
|   |-- Command.java
|   |-- CommandDispatcher.java
|   |-- CommandRegistry.java
|   |-- ErrorMessageFormatter.java
|   |-- HelpCommand.java
|   |-- StatusCommand.java
|   |-- VersionCommand.java
|   |-- WeatherCommand.java
|   `-- WechatCommand.java
|-- exception                          # 业务异常
|   |-- CommandException.java
|   `-- WeatherServiceException.java
|-- tool                               # Agent 工具封装
|   |-- AgentTool.java
|   |-- ToolRegistry.java
|   `-- WeatherTool.java
|-- weather                            # 高德天气 API
|   |-- AmapWeatherClient.java
|   |-- WeatherClient.java
|   |-- WeatherResult.java
|   `-- WeatherService.java
|-- image                              # 图片生成模块
|   `-- generation
|       |-- ImageGenerationClient.java
|       |-- ImageGenerationException.java
|       |-- ImageGenerationIntentParser.java
|       |-- ImageGenerationRequest.java
|       |-- ImageGenerationResult.java
|       |-- ImageGenerationService.java
|       `-- client
|           `-- DashScopeImageGenerationClient.java
`-- wechat                             # 微信 iLink 接入和会话处理
    |-- bot                            # Bot 生命周期、状态、消息调度
    |   |-- WechatBotService.java
    |   |-- WechatBotState.java
    |   |-- WechatBotStatus.java
    |   `-- WechatReply.java
    |   `-- WechatStartResult.java
    |-- client                         # iLink SDK 适配层和微信客户端抽象
    |   |-- IlinkWechatClient.java
    |   |-- IlinkWechatClientFactory.java
    |   |-- WechatClient.java
    |   |-- WechatClientFactory.java
    |   |-- WechatIncomingMessage.java
    |   `-- WechatLoginInfo.java
    `-- conversation                   # 微信自然语言会话、上下文和意图识别
        |-- WeatherIntentParser.java
        `-- WechatConversationService.java
```

更详细的结构说明见 [docs/PROJECT_STRUCTURE.md](docs/PROJECT_STRUCTURE.md)。

## 配置

复制 `.env.example` 为 `.env`，并填写自己的 Key：

```properties
AMAP_WEATHER_KEY=你的高德 Web 服务 API KEY
DASHSCOPE_API_KEY=你的阿里百炼 API KEY
```

默认大模型配置在 `src/main/resources/application.properties`：

```properties
dashscope.base-url=https://ws-6gncy95g9skiwjfi.cn-beijing.maas.aliyuncs.com/compatible-mode/v1
dashscope.model=qwen3.7-plus
dashscope.vision-model=qwen3.7-plus
dashscope.enable-thinking=true
dashscope.image-base-url=https://ws-6gncy95g9skiwjfi.cn-beijing.maas.aliyuncs.com/api/v1
dashscope.image-model=qwen-image-2.0-pro
image.output-dir=generated-images
```

## 运行

如果本机还没有安装微信 iLink SDK，先执行：

```powershell
cd C:\Users\Lenovo\Desktop\wechat-ilink-sdk-java
mvn clean install -DskipTests "-Dmaven.compiler.source=8" "-Dmaven.compiler.target=8" "-Dmaven.compiler.release=8"
cd C:\Users\Lenovo\Desktop\openclaw_model
```

启动项目：

```powershell
mvn spring-boot:run
```

常用输入：

```text
你是谁              -> 默认走阿里百炼大模型聊天
/help              -> 查看帮助
/version           -> 查看版本
/status            -> 查看状态
/weather 北京      -> 查询北京天气
/image 一只赛博朋克风格的橘猫 -> 生成图片
/wechat start      -> 启动微信 Bot
/wechat status     -> 查看微信 Bot 状态
/wechat stop       -> 停止微信 Bot
exit               -> 退出程序
```

也可以将命令作为启动参数执行：

```powershell
mvn spring-boot:run "-Dspring-boot.run.arguments=/status"
```

## 微信接入说明

微信入口复用 Agent 能力，但多了一层微信会话处理：

- 普通文本默认进入阿里百炼大模型
- 天气类自然语言会先解析城市，再调用高德天气 API
- 图片生成类自然语言会先提取提示词，再调用阿里百炼文生图 API
- 每个 `from_user_id` 保存最近 6 轮上下文
- 发送消息依赖 iLink 的 `contextToken`，这个 token 来自 `getUpdates()` 拉到的入站消息

示例：

```text
用户：我想出去玩
助手：你偏好什么类型？
用户：可以，偏好美食
助手：会结合前文继续推荐，而不是把这句话当成孤立输入
```

## 日志排查

iLink 接收日志会打印：

```text
iLink 收到文本消息：messageId=..., fromUserId=..., contextToken=..., text=...
```

微信处理日志会打印：

```text
微信收到消息，fromUserId=..., text=...
微信会话收到消息，userId=..., text=...
微信回复发送完成，fromUserId=..., replyLength=...
```

如果用户说“微信没有反应”，优先看日志里是否出现：

1. `iLink 收到文本消息`
2. `微信收到消息`
3. `微信会话收到消息`
4. `微信回复发送完成`

缺在哪一步，就重点排查哪一层。

## 测试与构建

```powershell
mvn test
mvn clean package
```
