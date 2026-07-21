# OpenClaw CLI / WeChat Agent

OpenClaw 是一个基于 Java 17 + Spring Boot 的智能助手项目，支持 CLI 命令行入口和微信 iLink 入口。当前项目重点是微信端 Agent：微信端可以接收文本、图片、语音、文件消息，并根据用户需求调用天气、图片生成、图片理解、语音识别、语音合成、文件解析、文档生成和大模型对话等工具。

## 当前功能

- CLI 普通对话：直接输入文本时调用阿里百炼大模型。
- CLI 基础命令：`/help`、`/version`、`/status`、`/weather`、`/wechat`。
- 微信 iLink 接入：扫码登录、接收消息、发送文本/图片/语音文件回复。
- 大模型对话：接入阿里百炼 DashScope，文本模型配置为 `qwen3.7-max-2026-06-08`。
- 天气查询：接入高德天气 API，再由大模型整理成自然回复和出行建议。
- 微信图片识别：支持微信附件图片、图片链接、data URI 图片。
- 微信文件识别与生成：支持 PDF、Word、TXT、Markdown、Excel、PPT 文件解析，也支持按用户需求生成 Word、PDF、TXT、Markdown 文档。
- 微信图片生成：图片生成能力只保留在微信端工具中，支持提示词优化、确认后生成、上下文修改图片。
- 微信语音识别：支持微信语音下载、格式检测、必要时 ffmpeg 转码，再调用 ASR。
- 微信语音合成：支持把回答文本转成语音文件发送，长文本会拆段。
- 微信音色修改：支持按“温柔女声、沉稳男声、适合讲故事”等需求筛选 `qwen3-tts-flash` 官方音色，试听后确认保存到内存偏好。
- 上下文记忆：微信端按 `fromUserId` 保存最近多轮对话。
- 多需求拆解：一句话包含多个需求时，由结构化工具计划拆分并按用户表达顺序执行。
- 工具插件化：微信端工具统一注册到 `WechatToolRegistry`，后续新增地图、新闻、日程、文件分析等工具时，不需要重写主流程。

## 整体架构

```text
用户入口层
  ├─ CLI 终端
  └─ 微信 iLink

入口适配层
  ├─ ConsoleRunner
  └─ IlinkWechatClient / WechatBotService

会话编排层
  └─ WechatConversationService
       ├─ 上下文记忆
       ├─ 多需求拆解
       ├─ 结构化工具计划
       ├─ 规则兜底路由
       └─ 有序回复组合

工具协议层
  ├─ ToolCallPlanner
  ├─ ToolCallPlanParser
  ├─ WechatTool
  └─ WechatToolRegistry

工具能力层
  ├─ ChatWechatTool
  ├─ WeatherWechatTool
  ├─ ImageGenerationWechatTool
  ├─ VoiceRecognitionWechatTool
  ├─ VoiceSynthesisWechatTool
  └─ VoiceStyleWechatTool

外部服务层
  ├─ 阿里百炼文本大模型
  ├─ 阿里百炼图片理解模型
  ├─ 阿里百炼图片生成模型
  ├─ 阿里百炼语音识别/合成模型
  ├─ 高德天气 API
  └─ 微信 iLink SDK
```

## 微信端 MySQL 上下文记忆

微信端现已使用本机 MySQL 保存上下文记忆，CLI 的内存对话逻辑保持不变。

- 同一用户 60 分钟内持续对话会复用会话；超过 60 分钟无新消息会创建新会话。
- 用户原始消息、助手回复和工具执行日志保存 30 天。
- 图片待确认提示词、待追问问题、最近天气城市等短期状态保存为会话 JSON。
- 用户明确确认的音色保存为长期偏好，重启项目后仍然生效。
- 同一微信消息 ID 重复投递时会被忽略，避免重复调用模型和工具。
- MySQL 短暂不可用时自动退回当前进程内存，服务不会因持久化失败停止。

首次运行前请创建数据库：

```sql
CREATE DATABASE IF NOT EXISTS openclaw
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;
```

然后在 `.env` 中填写 `MYSQL_URL`、`MYSQL_USERNAME` 和 `MYSQL_PASSWORD`。应用启动时 Flyway 会自动创建记忆和文件相关数据表。

