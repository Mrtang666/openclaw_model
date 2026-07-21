# OpenClaw 项目结构说明

本文档用于说明当前项目的主要包结构、每个模块的职责，以及旧版 `prompt-json` 模式和新版 `function-calling` 模式的区别。

## 1. 总体流程

```text
微信 / CLI 输入
  -> 会话编排层
  -> 工具规划层
      -> legacy prompt-json 模式
      -> function-calling 模式
  -> 工具注册中心
  -> 天气 / 图片 / 语音 / 文件 / 普通聊天等工具
  -> 外部 API
  -> 返回微信或 CLI
```

## 2. 根包

```text
com.example.spring
  └─ AgentClawApplication.java
```

- `AgentClawApplication`：Spring Boot 启动类。

## 3. CLI 入口

```text
agent/
cli/
cli/command/
```

- `agent/AgentService`：CLI 默认大模型对话入口。
- `agent/ReplyEmitter`：流式输出接口。
- `cli/ConsoleRunner`：控制台输入循环。
- `cli/command/core`：命令接口、命令注册、命令分发、错误格式化。
- `cli/command/impl`：`help`、`version`、`status`、`weather`、`wechat` 等命令实现。

说明：CLI 端不再保留图片生成命令，图片生成只保留在微信端工具里。

## 4. 文本大模型

```text
chat/
  ├─ ChatClient.java
  ├─ ChatReply.java
  ├─ ChatService.java
  ├─ ChatServiceException.java
  └─ DashScopeChatClient.java
```

- `ChatService`：项目内调用文本大模型的统一入口。
- `DashScopeChatClient`：阿里百炼兼容 OpenAI Chat Completions 的客户端。

## 5. 工具协议层

```text
tool/
  ├─ AgentTool.java
  ├─ ToolRegistry.java
  ├─ WeatherTool.java
  └─ protocol/
      ├─ ConversationToolPlanner.java
      ├─ ConversationIntentDecision.java
      ├─ ConfigurableConversationToolPlanner.java
      ├─ legacy/
      │   ├─ ToolCall.java
      │   ├─ ToolPlan.java
      │   ├─ ToolCallPlanParser.java
      │   └─ ToolCallPlanner.java
      ├─ function/
      │   ├─ DashScopeFunctionCallingClient.java
      │   ├─ FunctionCallingMessage.java
      │   ├─ FunctionCallingToolCall.java
      │   ├─ FunctionCallingModelResponse.java
      │   ├─ FunctionCallingResponseParser.java
      │   ├─ FunctionCallingToolPlanner.java
      │   └─ FunctionCallingToolSchemaConverter.java
      └─ validation/
          ├─ ToolCallValidator.java
          └─ ToolCallValidationResult.java
```

- `ConversationToolPlanner`：统一工具规划接口。
- `ConfigurableConversationToolPlanner`：根据 `agent.tool-calling.mode` 切换规划模式。
- `legacy/`：旧版 JSON 规划模式。模型输出 JSON，Java 解析后执行工具。
- `function/`：新版 Function Calling 模式。模型返回标准 `tool_calls`，Java 执行工具后把 tool message 回传模型。
- `validation/`：工具调用参数校验，负责拦截缺少必填参数、枚举值非法等问题。

## 6. 微信会话编排

```text
wechat/conversation/
  ├─ WechatConversationService.java
  ├─ agent/
  │   ├─ FunctionCallingAgentLoop.java
  │   ├─ FunctionCallingAgentRequest.java
  │   └─ AgentToolExecutionResult.java
  ├─ memory/
  │   └─ WechatAgentMemoryContextBuilder.java
  ├─ intent/
  │   └─ WeatherIntentParser.java
  └─ tools/
      ├─ WechatTool.java
      ├─ WechatToolCapability.java
      ├─ WechatToolDefinition.java
      ├─ WechatToolParameter.java
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

- `WechatConversationService`：微信端核心编排层，负责处理文本、图片、语音、文件、上下文、工具调用和最终回复。
- `agent/FunctionCallingAgentLoop`：标准 Agent Function Calling 循环。
- `memory/WechatAgentMemoryContextBuilder`：把滚动摘要、最近对话、媒体记忆、工具状态拼成大模型上下文。
- `tools/WechatToolCapability`：描述工具能力边界，包括能做什么、不能做什么、缺什么要追问、输出什么。
- `tools/WechatToolRegistry`：微信端工具注册中心。

## 7. 微信平台适配

```text
wechat/adapter/
wechat/adapter/ilink/
wechat/bot/
wechat/model/
```

- `adapter/WechatClient`：微信客户端抽象接口。
- `adapter/ilink/IlinkWechatClient`：iLink SDK 适配层，负责登录、收消息、下载附件、发送文本/图片/语音/文件。
- `bot/WechatBotService`：微信 Bot 生命周期和消息处理队列。
- `model/WechatIncomingMessage`：统一封装微信文本、图片、语音、文件消息。

## 8. 领域工具能力

### 天气

```text
weather/
  ├─ client/
  ├─ model/
  └─ service/
```

- 使用高德天气 API。
- 微信端通过 `WeatherWechatTool` 暴露为工具。

### 图片

```text
wechat/image/
wechat/image/generation/
```

- `wechat/image`：图片理解。
- `wechat/image/generation`：图片生成。
- 微信端通过 `ImageGenerationWechatTool` 作为工具调用。

### 语音

```text
wechat/voice/recognition/
wechat/voice/synthesis/
wechat/voice/style/
```

- `recognition`：ASR，语音转文字。
- `synthesis`：TTS，文字转语音。
- `style`：音色候选、试听、确认、偏好保存。

### 文件

```text
wechat/document/
  ├─ model/
  └─ service/
```

- 支持文件类型检测、解析、分块、摘要、归档和文档生成。
- 微信端通过 `DocumentAnalysisWechatTool` 和 `DocumentGenerationWechatTool` 作为工具调用。

## 9. 微信记忆

```text
wechat/memory/
  ├─ config/
  ├─ fallback/
  ├─ model/
  ├─ scheduler/
  └─ service/
```

- `WechatMemoryService`：微信记忆统一接口。
- `MySqlWechatMemoryService`：MySQL 持久化实现。
- `InMemoryWechatMemoryFallback`：数据库不可用时的内存兜底。
- `WechatMemoryMaintenanceScheduler`：过期数据清理和摘要维护。
- 当前上下文保留策略由 `wechat.memory.*` 配置控制。

## 10. 配置文件

```text
src/main/resources/application.properties
.env.example
```

- `application.properties`：按 MySQL、Agent、微信记忆、天气、DashScope 文本、图片、语音、日志分组。
- `.env.example`：本地环境变量示例，不包含真实密钥。

## 11. 数据库迁移

```text
src/main/resources/db/migration/
  ├─ V1__create_wechat_memory_tables.sql
  └─ V2__create_wechat_document_tables.sql
```

- `V1`：微信记忆、消息、工具调用日志表。
- `V2`：微信文件元数据、文件分块、文档生成记录表。

## 12. 常用验证命令

```powershell
mvn -q test
```

聚焦验证 Function Calling 和工具协议：

```powershell
mvn -q "-Dtest=WechatAgentMemoryContextBuilderTests,WechatToolRegistryTests,FunctionCallingToolSchemaConverterTests,ConfigurableConversationToolPlannerTests,FunctionCallingAgentLoopTests" test
```
