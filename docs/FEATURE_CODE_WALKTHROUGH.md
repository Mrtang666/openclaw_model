# OpenClaw 功能代码导读与面试讲解

> 这份文档按“功能做什么 -> 真实调用链 -> 关键代码 -> 安全与边界 -> 可直接口述”的方式介绍当前项目。它描述的是仓库代码已实现的路径；标为“待补齐”的内容不能说成已经上线。

## 0. 先用一句话讲清项目

OpenClaw 是一个以 Spring Boot 为主服务的模块化 Agent 平台。它有 CLI 和微信两个主要入口，模型通过 Function Calling 调用受控工具；MySQL 保存业务事实和会话数据，Qdrant 保存知识向量，Python/Node Sidecar 隔离小红书采集和浏览器运行时。

总的代码骨架是：

```text
CLI / 微信 / HTTP 控制台
  -> Service 编排
  -> Function Calling 或领域工作流
  -> Tool / Client / Repository
  -> MySQL、Qdrant、模型、第三方 API、Sidecar
```

关键入口：`AgentClawApplication`、`cli/ConsoleRunner`、`wechat/bot/WechatBotService`、`wechat/conversation/WechatConversationService`。

---

## 1. 微信多连接、消息接收与顺序处理

### 做什么

一个服务可以管理多个微信连接。每个连接接收文本、图片、语音、视频和文件消息，再把它们交给统一会话编排层；同一聊天对象的消息串行，不同用户可并行。

### 调用链

```text
微信 iLink SDK
  -> WechatBotService
  -> WechatMessageDispatcher
  -> WechatConversationService
  -> Agent / 工具 / 记忆
  -> WechatReply
  -> WechatClient 发送回复
```

### 关键代码

- `wechat/bot/WechatBotService` 管理启动、登录、收消息和回信。
- `wechat/bot/multiclient/` 管理连接快照、登录状态和多连接生命周期。
- `wechat/bot/concurrency/ConversationKey` 用 `connectionId + userId` 标识会话。
- `wechat/bot/concurrency/WechatMessageDispatcher` 维护每会话 mailbox，保证同一会话内顺序。
- `wechat/bot/WechatReply` 用有序 `parts` 表示文字、图片、语音和文件，避免多媒体回复顺序混乱。

### 为什么这样设计

单个用户连续发送“查天气”“再换成上海”“转语音”时，后一句依赖前一句上下文；如果并发处理，后续请求可能先写入记忆或先返回。按会话串行是用少量延迟换取上下文一致性，不同用户仍然可并行。

### 当前边界

- mailbox 是 JVM 内存结构，适合单实例；多实例必须改为按会话分区的队列、Redis Stream 或持久化 ownership。
- 微信回调与最终回复发送不是一个数据库事务，需要 inbox/outbox 和业务幂等键才能做到可靠重放。
- 登录状态和连接归属也需要外置，才能安全横向扩容。

### 面试口述

“微信侧我没有把模型调用放在 SDK 回调线程里，而是先按 `connectionId + userId` 投递到 mailbox。这样同一会话是有序的，不同用户可以并发。消息最终统一编排为 `WechatReply.parts`，因此文字、截图、语音和文档可以按确定顺序发送。当前实现是单机会话串行；扩到多实例时会把会话路由和队列状态外置。”

---

## 2. 会话 Agent 与 Function Calling

### 做什么

用户不需要记住命令。模型根据工具 schema 选择天气、提醒、网页、邮件、知识库等能力，并可在一轮任务里连续调用多个工具。

### 调用链

```text
WechatConversationService
  -> WechatAgentMemoryContextBuilder + WechatRagContextService
  -> FunctionCallingAgentLoop
  -> FunctionCallingToolPlanner / DashScopeFunctionCallingClient
  -> WechatToolRegistry
  -> 对应 WechatTool
  -> tool result 回写模型
  -> 最终 WechatReply
```

### 关键代码

