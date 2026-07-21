# 项目结构说明

这份文档用于说明 OpenClaw 当前代码的分层和每个主要目录的职责。当前项目以“入口层 -> 会话编排层 -> 工具协议层 -> 具体工具能力 -> 外部服务适配”的方式组织代码，重点能力集中在微信端 Agent。

## 1. 总体分层

```text
用户入口
  ├─ CLI 控制台
  └─ 微信 iLink

入口与分发
  ├─ agent/
  ├─ cli/
  ├─ cli/command/
  └─ wechat/bot/

会话编排与工具规划
  ├─ tool/protocol/
  └─ wechat/conversation/

领域能力
  ├─ chat/
  ├─ weather/
  ├─ wechat/document/
  ├─ wechat/image/
  ├─ wechat/image/generation/
  └─ wechat/voice/

外部平台适配
  ├─ chat/DashScopeChatClient.java
  ├─ weather/client/
  ├─ wechat/adapter/
  ├─ wechat/adapter/ilink/
  ├─ wechat/image/client/
  ├─ wechat/image/generation/client/
  ├─ wechat/voice/recognition/client/
  └─ wechat/voice/synthesis/client/
```

## 2. 根包

```text
com.example.spring
  └─ AgentClawApplication.java
```

- `AgentClawApplication`：Spring Boot 启动类，负责启动整个项目。

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

- `ConsoleRunner`：项目启动后的控制台输入循环。
- `command/core`：命令接口、命令注册、命令分发、错误格式化。
- `command/impl`：具体 CLI 命令实现，目前包括 `/help`、`/version`、`/status`、`/weather`、`/wechat`。
- CLI 端已经移除图片生成命令，图片生成只保留在微信端工具里。

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
- 当前主要用于普通对话、工具规划、提示词优化、天气结果整理、文档正文生成等场景。

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
- `ToolCallPlanParser`：解析模型输出的 JSON，并转成系统可执行的工具调用计划。

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
      ├─ WechatIncomingFile.java
      ├─ WechatIncomingImage.java
      ├─ WechatIncomingMessage.java
      ├─ WechatIncomingVoice.java
      └─ WechatLoginInfo.java
```

- `WechatClient`：微信客户端抽象接口，隐藏 iLink SDK 的具体实现。
- `IlinkWechatClient`：iLink SDK 适配实现，负责登录、接收消息、下载图片/语音/文件、发送文本/图片/语音/文件。
- `wechat/model`：微信入站消息、图片、语音、文件、登录信息等数据对象。
- `WechatIncomingFile`：微信文件附件的统一模型，包含文件名、MIME、字节内容、大小、哈希等信息。

## 9. wechat/bot：微信 Bot 生命周期与发送层

```text
wechat/bot/
  ├─ WechatBotService.java
  ├─ WechatBotState.java
  ├─ WechatBotStatus.java
  ├─ WechatReply.java
  └─ WechatStartResult.java
```

- `WechatBotService`：登录、拉取消息、发送等待提示、异步处理消息、发送文本/图片/语音/文件。
- `WechatReply`：微信回复对象，支持有序 `parts`，例如文本、图片、语音、文件按顺序发送。

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
      ├─ DocumentAnalysisWechatTool.java
      ├─ DocumentGenerationWechatTool.java
      ├─ VoiceRecognitionWechatTool.java
      ├─ VoiceSynthesisWechatTool.java
      └─ VoiceStyleWechatTool.java
```

- `WechatConversationService`：微信端核心编排层，处理文本、图片、语音、文件、上下文、工具计划和最终回复组合。
- `intent`：兜底规则意图识别，目前主要用于天气等兼容路径。
- `tools`：微信端插件化工具中心。天气、聊天、图片生成、文档解析、文档生成、语音识别、语音生成、音色修改都以工具形式注册。
- `WechatToolRequest`：工具执行请求，携带用户文本、工具参数、历史上下文、图片和文件上下文等信息。

## 11. wechat/document：微信文件解析、分块、生成与归档

```text
wechat/document/
  ├─ model/
  │   ├─ DocumentChunk.java
  │   ├─ DocumentFormat.java
  │   ├─ GeneratedDocument.java
  │   ├─ GeneratedDocumentRequest.java
  │   └─ ParsedDocument.java
  └─ service/
      ├─ DefaultDocumentGenerationService.java
      ├─ DocumentArchiveService.java
      ├─ DocumentChunkService.java
      ├─ DocumentParseService.java
      └─ DocumentTypeDetector.java
```

