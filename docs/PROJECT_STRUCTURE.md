# 项目结构说明

这份文档记录 OpenClaw 当前代码分组。当前结构按“入口 -> 编排 -> 工具协议 -> 微信工具 -> 外部服务”的方式整理。

## 1. 总体分层

```text
用户入口
  ├─ CLI 控制台
  └─ 微信 iLink

入口与分发
  ├─ cli/
  ├─ cli/command/
  ├─ agent/
  └─ wechat/bot/

会话编排与工具规划
  ├─ tool/protocol/
  └─ wechat/conversation/

领域能力
  ├─ chat/
  ├─ weather/
  ├─ wechat/image/
  ├─ wechat/image/generation/
  └─ wechat/voice/

外部平台适配
  ├─ wechat/adapter/
  ├─ wechat/adapter/ilink/
  ├─ weather/client/
  ├─ wechat/image/generation/client/
  └─ chat/DashScopeChatClient.java
```

## 2. 根包

```text
com.example.spring
  └─ AgentClawApplication.java
```

Spring Boot 启动类，负责启动整个项目。

## 3. agent：CLI 默认对话入口

```text
agent/
  ├─ AgentService.java
  └─ ReplyEmitter.java
```

- `AgentService`：CLI 输入的统一处理入口，决定走本地命令还是大模型对话。
- `ReplyEmitter`：流式输出接口，用于 CLI 流式打印和大模型流式回复。

## 4. cli：控制台入口与命令系统

```text
cli/
  ├─ ConsoleRunner.java
  └─ command/
      ├─ core/
      │   ├─ Command.java
      │   ├─ CommandDispatcher.java
      │   ├─ CommandRegistry.java
      │   └─ ErrorMessageFormatter.java
      └─ impl/
          ├─ HelpCommand.java
          ├─ VersionCommand.java
          ├─ StatusCommand.java
          ├─ WeatherCommand.java
          └─ WechatCommand.java
```

- `ConsoleRunner`：项目启动后的控制台循环。
- `command/core`：命令接口、注册、分发、错误格式化。
- `command/impl`：具体 CLI 命令实现，目前包含 `/help`、`/version`、`/status`、`/weather`、`/wechat`。
- CLI 图片生成命令已经移除，不再存在 `ImageCommand`。

## 5. chat：文本大模型能力

```text
chat/
  ├─ ChatClient.java
  ├─ ChatReply.java
  ├─ ChatService.java
  ├─ ChatServiceException.java
  └─ DashScopeChatClient.java
```

- `ChatService`：业务层统一调用的大模型服务。
- `DashScopeChatClient`：阿里百炼兼容 OpenAI Chat Completions 接口的客户端。

## 6. tool：工具协议与旧版工具注册

```text
tool/
  ├─ AgentTool.java
  ├─ ToolRegistry.java
  ├─ WeatherTool.java
  └─ protocol/
      ├─ ConversationIntentDecision.java
      ├─ ToolCall.java
      ├─ ToolCallPlanParser.java
      ├─ ToolCallPlanner.java
      └─ ToolPlan.java
```

- `tool/protocol`：结构化工具调用协议，让大模型输出 JSON 任务计划。
- `ToolCallPlanner`：把用户需求、历史上下文和工具列表交给大模型做任务拆解。
- `ToolCallPlanParser`：解析模型输出的 JSON。

## 7. weather：天气能力

```text
weather/
  ├─ client/
  │   ├─ AmapWeatherClient.java
  │   └─ WeatherClient.java
  ├─ model/
  │   └─ WeatherResult.java
  └─ service/
      └─ WeatherService.java
```

- `client`：高德天气 API 调用。
- `model`：天气结果数据结构。
- `service`：天气查询参数校验和业务封装。

## 8. wechat/adapter 与 wechat/model：微信平台适配

```text
wechat/
  ├─ adapter/
  │   ├─ WechatClient.java
  │   ├─ WechatClientFactory.java
  │   └─ ilink/
  │       ├─ IlinkWechatClient.java
  │       └─ IlinkWechatClientFactory.java
  └─ model/
      ├─ ImageSourceType.java
      ├─ VoiceSourceType.java
      ├─ WechatIncomingImage.java
      ├─ WechatIncomingMessage.java
      ├─ WechatIncomingVoice.java
      └─ WechatLoginInfo.java
```

- `wechat/adapter`：微信客户端抽象。
- `wechat/adapter/ilink`：iLink SDK 适配实现。
- `wechat/model`：微信入站消息、图片、语音、登录信息等数据对象。

## 9. wechat/bot：微信 Bot 生命周期与发送层

```text
wechat/bot/
  ├─ WechatBotService.java
  ├─ WechatBotState.java
  ├─ WechatBotStatus.java
  ├─ WechatReply.java
  └─ WechatStartResult.java
```

- `WechatBotService`：登录、拉取消息、发送等待提示、异步处理消息、发送文本/图片/语音。
- `WechatReply`：微信回复对象，支持有序 `parts`，例如文本、图片、语音按顺序发送。

## 10. wechat/conversation：微信会话编排与工具中心