- `wechat/conversation/WechatConversationService` 是输入标准化、上下文构建、Agent 调用和记忆回写的编排入口。
- `wechat/conversation/agent/FunctionCallingAgentLoop` 实现“模型返回 tool_calls -> Java 执行 -> tool message 回传 -> 模型继续或结束”的标准循环。
- `wechat/conversation/tools/WechatTool` 是所有微信工具的统一接口；`WechatToolRegistry` 收集 Spring Bean 并向模型暴露 schema。
- `tool/protocol/function/` 封装 Function Calling 的 schema 转换、响应解析和 DashScope 客户端。
- `tool/protocol/legacy/` 保留旧的 prompt-json 规划模式，用于兼容与对比。

### 关键机制

- 每个工具声明名称、描述、参数和 capability；模型不能直接拼任意 HTTP 请求。
- `ToolCallValidator` 在执行前校验 required、enum、类型等 schema 约束。
- `agent.tool-calling.max-loop-rounds` 默认是 5，防止模型在工具之间无限循环。
- 工具结果以 `tool_call_id` 回传给模型，保证模型能把结果对应到正确调用。
- 语音、图片、文档和浏览器截图会以 `WechatReply.Part` 直接保留给用户；普通工具结果主要回灌模型继续推理。

### 当前边界

- 当前工具参数以 `Map<String, String>` 为主，对嵌套对象和复杂数组的类型表达较弱。
- `previous_result` 更适合线性工作流；复杂 DAG 任务应使用有名字、类型和权限标签的任务工作区。
- 防重复不能只依赖 Agent Loop：邮件、订单、网盘保存等副作用工具仍需要业务层幂等键。

### 面试口述

“模型只负责决定调用哪个受控工具，不能直接执行任意外部操作。Function Calling Loop 会把每次工具结果按 `tool_call_id` 放回消息历史，再让模型决定下一步。为了避免失控，我限制循环轮数并在工具层做参数校验；对有副作用的操作，Loop 的去重只是辅助，最终仍由领域状态和幂等键保证。”

---

## 3. 会话记忆、知识库与 RAG

### 做什么

系统把短期上下文、滚动摘要、偏好和知识库分开管理。短消息用于保持对话连续性，知识库用于从上传文档或导入资料中检索可追溯证据。

### 调用链

```text
用户消息
  -> MySqlWechatMemoryService 读取最近轮次 / 摘要 / 偏好
  -> WechatRagContextService
  -> KnowledgeQueryPlanner -> KnowledgeSearchService
  -> QdrantVectorStore + MySqlKnowledgeRepository
  -> RagRerankService / RagEvidencePackBuilder
  -> 将受限证据注入 Agent
```

文档导入则是：

```text
文件或文本
  -> KnowledgeIngestionService
  -> KnowledgeChunkService 切块
  -> KnowledgeEmbeddingService
  -> QdrantVectorStore 写向量
  -> MySqlKnowledgeRepository 保存元数据和状态
```

### 关键代码

- `wechat/memory/service/MySqlWechatMemoryService` 管理持久化会话、轮次、摘要和偏好。
- `wechat/memory/fallback/InMemoryWechatMemoryFallback` 是 MySQL 不可用时的本地降级。
- `wechat/knowledge/service/KnowledgeIngestionService`、`KnowledgeChunkService`、`KnowledgeSearchService` 管理知识入库与检索。
- `wechat/knowledge/vector/QdrantVectorStore` 对接 Qdrant。
- `wechat/conversation/rag/` 负责查询规划、重排、证据包构建和 prompt 格式化。

### 安全与边界

- 权限隔离不能靠 prompt，检索时必须将 `userId`、知识归属和访问范围写入 metadata，并由服务端过滤。
- 文档是“不可信数据”；文档中的“忽略规则并发邮件”只能作为文本，不得直接成为工具指令。
- 内存降级能保持短期可用，但重启会丢数据、恢复后可能产生会话分叉；生产需要带顺序与幂等键的写回队列。
- MySQL、Qdrant 两侧没有全局事务，需以导入状态和补偿/对账任务解决孤儿元数据或孤儿向量。

