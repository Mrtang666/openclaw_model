# OpenClaw CLI / WeChat Agent

OpenClaw 是一个基于 Java 17 + Spring Boot 的智能助手项目，支持 CLI 命令行入口和微信 iLink 入口。项目当前重点是微信端 Agent：用户可以在微信里发送文本、图片、语音、文件，系统会基于大模型 Function Calling 流程判断需求，并调用天气、地图、图片理解、图片生成、语音识别、语音合成、音色修改、文件解析、文档生成等工具完成回复。

## 1. 当前能力

### CLI 端

- 普通对话：直接输入文本时调用阿里百炼大模型。
- 基础命令：`/help`、`/version`、`/status`。
- 天气命令：`/weather 北京`。
- 微信控制命令：`/wechat start`、`/wechat status`、`/wechat stop`。
- CLI 端已移除图片生成能力，图片生成只保留在微信端工具中。

### 微信端

- 文本对话：默认由大模型接管，每条消息都会进入微信接收、上下文读取、Agent 推理和回复流程。
- Function Calling 工具调用：大模型可以按用户需求主动选择工具，工具执行结果会回传给模型继续推理。
- 多需求处理：一句话包含多个需求时，按用户表达顺序逐个处理。
- 上下文记忆：微信端使用 MySQL 保存用户、会话、消息、状态、摘要、工具日志和明确偏好。
- 天气查询：接入高德天气 API，并由大模型整理为自然语言回复和出行建议。
- 地图查询：支持地点搜索与介绍、两地驾车/公共交通/步行方案、周边美食/景点/商场推荐，并提供地图导航和票务平台搜索入口。
- 选购建议：根据商品品类、预算、用途、偏好和限制条件，提供关键参数、预算取舍、避坑项与下单检查清单，不返回具体商品链接。
- 图片理解：支持微信图片附件、图片链接、data URI 图片，先描述图片内容，再结合后续问题对话。
- 快递物流查询：支持按快递单号查询物流状态、最新位置和近期轨迹；部分快递公司可补充手机号后四位校验。
- 图片生成：支持提示词优化、确认后生成、根据上下文修改图片，并发送图片给微信用户。
- 语音识别：支持微信语音下载、格式检测、必要时 ffmpeg 转码，然后调用 ASR。
- 语音合成：支持把指定文本或上一轮回答转成语音文件发送，长文本会自动拆段。
- 音色修改：支持筛选、试听、确认音色；用户明确确认后的音色偏好会通过微信记忆服务持久化。
- 文件解析：支持 PDF、Word、TXT、Markdown、Excel、PPT 等文件解析和内容分块。
- 文档生成：支持根据用户需求生成 Word、PDF、TXT、Markdown 文档并回传。

## 2. 技术栈

- Java 17
- Spring Boot 3.4.7
- Maven
- MySQL
- Flyway
- JDBC
- 阿里百炼 DashScope
- 高德天气 / 地图 Web 服务 API
- 微信 iLink SDK
- Apache PDFBox
- Apache POI
- ffmpeg，可选，用于语音转码

## 3. 整体架构

```text
用户入口
  ├─ CLI 终端
  └─ 微信 iLink

入口适配层
  ├─ ConsoleRunner
  └─ IlinkWechatClient / WechatBotService

会话编排层
  └─ WechatConversationService
       ├─ 读取微信上下文记忆
       ├─ 处理文本 / 图片 / 语音 / 文件输入
       ├─ 构造 Function Calling 请求
       ├─ 执行 Agent 工具调用循环
       ├─ 保存消息、状态、偏好和工具日志
       └─ 组合文本、图片、语音、文件回复

Agent 工具调用层
  ├─ FunctionCallingAgentLoop
  ├─ DashScopeFunctionCallingClient
  ├─ FunctionCallingToolSchemaConverter
  ├─ ToolCallValidator
  └─ WechatToolRegistry

微信工具层
  ├─ ChatWechatTool
  ├─ WeatherWechatTool
  ├─ MapWechatTool
  ├─ ImageGenerationWechatTool
  ├─ VoiceRecognitionWechatTool
  ├─ VoiceSynthesisWechatTool
  ├─ VoiceStyleWechatTool
  ├─ DocumentAnalysisWechatTool
  └─ DocumentGenerationWechatTool

外部服务
  ├─ 阿里百炼文本大模型
  ├─ 阿里百炼图片理解模型
  ├─ 阿里百炼图片生成模型
  ├─ 阿里百炼语音识别 / 语音合成模型
  ├─ 高德天气 / 地图 Web 服务 API
  ├─ MySQL
  └─ 微信 iLink SDK
```