```text
wechat/conversation/
  ├─ WechatConversationService.java
  ├─ intent/
  │   └─ WeatherIntentParser.java
  └─ tools/
      ├─ WechatTool.java
      ├─ WechatToolDefinition.java
      ├─ WechatToolRegistry.java
      ├─ WechatToolRequest.java
      ├─ ChatWechatTool.java
      ├─ WeatherWechatTool.java
      ├─ ImageGenerationWechatTool.java
      ├─ VoiceRecognitionWechatTool.java
      ├─ VoiceSynthesisWechatTool.java
      └─ VoiceStyleWechatTool.java
```

- `WechatConversationService`：微信端核心编排层，处理文本、图片、语音、上下文、工具计划和最终回复组合。
- `intent`：兜底规则意图识别。
- `tools`：微信端插件化工具中心，天气、聊天、图片生成、语音识别、语音生成、音色修改都以工具形式注册。

## 11. wechat/image：微信图片理解

```text
wechat/image/
  ├─ exception/
  │   └─ ImageUnderstandingException.java
  ├─ model/
  │   └─ ImageAnalysisRequest.java
  ├─ service/
  │   ├─ ImageInputResolver.java
  │   ├─ ImageUnderstandingService.java
  │   └─ DefaultImageUnderstandingService.java
  └─ client/
      ├─ ImageUnderstandingClient.java
      └─ DashScopeImageUnderstandingClient.java
```

- `ImageInputResolver`：解析微信图片、图片链接、data URI。
- `DefaultImageUnderstandingService`：调用视觉模型并生成图片描述/回答。

## 12. wechat/image/generation：微信图片生成

```text
wechat/image/generation/
  ├─ ImageGenerationClient.java
  ├─ ImageGenerationException.java
  ├─ client/
  │   └─ DashScopeImageGenerationClient.java
  ├─ intent/
  │   └─ ImageGenerationIntentParser.java
  ├─ model/
  │   ├─ ImageGenerationRequest.java
  │   └─ ImageGenerationResult.java
  └─ service/
      └─ ImageGenerationService.java
```

- `intent`：图片生成相关旧规则意图识别。
- `model`：图片生成请求和结果。
- `service`：图片生成和图片下载封装。
- `client`：阿里百炼图片生成 API 客户端。
- 该模块只服务微信端图片生成工具，不再暴露 CLI 图片生成命令。

## 13. wechat/voice：微信语音识别、语音生成与音色偏好

```text
wechat/voice/
  ├─ recognition/
  │   ├─ VoiceRecognitionException.java
  │   ├─ audio/
  │   │   ├─ AudioFormatDetector.java
  │   │   ├─ AudioTranscoder.java
  │   │   └─ FfmpegAudioTranscoder.java
  │   ├─ client/
  │   │   ├─ VoiceRecognitionClient.java
  │   │   └─ DashScopeVoiceRecognitionClient.java
  │   ├─ model/
  │   │   ├─ VoiceRecognitionRequest.java
  │   │   └─ VoiceRecognitionResult.java
  │   └─ service/
  │       ├─ VoiceRecognitionService.java
  │       └─ DefaultVoiceRecognitionService.java
  ├─ synthesis/
  │   ├─ exception/
  │   │   └─ VoiceSynthesisException.java
  │   ├─ client/
  │   │   ├─ VoiceSynthesisClient.java
  │   │   └─ DashScopeVoiceSynthesisClient.java
  │   ├─ model/
  │   │   ├─ VoiceSynthesisAudio.java
  │   │   ├─ VoiceSynthesisRequest.java
  │   │   └─ VoiceSynthesisSegment.java
  │   └─ service/
  │       ├─ VoiceSynthesisService.java
  │       └─ DefaultVoiceSynthesisService.java
  └─ style/
      ├─ model/
      │   ├─ VoiceCandidatePage.java
      │   └─ VoiceProfile.java
      └─ service/
          ├─ VoiceCatalog.java
          └─ VoicePreferenceService.java
```

- `recognition`：ASR，微信语音转文字。
- `synthesis`：TTS，文本转语音，长文本会按时长限制拆段。
- `style`：音色目录、候选筛选、试听上下文和用户音色偏好。当前使用内存保存偏好，后续引入数据库时可以替换 `VoicePreferenceService` 的存储实现。

## 14. 测试目录

测试目录整体跟随主代码包结构：

```text
src/test/java/com/example/spring/cli/command/
src/test/java/com/example/spring/weather/client/
src/test/java/com/example/spring/wechat/adapter/ilink/
src/test/java/com/example/spring/wechat/bot/
src/test/java/com/example/spring/wechat/conversation/
src/test/java/com/example/spring/wechat/conversation/tools/
src/test/java/com/example/spring/wechat/image/
src/test/java/com/example/spring/wechat/image/generation/
src/test/java/com/example/spring/wechat/voice/recognition/
src/test/java/com/example/spring/wechat/voice/style/
src/test/java/com/example/spring/wechat/voice/synthesis/
```

重构后使用干净构建验证：

```powershell
mvn clean test
```