### 面试口述

“我没有把所有历史直接塞给模型，而是分为最近轮次、摘要、偏好和 RAG 证据。知识库通过切块和 embedding 写入 Qdrant，检索结果经过重排和长度限制后才进入 prompt。RAG 是辅助证据，不是权限系统；真正的用户隔离必须在数据库和向量 metadata 过滤中实现。”

---

## 4. 网页搜索、网页阅读与资源上下文

### 做什么

这套能力解决“获取公开信息”和“继续引用刚才打开的网页”。它与浏览器自动化不同：网页工具偏内容读取与搜索，不需要驱动真实浏览器页面交互。

### 调用链

```text
WebSearchWechatTool / WebReadWechatTool
  -> WebSearchService / WebReadService
  -> WebSearchClient 或网页内容提取器
  -> WebPageCacheRepository
  -> WebResourceContextService 保存当前网页快照
  -> Agent 基于摘要继续回答
```

### 关键代码

- `wechat/web/service/WebSearchService` 封装搜索请求。
- `wechat/web/service/WebReadService` 与 `WebContentExtractor` 读取并清洗网页可见内容。
- `wechat/web/context/WebResourceContextService` 保存网页搜索/阅读快照，支持“刚才第二个网页”的后续引用。
- `wechat/web/repository/MySqlWebPageCacheRepository` 缓存页面，减少重复拉取。
- `wechat/web/mcp/StreamableHttpMcpToolClient` 既被网页 MCP，也被浏览器 Sidecar 复用。

### 边界

- 搜索结果有时效性，用户问“今天、最新、价格”时不能只依赖旧 RAG 证据。
- 网页 HTML、跳转链接和提取文本都不可信，需要防 SSRF、开放重定向和间接 Prompt Injection。
- 网页缓存要设置过期时间，且不能把受限页面内容复用给其他用户。

### 面试口述

“网页搜索和浏览器自动化是两条不同能力：前者解决信息获取和内容提取，后者解决对一个受管 Chromium 的交互。网页侧会保存当前资源快照，让 Agent 能理解用户所说的‘刚才那个网页’，同时通过缓存和长度限制控制延迟与 Token。”

---

## 5. 多模态：图片、语音、视频、文件与文档生成

### 做什么

微信消息可以是图片、语音、视频或文件。系统先归档和解析输入，再把可理解的文本、引用 ID 或媒体上下文交给 Agent；输出端可返回图片、语音或生成的文档。

### 调用链

```text
图片 / 语音 / 视频 / 文件
  -> 输入解析与本地归档
  -> ImageInputResolver / VoiceRecognition / DocumentAnalysis 等服务
  -> WechatToolRequest 的媒体上下文
  -> Agent 选择理解、生成或继续处理工具
  -> WechatReply.Part.image / voice / file
```

### 关键代码

- 图片：`wechat/image/service/ImageInputResolver`、`ImageUnderstandingService`、`ImageGenerationService` 和 DashScope client。
- 语音：`wechat/voice/recognition/` 识别语音，`wechat/voice/synthesis/` 和 `VoiceStyleService` 生成语音。
- 视频：`conversation/tools/VideoUnderstandWechatTool` 将视频理解能力封装成工具。
- 文件：`wechat/document/` 负责文件存储、解析和文档生成；`DocumentAnalysisWechatTool`、`DocumentGenerationWechatTool` 暴露给 Agent。
- 统一输出：`wechat/bot/WechatReply` 的有序 part 结构。

### 关键设计

用户只发送文件却未说明任务时，文件不会被丢弃，而应存为当前会话可引用资源；下次说“分析刚才的文件”时，系统按照会话、时间和媒体类型找回。

截图、图片生成和文档生成不是只有一段文本链接：工具返回的二进制内容会被包装为媒体 part，因此发送层知道它应以图片、语音或文件发出。