## 4. Function Calling 工作流

微信端默认使用标准 Function Calling Agent Loop。

```text
微信用户发送消息
  ↓
WechatBotService 接收消息并发送等待提示
  ↓
WechatConversationService 读取上下文记忆
  ↓
FunctionCallingAgentLoop 把用户消息、上下文、工具定义发送给大模型
  ↓
大模型返回 tool_calls
  ↓
Java 根据 tool_calls 执行对应 WechatTool
  ↓
工具结果作为 tool message 回传给大模型
  ↓
大模型继续判断是否需要调用下一个工具
  ↓
没有更多工具调用后，生成最终回复
  ↓
WechatBotService 按顺序发送文本、图片、语音或文件
```

当前默认配置：

```properties
AGENT_TOOL_CALLING_MODE=function-calling
AGENT_TOOL_CALLING_MAX_LOOP_ROUNDS=5
```

项目仍保留旧版 `prompt-json` 规划模式作为对比和回退，但主流程推荐使用 `function-calling`。

## 5. 微信端核心流程

### 文本消息

```text
文本消息
  → iLink 接收
  → 转换为 WechatIncomingMessage
  → 读取 MySQL 上下文
  → Function Calling Agent Loop
  → 调用聊天 / 天气 / 图片 / 语音 / 文件等工具
  → 保存用户消息、助手回复和工具日志
  → 微信发送最终结果
```

### 图片消息

```text
图片消息
  → 下载图片二进制
  → ImageInputResolver 识别图片来源和格式
  → DefaultImageUnderstandingService 调用视觉模型
  → 先描述图片内容
  → 图片描述和图片上下文进入会话记忆
  → 后续支持“把刚才那张图改成……”这类上下文修改需求
```

### 图片生成

```text
图片生成 / 图片修改需求
  → 大模型判断需要 image_generation 工具
  → ImageGenerationWechatTool 理解上下文和用户要求
  → 必要时先优化图片提示词
  → 调用 ImageGenerationService
  → DashScopeImageGenerationClient 请求图片生成接口
  → 下载生成图片
  → 微信端发送图片
```

如果用户明确说“先给我提示词，等我确认后再生成”，工具只会返回优化后的提示词，不会直接生成图片。

### 语音识别与语音合成

```text
语音消息
  → 下载微信语音
  → 优先使用 iLink 自带文本
  → 没有文本时检测音频格式
  → 必要时 ffmpeg 转码
  → 调用 ASR 得到文字
  → 文字重新进入文本 Agent 流程
```

```text
用户要求“用语音读一遍”
  → 大模型判断需要 voice_synthesis 工具
  → VoiceSynthesisWechatTool 选择要朗读的文本
  → 读取用户已保存音色偏好
  → 调用 TTS 生成音频
  → 长文本按微信发送稳定性拆分
  → 微信发送语音 / 音频文件
```

### 文件解析与文档生成

```text
用户发送文件
  → 下载文件
  → DocumentTypeDetector 根据后缀、MIME、文件头识别类型
  → DocumentParseService 解析正文、表格和段落
  → DocumentChunkService 分块
  → DocumentArchiveService 归档文件和分块信息
```

如果用户只发送文件但没有说明需求，系统会先追问“你想让我怎么处理这个文件”。如果用户提出总结、提炼、改写、生成文档等需求，则由对应工具处理。