更完整的协作者建库说明见 [docs/DATABASE_SETUP.md](docs/DATABASE_SETUP.md)，独立建库脚本见 [docs/sql/create_database.sql](docs/sql/create_database.sql)。

## 核心流程

### CLI 流程

```text
用户在命令行输入
  ↓
ConsoleRunner
  ↓
AgentService
  ↓
CommandDispatcher
  ├─ /help、/version、/status 本地命令
  ├─ /weather 调用天气服务
  ├─ /wechat 控制微信 Bot
  └─ 普通文本调用大模型对话
```

注意：CLI 端已经移除图片生成命令，不再提供 `/image`。

### 微信文本消息流程

```text
微信用户发送文本
  ↓
iLink SDK 拉取消息
  ↓
IlinkWechatClient 转换成 WechatIncomingMessage
  ↓
WechatBotService 发送等待提示并进入消息队列
  ↓
WechatConversationService
  ├─ 调用 ToolCallPlanner 让大模型输出 JSON 工具计划
  ├─ ToolCallPlanParser 解析计划
  ├─ WechatToolRegistry 找到对应工具
  └─ 按用户表达顺序执行工具并组合回复
  ↓
WechatBotService 按顺序发送文本、图片或语音
```

### 微信图片消息流程

```text
微信用户发送图片
  ↓
IlinkWechatClient 下载图片二进制
  ↓
ImageInputResolver 识别图片来源和格式
  ↓
DefaultImageUnderstandingService
  ↓
DashScopeImageUnderstandingClient
  ↓
先描述图片内容，再结合用户问题回答
  ↓
把图片描述写入会话上下文，支持后续“按刚才那张图修改”
```

### 微信图片生成流程

```text
用户提出图片生成/修改需求
  ↓
ToolCallPlanner 识别为 image_generation 工具
  ↓
ImageGenerationWechatTool
  ├─ 必要时先调用文本大模型优化提示词
  ├─ 如果用户要求“等我确认”，只返回优化后的提示词
  └─ 否则调用 ImageGenerationService
  ↓
DashScopeImageGenerationClient 请求阿里百炼图片模型
  ↓
ImageGenerationService 下载图片二进制
  ↓
WechatBotService 发送图片给用户
```

### 微信文件解析与文档生成流程

```text
用户发送文件或提出文档需求
  ↓
IlinkWechatClient 下载文件二进制并封装为 WechatIncomingFile
  ↓
DocumentTypeDetector 结合文件后缀、MIME 和文件头识别真实类型
  ↓
DocumentParseService 解析 PDF / DOCX / TXT / MD / XLSX / PPTX，并按片段切块
  ↓
如果用户只发送文件：WechatConversationService 先追问“你想让我怎么处理”
  ↓
如果用户提出总结、提炼、改写等需求：DocumentAnalysisWechatTool 输出摘要和关键片段
  ↓
如果用户要求生成 Word / PDF / TXT / Markdown：DocumentGenerationWechatTool 生成文件
  ↓
WechatBotService 发送文本结果或文件附件给用户
```

### 微信语音流程

```text
微信用户发送语音
  ↓
IlinkWechatClient 下载语音
  ↓
DefaultVoiceRecognitionService
  ├─ 优先使用 iLink 已带的 embeddedText
  ├─ 否则检测音频格式
  ├─ 必要时调用 ffmpeg 转 wav
  └─ 调用 DashScopeVoiceRecognitionClient
  ↓
得到文字后重新进入微信文本消息流程
```

语音合成时，`VoiceSynthesisWechatTool` 会把需要朗读的文本交给 TTS 服务，生成音频文件后由微信发送层发送给用户。如果用户已经通过音色修改工具保存过偏好，会优先使用该用户保存的音色；否则使用默认音色 `Cherry`。

音色修改时，`VoiceStyleWechatTool` 会根据用户描述筛选官方音色：如果用户说“换一个温柔的女声”，系统会展示候选音色；如果用户说“试听第一个”，系统会生成试听语音；如果用户说“就用这个”，系统会在内存中保存该用户的音色偏好。当前没有接入数据库，所以重启项目后偏好会丢失，后续可以把 `VoicePreferenceService` 替换为数据库实现。

## 主要目录职责