### 边界

- 文件名、MIME、大小、存储路径和下载权限必须由服务端控制，不能信任用户路径。
- `replaceExistingMediaOfSameType` 类策略只适合“当前单张图”语义，不适合一轮返回多图或多文件；应使用有序资源列表。
- 模型生成的 DOCX/XLSX/HTML 需要解析或渲染测试验证可打开、字段完整，不能只检查文件非空。

### 面试口述

“多模态的关键不是单独调一个视觉或语音接口，而是把输入媒体归档为可在后续轮次引用的会话资源，再将输出统一映射为 `WechatReply.Part`。这样模型看到的是可控上下文，微信发送层看到的是明确的媒体类型和顺序。”

---

## 6. 提醒任务与微信通知

### 做什么

用户可以创建绝对时间、相对时间和重复提醒，并进行查询、修改、完成、取消、稍后提醒。到期后系统向绑定的微信收件人投递通知。

### 调用链

```text
Reminder*WechatTool
  -> ReminderService / ReminderScheduleCalculator
  -> ReminderTaskRepository + RecipientBindingRepository
  -> MySQL reminder_task / recipient binding
  -> ReminderScheduler 扫描到期任务
  -> WechatReminderNotificationSender
  -> 微信发送
```

### 关键代码

- `wechat/reminder/service/ReminderService` 实现创建、查询、更新、完成、取消和 snooze。
- `ReminderScheduleCalculator` 统一计算绝对/相对时间、重复规则和下一次执行时间。
- `wechat/reminder/scheduler/ReminderScheduler` 轮询到期任务。
- `ReminderNotificationSender` 抽象投递，`WechatReminderNotificationSender` 是微信实现。
- `MySqlReminderTaskRepository` 和 `MySqlReminderRecipientBindingRepository` 负责持久化。

### 为什么区分绝对和相对时间

“明天 8 点”是绝对时刻，“3 小时后”是相对延迟；服务端分别解析能避免模型自行换算时区或时间。数据库应保存统一时刻、用户时区和原始表达，便于审计和纠错。

### 当前边界

- 多实例下调度需要条件更新或租约抢占，确保同一到期任务只被一个实例领取。
- 投递需要以“提醒 ID + 渠道 + 收件人”作幂等键；否则抢占超时或重试会导致重复通知。
- `snooze` 应绑定已发送提醒的具体 ID，不能仅依赖“最近一条”的模糊猜测。

### 面试口述

“提醒是一个持久化任务，而不是模型记住一句话。模型只负责调用创建或修改工具；时间计算、状态转换、到期扫描和微信投递都由后端完成。生产化重点是到期任务抢占、投递幂等和失败重试。”

---

## 7. 邮件、百度网盘与文件外发

### 邮件

邮件能力分为查询、普通文本发送和附件发送，避免把读取收件箱与向外发送文件混为同一权限。

```text
EmailQueryWechatTool / EmailSendWechatTool
  -> 邮件服务与确认状态
  -> EmailClient / SmtpEmailClient 或 IMAP 查询
  -> 邮件服务器
```

关键目录是 `wechat/email/`，工具位于 `conversation/tools/Email*WechatTool`。`EmailSendWechatTool` 持有待确认发送的状态，`SmtpEmailClient` 负责 SMTP 投递。

安全要点：白名单收件人可直接发送；非白名单必须把确认 token 绑定到用户、会话、收件人、主题和附件摘要，并设置 TTL 与一次性消费。附件需做受控根目录、大小、数量、MIME 和敏感路径限制。

### 百度网盘

网盘能力包括授权、列表、搜索、保存和分享：

```text
NetdiskAuthWechatTool
  -> BaiduNetdiskAuthController / OAuth 状态
  -> NetdiskAuthorizationRepository
  -> Baidu Netdisk API
```

关键目录是 `wechat/netdisk/`。OAuth token 必须按用户隔离、加密存储、刷新和撤销；网盘路径不是权限凭据，所有操作都需要从当前用户授权上下文派生。