## 6. 数据库说明

微信端使用 MySQL 保存上下文记忆，CLI 的普通对话仍保持轻量的命令行逻辑。

当前保存内容包括：

- 微信用户身份
- 会话记录
- 用户消息和助手回复
- 会话临时状态 JSON
- 用户明确偏好，例如音色
- 会话摘要
- 工具调用日志
- 文件元数据
- 文件分块内容
- 生成文档记录

首次运行前请创建数据库：

```bash
mysql -u root -p < docs/sql/create_database.sql
```

或者手动执行：

```sql
CREATE DATABASE IF NOT EXISTS openclaw
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;
```

然后在 `.env` 中配置：

```properties
MYSQL_URL=jdbc:mysql://127.0.0.1:3306/openclaw?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai
MYSQL_USERNAME=root
MYSQL_PASSWORD=你的MySQL密码
FLYWAY_ENABLED=true
```

业务表由 Flyway 自动创建，不建议手动反复执行 `V1`、`V2` 迁移脚本。

更多说明：

- [数据库初始化说明](docs/DATABASE_SETUP.md)
- [数据库建库脚本](docs/sql/create_database.sql)

## 7. 项目目录

```text
src/main/java/com/example/spring/
  ├─ AgentClawApplication.java
  ├─ agent/                         # CLI 默认大模型对话
  ├─ chat/                          # 阿里百炼文本模型客户端和服务
  ├─ cli/                           # CLI 入口
  ├─ cli/command/core/              # CLI 命令接口、注册、分发、异常格式化
  ├─ cli/command/impl/              # help / version / status / weather / wechat 命令
  ├─ exception/                     # 项目基础异常
  ├─ tool/                          # 早期通用工具封装
  ├─ tool/protocol/                 # 工具调用协议
  │   ├─ function/                  # 标准 Function Calling 协议实现
  │   ├─ legacy/                    # 旧版 prompt-json 工具规划模式
  │   └─ validation/                # 工具参数校验
  ├─ weather/                       # 高德天气 API
  └─ wechat/
      ├─ adapter/                   # 微信客户端抽象与 iLink 适配
      ├─ bot/                       # 微信 Bot 生命周期、消息队列和发送逻辑
      ├─ conversation/              # 微信会话编排
      ├─ conversation/agent/        # Function Calling Agent 循环
      ├─ conversation/intent/       # 少量辅助意图识别
      ├─ conversation/memory/       # Agent 上下文拼装
      ├─ conversation/tools/        # 微信工具定义与工具实现
      ├─ document/                  # 文件解析、分块、归档和文档生成
      ├─ image/                     # 图片理解
      ├─ image/generation/          # 微信端图片生成
      ├─ map/                       # 地点、路线、周边搜索与地图链接
      ├─ commerce/advice/           # 中立选购建议、预算与品类规则
      ├─ commerce/logistics/        # 快递物流查询、领域模型与客户端
      ├─ memory/                    # MySQL 记忆服务、兜底和清理任务
      ├─ model/                     # 微信入站消息模型
      └─ voice/                     # 语音识别、语音合成、音色管理
```

资源和文档目录：

```text
src/main/resources/
  ├─ application.properties
  └─ db/migration/
      ├─ V1__create_wechat_memory_tables.sql
      └─ V2__create_wechat_document_tables.sql

docs/
  ├─ DATABASE_SETUP.md
  ├─ DOCUMENTATION_GUIDE.md
  ├─ LOGISTICS_TRACK_TOOL.md
  ├─ MAP_TOOL.md
  ├─ PROJECT_STRUCTURE.md
  ├─ SHOPPING_ADVICE_TOOL.md
  └─ sql/create_database.sql
```

更详细的包结构说明见 [docs/PROJECT_STRUCTURE.md](docs/PROJECT_STRUCTURE.md)。

## 8. 环境配置

复制配置模板：

```powershell
copy .env.example .env
```

常用配置：