```text
src/main/java/com/example/spring/
  ├─ agent/                         # CLI 默认对话入口
  ├─ chat/                          # 阿里百炼文本大模型接入
  ├─ cli/                           # 控制台入口
  ├─ cli/command/core/              # CLI 命令接口、注册、分发、错误格式化
  ├─ cli/command/impl/              # CLI 命令实现：help/version/status/weather/wechat
  ├─ tool/                          # 早期简单工具封装
  ├─ tool/protocol/                 # 结构化工具调用协议
  ├─ weather/                       # 高德天气 API 能力
  └─ wechat/
      ├─ adapter/                   # 微信客户端抽象与 iLink 适配
      ├─ bot/                       # 微信 Bot 生命周期、队列、发送逻辑
      ├─ conversation/              # 微信会话编排
      ├─ conversation/tools/        # 微信插件化工具
      ├─ document/                  # 微信文件解析、分块、生成与本地归档
      ├─ image/                     # 微信图片理解
      ├─ image/generation/          # 微信端图片生成能力
      ├─ model/                     # 微信入站消息、图片、语音等模型
      └─ voice/                     # 微信语音识别、语音合成与音色偏好
```

图片生成相关文件现在都内嵌在微信包内：

```text
src/main/java/com/example/spring/wechat/image/generation/
  ├─ ImageGenerationClient.java
  ├─ ImageGenerationException.java
  ├─ client/DashScopeImageGenerationClient.java
  ├─ intent/ImageGenerationIntentParser.java
  ├─ model/ImageGenerationRequest.java
  ├─ model/ImageGenerationResult.java
  └─ service/ImageGenerationService.java
```

## 配置说明

复制 `.env.example` 为 `.env`，填写自己的 Key：

```properties
AMAP_WEATHER_KEY=你的高德 Web 服务 API KEY
DASHSCOPE_API_KEY=你的阿里百炼 API KEY
```

主要模型配置在 `src/main/resources/application.properties`：

```properties
dashscope.base-url=https://ws-6gncy95g9skiwjfi.cn-beijing.maas.aliyuncs.com/compatible-mode/v1
openclaw.dashscope.model=qwen3.7-max-2026-06-08
dashscope.vision-model=qwen3.7-plus
openclaw.dashscope.image-model=qwen-image-2.0-pro
dashscope.voice-model=qwen3-asr-flash
openclaw.dashscope.tts-model=qwen3-tts-flash
```

说明：`dashscope.voice-model` 当前用于语音识别 ASR；`qwen3-tts-flash` 是语音合成 TTS 模型，所以配置在 `openclaw.dashscope.tts-model`。

## 运行方式

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

CLI 常用输入：

```text
你是谁
/help
/version
/status
/weather 北京
/wechat start
/wechat status
/wechat stop
exit
```

## 微信端示例

```text
用户：帮我生成一张赛博朋克风格的橘猫图片
系统：先优化提示词，再调用图片生成工具，并把生成的图片发回微信
```

```text
用户：帮我查看今天杭州天气，然后用语音读一遍
系统：先调用天气工具，再调用语音合成工具发送语音文件
```

```text
用户：换一个温柔的女声
系统：调用音色修改工具，展示 3-5 个候选音色
用户：试听第一个
系统：用第一个候选音色生成试听语音
用户：就用这个
系统：保存该用户音色偏好，后续语音回复优先使用该音色
```

## 日志排查

iLink 接收日志：

```text
iLink 收到消息，messageId=..., fromUserId=..., contextToken=..., text=..., imageCount=..., voiceCount=...
```

微信 Bot 处理日志：

```text
微信收到消息，fromUserId=..., text=..., imageCount=..., voiceCount=...
微信消息进入处理队列，fromUserId=...
微信开始生成回复，fromUserId=..., text=...
微信回复发送完成，fromUserId=..., replyLength=..., hasImage=...
```

如果微信端没有回复，按顺序排查：

```text
1. 是否出现 iLink 收到消息
2. 是否出现 微信收到消息
3. 是否进入 微信消息处理队列
4. 是否出现 微信开始生成回复
5. 是否出现 微信回复发送完成
```

## 测试与构建

运行测试：

```powershell
mvn test
```

干净构建：

```powershell
mvn clean test
```