### 面试口述

“邮件和网盘都属于外部副作用能力，所以我把读取、写入、分享、附件外发拆成不同工具。模型可以提出动作，但收件人白名单、确认 token、文件路径白名单和授权 token 都由服务端控制。重试必须先查业务状态，避免发两封邮件或创建两个分享链接。”

---

## 8. 天气、地图、打车、外卖、新闻、物流与购物建议

### 做什么

这些是典型的“模型负责理解意图，领域服务负责真实业务规则和第三方 API 调用”的工具集合。

### 关键代码

- 天气：`weather/service/WeatherService` 和 `weather/client/AmapWeatherClient`。
- 地图：`wechat/map/service/MapService`，工具是 `MapWechatTool`。
- 打车：`wechat/taxi/service/RideOrchestrationService`、`RideOrderPollingService`，状态/支付在 `ride` 相关模型与迁移中。
- 外卖：`wechat/food/service/FoodOrderOrchestrationService`，地址由 `FoodAddressCryptoService` 加密，订单落库在 `FoodDeliveryRepository`。
- 新闻：`wechat/news/service/NewsService` 和 `NewsWechatTool`。
- 物流、购物、旅行：分别由 `LogisticsTrackWechatTool`、`ShoppingAdviceWechatTool`、`MeituanTravelWechatTool` 暴露给 Agent。

### 统一工作流

```text
用户自然语言
  -> Function Calling 选择领域工具
  -> 参数校验 / 地址或身份校验
  -> 领域 Orchestration Service
  -> 第三方服务或持久化状态
  -> 返回“查询结果 / 预览 / 待确认动作”
```

### 关键原则

- 天气、路线、新闻、物流结果都有时效性，应返回查询时间和数据来源，不把建议说成确定事实。
- 打车、外卖、支付都应先给预览，再显式确认；“模型输出了订单信息”不等于“已下单”。
- 外卖地址是敏感信息，项目使用 `FoodAddressCryptoService` 将其与一般订单字段分开处理。
- 订单、支付、创建行程等副作用必须有状态机和幂等键，不能由模型重试直接重复执行。

### 面试口述

“生活服务工具不是让模型直接对接第三方接口，而是用 `OrchestrationService` 承担地址、状态、预览和确认规则。模型只选择工具和补齐参数，真正的下单或支付必须经过领域服务的状态机和用户确认。”

---

## 9. 医疗照护协同

### 做什么

医疗模块不是诊疗模型，而是围绕患者、家属和医护人员的身份关系、记忆确认、照护计划、任务、告警和通知建立协同闭环。既可通过医疗控制台访问，也可通过 `CareAgentWechatTool` 在受限模式下发起操作。

### 调用链

```text
医疗控制台 / CareAgentWechatTool
  -> 医疗身份与授权服务
  -> 患者记忆 / 照护计划 / 照护任务 / 安全告警服务
  -> MySQL 医疗表
  -> 通知记录与微信投递
```

### 关键代码

- `medical/login/MedicalLoginSessionService` 管理医疗控制台登录 token。
- `medical/identity/` 管理患者、家属、医生等身份及授权关系。
- `medical/memory/` 管理患者记忆的提取、确认、纠正和拒绝。
- `medical/care/` 管理照护计划与照护任务。
- `medical/alert/` 管理安全告警、确认和通知。
- `frontend/medical-console/` 是独立静态管理界面；详细 API 见 `docs/CARE_BACKEND_API.md`。

### 关键状态机

患者记忆不能直接成为医疗事实：

```text
RECEIVED -> EXTRACTED -> WAITING_CONFIRMATION
                         -> VERIFIED / CORRECTED / REJECTED
```

照护计划也不应直接生效：

```text
DRAFT -> WAITING_REVIEW -> APPROVED -> ACTIVE
                                      -> PAUSED -> COMPLETED
```

### 安全与边界