```properties
# MySQL
MYSQL_URL=jdbc:mysql://127.0.0.1:3306/openclaw?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai
MYSQL_USERNAME=root
MYSQL_PASSWORD=你的MySQL密码
FLYWAY_ENABLED=true

# Agent 模式
AGENT_TOOL_CALLING_MODE=function-calling
AGENT_TOOL_CALLING_MAX_LOOP_ROUNDS=5

# 高德天气
AMAP_WEATHER_KEY=你的高德Web服务Key

# 高德地图工具；留空时自动复用 AMAP_WEATHER_KEY
AMAP_MAP_KEY=

# 快递100物流查询
KUAIDI100_CUSTOMER=你的快递100企业接口Customer
KUAIDI100_KEY=你的快递100企业接口Key

# 阿里百炼
DASHSCOPE_API_KEY=你的DashScope API Key
DASHSCOPE_BASE_URL=https://你的百炼工作空间Host/compatible-mode/v1
DASHSCOPE_CHAT_MODEL=qwen3.7-max-2026-06-08
DASHSCOPE_VISION_MODEL=qwen3.7-plus
DASHSCOPE_IMAGE_BASE_URL=https://你的百炼工作空间Host/api/v1
DASHSCOPE_IMAGE_MODEL=qwen-image-2.0-pro
# 如果语音识别地址和 DASHSCOPE_BASE_URL 不同，再配置这一项：
# DASHSCOPE_VOICE_BASE_URL=https://你的语音识别Host/compatible-mode/v1
DASHSCOPE_VOICE_MODEL=qwen3-asr-flash
DASHSCOPE_TTS_BASE_URL=https://dashscope.aliyuncs.com/api/v1
DASHSCOPE_TTS_MODEL=qwen3-tts-flash
DASHSCOPE_TTS_VOICE=Cherry
```

说明：

- `.env` 保存真实密钥，不要提交到仓库。
- `DASHSCOPE_BASE_URL`、`DASHSCOPE_IMAGE_BASE_URL` 这类模型 Host 和 `DASHSCOPE_API_KEY` 一样属于个人配置，应放在 `.env` 中，不要写死到代码或 `application.properties`。
- `application.properties` 中只保留环境变量占位，不保存个人 Host。
- 图片生成、语音识别、语音合成分别使用独立配置项，便于后续替换模型。
- `DASHSCOPE_VOICE_BASE_URL` 可以留空，默认复用 `DASHSCOPE_BASE_URL`；如果语音识别服务地址不同，再单独填写。
- 如果本机没有 ffmpeg，可以先关闭 `AUDIO_FFMPEG_ENABLED=false`，但部分语音格式可能无法识别。

## 9. 运行方式

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

CLI 示例：

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

## 10. 微信端使用示例

```text
用户：北京今天天气怎么样，适合出门吗？
系统：调用天气工具，结合天气数据给出自然语言解释和出行建议。
```

```text
用户：从杭州东站到西湖怎么走，开车和坐地铁分别多久？
系统：调用地图工具，返回驾车和公共交通距离、预计时间、线路方案与高德导航链接。
```

```text
用户：帮我推荐西湖附近的美食和景点，景点有票的话给我购票入口。
系统：调用地图周边搜索；景点只提供第三方票务平台搜索入口，实时价格和余票以平台页面为准。
```

```text
用户：预算 500 元以内，空气炸锅应该关注哪些参数，有哪些常见坑？
系统：调用 shopping_advice，根据预算和品类提供选购指标、取舍建议、避坑项与下单检查清单。
```

```text
用户：先找西湖附近适合露营的地方，再推荐一套 800 元以内的露营装备。
系统：先调用 map_search 查询附近地点，再调用 shopping_advice，结合地点、预算和露营场景给出装备选购建议。
```

选购工具参数、能力边界和 Function Calling JSON 示例见 [选购建议工具说明](docs/SHOPPING_ADVICE_TOOL.md)。