- `DocumentTypeDetector`：结合文件后缀、MIME 和文件头识别文件类型。
- `DocumentParseService`：解析 PDF、DOCX、TXT、Markdown、XLSX、PPTX 文件，把文件正文转成可供模型理解的文本。
- `DocumentChunkService`：把长文档按长度切块，避免一次性把大文件全部塞进模型上下文。
- `DocumentArchiveService`：把微信收到的原始文件保存到本地 `data/wechat/documents`，方便后续追溯和复用。
- `DefaultDocumentGenerationService`：根据模型生成的正文导出 DOCX、PDF、TXT、Markdown 文档。
- `DocumentAnalysisWechatTool`：微信端文档解析工具，用于总结、提炼重点、读取文件内容。
- `DocumentGenerationWechatTool`：微信端文档生成工具，用于根据用户需求或最近文件上下文生成可发送的文件。

## 12. wechat/image：微信图片理解

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

- `ImageInputResolver`：解析微信附件图片、图片链接、data URI。
- `DefaultImageUnderstandingService`：调用视觉模型并生成图片描述或基于图片的回答。

## 13. wechat/image/generation：微信图片生成

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

- `intent`：图片生成相关的旧规则意图识别。
- `model`：图片生成请求和结果。
- `service`：图片生成和图片下载封装。
- `client`：阿里百炼图片生成 API 客户端。
- 该模块只服务微信端图片生成工具，不再暴露 CLI 图片生成命令。

## 14. wechat/voice：微信语音识别、语音生成与音色偏好

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

- `recognition`：ASR，负责微信语音转文字。
- `synthesis`：TTS，负责文本转语音，长文本会按时长限制拆段。
- `style`：音色目录、候选筛选、试听上下文和用户音色偏好。

## 15. wechat/memory：微信端上下文记忆

```text
wechat/memory/
  ├─ config/
  │   └─ WechatMemoryProperties.java
  ├─ fallback/
  │   └─ InMemoryWechatMemoryFallback.java
  ├─ model/
  │   ├─ ConversationTurn.java
  │   ├─ WechatConversationMemory.java
  │   └─ WechatMemorySession.java
  ├─ scheduler/
  │   └─ WechatMemoryMaintenanceScheduler.java
  └─ service/
      ├─ MySqlWechatMemoryService.java
      └─ WechatMemoryService.java
```

- `WechatMemoryService`：微信上下文记忆接口。
- `MySqlWechatMemoryService`：MySQL 持久化实现，保存消息、会话状态和工具日志。
- `InMemoryWechatMemoryFallback`：数据库不可用时的内存兜底，保证服务不因数据库故障直接停止。
- `WechatMemoryMaintenanceScheduler`：定期清理超过保留期的历史数据。

## 16. 数据库迁移

```text
src/main/resources/db/migration/
  ├─ V1__create_wechat_memory_tables.sql
  └─ V2__create_wechat_document_tables.sql
```

- `V1__create_wechat_memory_tables.sql`：微信上下文记忆、消息日志、工具调用日志表。
- `V2__create_wechat_document_tables.sql`：微信文件元数据、文档分块、文档生成记录表。

## 17. 测试目录

测试目录整体跟随主代码包结构：

```text
src/test/java/com/example/spring/cli/command/
src/test/java/com/example/spring/weather/client/
src/test/java/com/example/spring/wechat/adapter/ilink/
src/test/java/com/example/spring/wechat/bot/
src/test/java/com/example/spring/wechat/conversation/
src/test/java/com/example/spring/wechat/conversation/tools/
src/test/java/com/example/spring/wechat/document/
src/test/java/com/example/spring/wechat/image/
src/test/java/com/example/spring/wechat/image/generation/
src/test/java/com/example/spring/wechat/memory/
src/test/java/com/example/spring/wechat/voice/recognition/
src/test/java/com/example/spring/wechat/voice/style/
src/test/java/com/example/spring/wechat/voice/synthesis/
```

常用验证命令：

```powershell
mvn clean test
```