- `WechatConversationMode` 只能约束 Agent 表达，不能代替后端鉴权。
- 必须同时校验角色、患者授权关系、有效期、数据范围和操作类型；纯 RBAC 不足以解决“这个医生是否能看这个患者”。
- 胸痛、呼吸困难、跌倒等高风险信息应走规则告警和紧急升级建议，不能由大模型给出诊断或保证。
- 查看、导出、修改、授权、审批、告警确认和通知都应审计。

### 面试口述

“医疗模块的核心不是让模型诊断，而是把模型提取到的信息放进可确认的工作流。患者记忆必须经历提取和确认，照护计划必须审核后才生效；权限上同时校验角色和患者授权关系。对紧急描述，系统只做安全分流、记录和通知，不替代医疗决策。”

---

## 10. 小红书舆情 Sidecar、分析、告警与报表

### 做什么

小红书模块从关键词采集公开内容，规范化后做语义分析和风险评分，生成风险事件、告警、日报和定时报表。采集运行在 Python Sidecar，主 Java 服务负责项目、授权、状态、分析、控制台和投递。

### 调用链

```text
XhsCollectWechatTool / XhsConsoleController
  -> XhsCollectionCoordinator
  -> HttpXhsSourceClient
  -> Python xhs-sidecar
  -> 采集结果 -> XhsJsonImportService
  -> MySQL 帖子、评论、任务
  -> XhsAnalysisPipeline -> XhsRiskScorer
  -> 事件 / 告警 / 日报 / 定时报表
```

### 关键代码

- 采集：`xhs/source/HttpXhsSourceClient`、`xhs/ingestion/XhsCollectionCoordinator`、`xhs-sidecar/xhs_sidecar/`。
- 规范化与隐私：`XhsJsonImportService`、`XhsAuthorKeyHasher`。
- 分析：`XhsAnalysisPipeline`、`XhsSemanticAnalyzer`、`LlmXhsSemanticAnalyzer`、`RuleBasedXhsSemanticAnalyzer`、`XhsRiskScorer`。
- 事件与告警：`XhsIncidentWorkflowRepository`、`XhsAlertService`、`XhsAlertScheduler`。
- 控制台：`XhsConsoleController`、`XhsConsoleService`、`XhsAuthorizationService`，静态页面在 `src/main/resources/static/xhs-console/`。
- 报表：`XhsDailyReportService`、`XhsDailyReportDocxService`、`XhsDailyReportXlsxService` 和 `xhs/schedule/`。

### 报表链路

```text
XhsScheduledReportDispatcher
  -> XhsScheduledReportExecutionService
  -> 采集等待 / 分析等待
  -> DOCX / XLSX 产物
  -> XhsReportArtifactStorage
  -> XhsScheduledReportDeliveryService
  -> 微信或邮件投递
```

### 安全与边界

- 作者标识使用 HMAC 伪匿名化，保留同一作者的稳定关联，同时避免直接保存原始身份。
- `AUTH_EXPIRED`、Sidecar 重启和部分关键词失败必须作为明确状态返回，不能包装成完整成功。
- 风险分数用于排序和人工复核，不是法律、公关或事实结论。
- Sidecar 的 Cookie、API Key 和采集日志必须隔离与脱敏；Python 运行时故障不应影响微信主服务。

### 面试口述

“我把采集与主服务分开：Python Sidecar 处理不稳定的采集依赖和 Cookie，Java 主服务负责项目权限、任务状态、数据入库、风险工作流和报表投递。风险评分只用于辅助排序，系统保留原始证据和人工复核入口，不把模型分数直接当结论。”

---

## 11. 动态 Skill、CLI 与目标审查

### 动态 Skill

`skills/` 目录中的 Skill 通过 `FileSystemSkillManager` 读取 `SKILL.md`、`skill.json` 和 references；`SkillMarkdownParser` 解析描述，`SkillToolMapping` 决定哪些工具及规则注入当前 Agent。