物流工具参数、能力边界和 Function Calling JSON 示例见 [快递物流查询工具说明](docs/LOGISTICS_TRACK_TOOL.md)。

```text
用户：帮我生成一张赛博朋克风格的橘猫图片
系统：优化提示词，调用图片生成工具，并把图片发送到微信。
```

```text
用户：这张图片里有什么？
系统：调用图片理解能力，先描述图片内容，再根据用户问题继续回答。
```

```text
用户：帮我生成三个随机故事，并用语音读一遍
系统：先生成故事文本，再调用语音合成工具，把故事内容转成语音发送。
```

```text
用户：换一个温柔的女声
系统：筛选候选音色。
用户：试听第一个
系统：发送试听音频。
用户：就用这个
系统：保存该用户音色偏好，后续语音合成优先使用该音色。
```

```text
用户：发送一个 PDF 文件
系统：先解析和归档文件；如果用户没说明需求，会追问想如何处理。
用户：帮我总结这个 PDF，并生成一份 Word
系统：读取文件上下文，先总结，再生成 Word 文件并发送。
```

## 11. 新增微信工具的开发方式

项目推荐把新增能力做成微信工具，而不是直接写死在主流程里。

基本步骤：

1. 在 `wechat/conversation/tools` 下新增一个实现 `WechatTool` 的类。
2. 定义工具名称、描述、参数和能力边界。
3. 在工具内部调用具体服务，例如地图、新闻、日程、文件分析等。
4. 让工具作为 Spring Bean 被 `WechatToolRegistry` 自动收集。
5. 如果工具参数比较严格，补充参数校验和测试。
6. 更新 README 或 `docs/PROJECT_STRUCTURE.md` 中的工具说明。

工具设计原则：

- 工具只做自己负责的事情。
- 用户意图交给大模型 Function Calling 判断。
- 工具参数必须清晰，不能让工具自己猜太多。
- 工具结果要返回给 Agent，让模型结合上下文继续判断下一步。
- 媒体工具要说明能力边界，例如文件大小、格式、是否支持编辑原图等。

## 12. 日志排查

iLink 接收日志示例：

```text
iLink 收到消息，messageId=..., fromUserId=..., contextToken=..., text=..., imageCount=..., voiceCount=...
```

微信 Bot 处理日志示例：

```text
微信收到消息，fromUserId=..., text=..., imageCount=..., voiceCount=...
微信消息进入处理队列，fromUserId=...
微信开始生成回复，fromUserId=..., text=...
微信回复发送完成，fromUserId=..., replyLength=..., hasImage=...
```

如果微信端没有回复，建议按这个顺序排查：

1. 是否出现 `iLink 收到消息`。
2. 是否出现 `微信收到消息`。
3. 是否进入微信消息处理队列。
4. 是否出现 `Function Calling Agent Loop 开始`。
5. 是否有工具调用失败日志。
6. 是否出现微信发送失败日志。
7. MySQL 是否连接正常，是否触发内存兜底。

日志级别可在 `.env` 中调整：

```properties
LOGGING_LEVEL_ROOT=WARN
LOGGING_LEVEL_WECHAT=INFO
LOGGING_LEVEL_WECHAT_BOT=DEBUG
LOGGING_LEVEL_WECHAT_CONVERSATION=DEBUG
LOGGING_LEVEL_ILINK=INFO
```

## 13. 测试与构建

运行测试：

```powershell
mvn test
```

干净构建：

```powershell
mvn clean test
```

只检查补丁格式：

```powershell
git diff --check
```

## 14. 协作注意事项

- 不要提交 `.env`、真实 API Key、微信登录态和本地生成文件。
- 数据库表结构通过 Flyway 迁移维护，不要直接修改已经发布过的迁移脚本。
- 新增表结构时创建新的 `V3__xxx.sql`、`V4__xxx.sql`。
- 新增工具时优先走 `WechatTool` + `WechatToolRegistry`，不要把业务逻辑堆进 `WechatConversationService`。
- 文档统一使用中文，方便学习、汇报和团队协作。