这样可以把地图出行、外卖、新闻邮件、网盘、浏览器、舆情等领域规则从 Java 常量中抽离。关键限制是 Skill 文本也应视为受控配置：加载来源、路径、版本和允许的工具映射必须校验，不能让任意用户上传 Markdown 改写系统策略。

### CLI 与 Agent Goal

CLI 入口位于 `cli/ConsoleRunner` 与 `cli/command/`，通过 `CommandDispatcher`、`CommandRegistry` 分派命令。基础 Agent 服务是 `agent/AgentService`；目标拆解与人工审查位于 `agent/goal/AgentGoalService`、`AgentGoalReviewService`、`AgentGoalReviewController`。

这部分体现同一仓库既有自然语言 Agent，也有确定性命令入口。CLI 适合运维、导入和诊断；微信适合普通用户；目标审查用于把长任务拆为可检查步骤。

### 面试口述

“Skill 不是另一个模型，而是可版本化的领域策略和工具映射。CLI 保留确定性操作入口，微信承接自然语言交互，Agent Goal 则为长任务提供可审查的步骤和人工干预点。”

---

## 12. 浏览器自动化：与其他功能的共同边界

浏览器模块的完整说明见本次面试讲解。它的真实链路为：

```text
Browser*WechatTool
  -> BrowserAutomationService
  -> BrowserMcpClient
  -> StreamableHttpMcpToolClient
  -> browser-mcp-sidecar
  -> chrome-devtools-mcp
  -> 受管 Chromium
```

关键代码在 `wechat/browser/` 和 `browser-mcp-sidecar/server.js`。它包含打开、读页、读取状态、点击、输入、等待、截图、重置共 8 个工具，默认关闭，默认仅允许访问 `localhost`、`127.0.0.1`。

必须如实说明的缺口：当前 `browser_type` 仅检查 target/text 非空，尚未真正拦截密码、验证码、银行卡和私钥；Java 会发送 Bearer API Key，但 Node Sidecar 当前没有对应的服务端验签；Chromium profile 为 Sidecar 级共享，不是按微信用户隔离。这些都是生产化前应优先修复的安全边界。

---

## 13. 配置、数据库迁移、测试与生产化重点

### 配置

`src/main/resources/application.properties` 是功能开关和外部依赖配置中心。浏览器、小红书、邮件、网盘、Qdrant 等均可条件启用。`.env.example` 提供本地变量样例；真实密钥不应提交 Git，日志需经过 `config/SecretMasker` 脱敏。

### 数据库

`src/main/resources/db/migration/` 使用 Flyway 管理迁移：微信记忆、知识库、媒体、网盘、支付、打车、提醒、外卖、医疗、Agent Goal、小红书依次演进。已发布迁移不能随意重命名或删除；旧环境必须以前向兼容迁移处理，而不是手改 Flyway history。

### 测试

- Java 单元测试：`src/test/java/`，覆盖工具、MCP 客户端、RAG、医疗、舆情、CLI 和迁移版本。
- Python Sidecar 测试：`xhs-sidecar/tests/`。
- Node Sidecar 测试：`browser-mcp-sidecar/*.test.js`。

### 生产化优先级

1. 为邮件、提醒、订单、通知、报表投递加入持久化幂等键与 outbox。
2. 外置微信连接归属、会话 mailbox、确认 token、浏览器 profile 和调度锁，支持多实例。
3. 收紧浏览器 Sidecar 鉴权、敏感输入拦截和用户隔离。
4. 将权限、审计、告警投递、模型/工具调用接入统一 trace、指标和告警。
5. 对模型、第三方 API、Sidecar 和数据库做超时、限流、熔断、重试与失败注入测试。

## 最后一句总结

面试时不要按目录逐个背类名。每个功能都用同一结构讲：**用户动作如何进入系统、模型在哪一层决策、后端在哪一层执行与持久化、外部依赖如何隔离、失败或越权时如何收敛、当前还有什么边界。** 这样能说明你理解的是工程闭环，而不是功能清单。
