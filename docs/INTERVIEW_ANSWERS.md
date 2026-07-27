# OpenClaw 项目面试参考答案

> 本文与 `INTERVIEW_QUESTIONS.md` 的 1-272 题逐号对应。答案分为“当前实现事实”和“更合理的生产方案”。面试时不要背诵，应该结合代码、时序图和失败场景展开。

## 一、项目总览与架构（1-40）

1. OpenClaw 是一个 Java/Spring Boot 智能助手，入口包括 CLI 和微信 iLink。核心价值是把大模型的自然语言理解与天气、地图、知识库、文档、图片、语音等确定性工具结合，完成可执行任务。
2. CLI 便于本地调试和基础对话，微信是主要用户入口。二者复用模型与部分领域服务，但命令分发、会话记忆、消息媒体协议及回复发送不同；当前尚未统一为同一套 Agent Runtime。
3. iLink 回调被 `WechatBotService` 接收，按会话键提交给 `WechatMessageDispatcher`；`WechatConversationService` 读取 MySQL 记忆并构造请求；`FunctionCallingAgentLoop` 请求模型、校验并执行工具、回传结果；最终保存消息并由 Bot 顺序发送各个 reply part。
4. 文本直接进入 Agent；图片先下载、识别、归档，语音先取 SDK 转写或经 ffmpeg/ASR，文件先检测类型、解析和归档。它们最终都转为可进入会话的文本/结构化上下文，再进入 Agent 和统一回复模型。
5. 应按真实贡献回答并指向提交与文件。第三方部分至少包括 iLink SDK、DashScope、高德、Qdrant、PDFBox、POI、微信支付 SDK；不能把调用第三方 API 说成自研模型或自研协议。
6. 可选三个难点：多轮工具编排与失败终止；同会话有序、跨会话并发；MySQL 记忆、摘要和多种媒体引用的一致性。代价分别是硬编码终止策略、进程内状态无法横向扩展、跨存储缺少原子事务。
7. 更准确定位是功能丰富的个人项目/原型，而非成熟生产系统。依据是缺少统一认证、分布式状态、可靠消息、完整 SLO、生产监控和跨系统一致性保障。
8. 天气、地图、图片、语音、文档、知识等已有实现和测试；支付、打车、网盘等即使具备代码，也需真实凭据、沙箱/线上联调和故障闭环才能称为完整生产能力。
9. 建议保留微信 Agent Loop、持久化记忆、知识/文档工具。它们形成“自然语言入口 + 长期上下文 + 私有资料执行”的差异化闭环；普通天气查询替代性较高。
10. 优先技术债：副作用工具的幂等与确认；Web/文件安全；跨系统一致性；可观测与成本控制；拆分过大的编排类。先处理会造成资金、隐私和重复操作的风险。
11. 当前仓库能证明的是自动化测试和代码链路，不能证明线上可用性。生产证明需要端到端成功率、P50/P95/P99 延迟、工具成功率、重复执行率、每请求 token/费用及真实回归集。
12. 组件为微信/CLI入口 -> Bot/命令适配 -> Conversation/Agent -> Tool Registry -> 领域服务/外部客户端；持久层包括 MySQL、Qdrant、本地文件，外部服务包括 DashScope、高德、物流、百度网盘、微信支付等。
13. Java/Spring 适合强类型、成熟连接池/事务/监控和长期服务；Python 的 AI 生态更丰富但工程约束较弱。选择成立的前提是团队熟悉 Java，且明确接受缺少现成 Agent 框架带来的自研成本。
14. JDBC 让 SQL、批量写入和索引行为更透明，适合当前表结构；代价是映射与事务样板代码多、更新插入竞态需自行处理。复杂度增加后可考虑 jOOQ/MyBatis，而非为了省代码盲目换 JPA。
15. 当前单体便于迭代。优先保持模块化单体；当独立扩缩容、故障隔离、团队所有权或发布节奏出现明确需求时，再拆媒体处理、知识入库或异步任务，不应按目录机械拆微服务。
16. adapter 隔离微信协议；client 封装外部 API；repository 负责持久化；service 承载业务规则；tool 把能力暴露给 Agent。编排层不应拼外部请求或直接写复杂 SQL，工具也不应承担所有领域逻辑。
17. 项目有领域 record 和各 client，但仍应逐接口检查 DTO 是否泄漏。理想方案是外部 DTO 只存在 client 层，转换成内部模型，避免供应商字段变化扩散到工具和会话层。
18. `WechatTool` 使发现、schema、校验和执行统一，便于新增能力并让模型选择；直接 if/else 会让编排类与所有业务耦合，也难以做统一审计、限流和风险控制。
19. 两套流程来自 CLI 的早期轻量实现和微信端后续演进。可抽取 Channel-neutral AgentRuntime、Tool、ConversationContext，入口只负责消息协议；但媒体发送、身份和持久记忆仍应留在通道适配层。
20. function-calling 是主路径，prompt-json 是兼容/对照。只有在迁移监控证明无流量、无回退价值且测试覆盖等价后才删除，否则保留会增加两套协议维护成本。
21. 可选能力应通过条件 Bean、配置校验和 capability 状态实现，而不是任一 key 缺失就启动失败。核心依赖失败决定 readiness，非核心工具应从 registry 隐藏并报告不可用原因。
22. 单值可用 `@Value`，成组、有默认值和校验的配置应使用 `@ConfigurationProperties`。后者类型安全、可测试、可生成元数据，也避免散落的字符串 key。
23. `WechatClient` 和 factory 是替换边界，但要检查业务层是否引用 SDK 类型。若所有 SDK DTO 都在 adapter 内转换，替换成本可控；否则抽象只是表面存在。
24. Web starter 用于登录页、管理 Controller、OAuth 回调等。Spring 容器同时管理 Web Server 与 CLI runner；需要确保 stdin 退出不误停 Web 服务，并设计统一优雅关闭。
25. 多实例首先破坏进程内 mailbox、待确认操作、登录 session、Bot connection ownership 和本地文件引用。需外置状态、粘性/路由或单连接租约、分布式队列与对象存储。
26. 通道可用表示能收发；Agent 可用表示至少能生成或降级回答；数据库可用关系到持久记忆。可将能力状态细分，非核心工具失败不应让整个服务不健康。
27. 状态通常包括 stopped/starting/waiting login/running/stopping/failed。启动与停止、重复 start、SDK 回调晚到都可能竞态，应以 CAS/锁和幂等 cleanup 保证合法转换。
28. 当前页面会话服务适合轮询式状态获取。简单场景轮询最好维护；SSE 适合服务端单向推送；WebSocket 仅在双向实时控制必要时使用，避免为二维码状态引入过度复杂度。
29. Manager 维护多个 connection，每个连接对应一个微信 Bot 实例；connectionId 是本系统管理标识，userId 是消息发送者标识，bot/account 身份属于连接。三者不能混为一谈。
30. 限制应覆盖正在创建、等待扫码、运行中的占位资源，否则并发 start 可绕过。使用原子 reservation，失败或超时释放配额。
31. 若只做先查后建会竞态。应以 connectionId 唯一键或 `computeIfAbsent`/锁实现同 key 幂等，返回已有任务状态；跨实例则用数据库唯一约束或租约。
32. 是否自动重连取决于 SDK 与 Bot 实现，不能仅凭 README 宣称。生产方案需要指数退避、最大尝试、登录失效识别；重连期间入站由平台补投，出站应进入带幂等键的持久队列。
33. 当前大量连接对象和运行态在内存，进程退出不能无缝恢复。可持久化连接元数据，但微信会话凭据是否可恢复取决于 SDK；启动后应对账并明确要求重新登录的场景。
34. 平台回调通常至少一次，必须以 connectionId + sourceMessageId 建唯一索引。若没有完整去重，重复投递会重复调用模型/下单；去重记录要在执行前原子占位并保存最终结果。
35. 应有 RECEIVED/PROCESSING/SUCCEEDED/FAILED 与 attempt。当前主要依靠调度和消息入库，宕机中间态恢复不足；持久 inbox 可以重新驱动未完成任务。
36. 等待提示降低感知延迟，但必须在最终成功、失败或超时时发出明确闭环。最好使用可更新状态消息；平台不支持更新时，发简短失败回复并关联请求。
37. part 列表定义顺序，发送层串行发送。中间失败应记录每 part 状态并从失败处重试，媒体发送要有业务幂等标识；不能简单重发整组。
38. SDK 回调线程不应执行模型和文件解析，否则阻塞心跳/后续回调。项目通过 dispatcher 转交 worker；回调只做验证、轻量转换和提交。
39. 登录和管理 Controller 若无 Spring Security 就存在风险。生产应有管理员认证、细粒度授权、CSRF、防暴力请求、内网/网关限制和审计。
40. session token 应使用安全随机数、短 TTL、一次性/状态绑定，并与预期连接和浏览器上下文关联。任何通过可预测 ID 查询二维码或控制 Bot 的接口都需防枚举。

## 二、并发与会话编排（41-68）

41. key 使用 connectionId + userId，保证同一账号下同一用户消息串行，避免记忆乱序；不同用户以及不同连接可以并行。群聊还可能需要加入 conversation/chatId，而不仅是 sender。
42. CHM 保证 mailbox 映射并发访问，mailbox 锁保护队列和 running 标志的复合操作。单用线程安全集合不能原子保证“入队且只启动一个 drain”。
43. submit 获取/创建 mailbox，锁内入队；若未运行则置 running 并向 worker 提交 drain；drain 循环锁内取任务、锁外执行；队列空后置非运行并从 map 清理。
44. 若删除使用带 value 的 `remove(key, mailbox)`，且 submit 与退出对同一 mailbox 正确同步，才能避免删掉新 mailbox。必须用并发时序测试验证“判空、置状态、remove、compute”之间没有窗口。
45. 参数来自并发配置。合理依据是模型/外部 API 的并发上限、JDBC/HTTP 连接池和内存，而不是 CPU 核数；队列必须有界，峰值通过压测确定。
46. `AbortPolicy` 抛 `RejectedExecutionException`，调用方必须捕获、清理 mailbox running 状态并向用户返回繁忙，否则任务可能留在队列却无人 drain。
47. 当前可能在单 mailbox 无界积压。应限制每用户队列、合并快速连续输入、允许取消旧任务或提示排队位置；涉及副作用的消息不能任意丢弃。
48. 同一 key 后续消息被阻塞，这是保证上下文顺序的结果。长任务应异步化为 job，使会话线程只登记任务；是否允许用户发“取消”需单独的高优先级控制通道。
49. worker 并发只是入口上限，还需 semaphore/rate limiter 对每个供应商限流，并与连接池容量匹配。满载时应早拒绝或排队，而不是把压力堆到数据库。
50. 可设置每 mailbox 单次 drain 的最大任务数后重新排队，采用公平调度；并对用户设并发/速率和成本配额。
51. `close` 应停止接收、等待已执行任务、在 deadline 后取消，并明确处理队列剩余项。生产可靠方案把队列持久化，重启后继续，而非仅 `shutdownNow`。
52. 不能。分布式方案可按 conversationKey 分区到 MQ，让同 key 固定消费者顺序处理；或 Redis/DB 租约锁，但要处理锁过期导致并发执行。
53. 虚拟线程降低阻塞线程成本但不提供顺序/背压；Reactor 适合全链路非阻塞但复杂；MQ 适合可靠、跨实例和削峰；当前规模下线程池 + mailbox 最直接。
54. 压测应覆盖同 key 突发、多 key 并发、慢工具、拒绝、关闭和异常。指标包括队列长度/等待时间、活跃线程、吞吐、顺序错误、拒绝数、内存和尾延迟。
55. ConversationService 同时处理多媒体、记忆、Agent、持久化和回复装配，趋向 God Class。可拆 InputPreprocessor、ConversationOrchestrator、MemoryCoordinator、ReplyAssembler，保持流程仍可读。
56. 输入归档与消息状态可在本地 DB 事务内；外部模型和媒体调用不能持有长事务。采用短事务 + 状态机/outbox/补偿，而不是把网络请求包进数据库事务。
57. 应在模型前保存 RECEIVED 用户消息，便于故障恢复；若模型失败再记录失败状态。模型后才保存会丢失已接收事实，但需避免未完成消息进入正常上下文。
58. 不应假装一致。优先持久化回复和 outbox，再异步发送；若持久化失败可返回临时错误而不发送业务结果，或者至少记录可恢复的本地 WAL。
59. 需要 outbox：同一事务写 assistant message 和 outbound event，发送者以 eventId 幂等发送并记录每 part 状态。平台不支持幂等时，本地至少保证不主动重复。
60. 这是兼容旧调用方式的过渡模型。迁移所有构造与发送逻辑到 ordered parts、增加契约测试，确认无旧消费者后删除旧字段。
61. Agent 决定调用顺序，工具返回自身内部 part 顺序，ReplyAssembler 合并，发送层只忠实执行。业务层不应依赖并发集合的遍历顺序。
62. 会。当前逻辑更像“同类型取最后一次工具结果”，不支持多图任务。应由结果协议声明 replace/append 和 groupId，而非按媒体类型全局删除。
63. 先归档并把 documentId/current attachment 写入会话状态，回复追问；下一轮从状态恢复并交给分析工具。状态需 TTL，防止几天后误用旧文件。
64. MemoryContext 保存带序号、时间和 ID 的最近资源，语义 resolver 将指代映射为 ID。多解时列候选让用户确认，不能只取最后一个。
65. Agent 依次调用，工具结果作为 tool message 和 rollingHistory，`previous_result` 可传给下一工具。生产上应保存结构化 artifact，而不是仅传可能被截断的自然语言。
66. 只保留上一个结果无法表达多个上游依赖或回溯引用。应维护 execution context：以 callId/toolName 存结构化输出，参数用 artifact reference 指向任意结果。
67. Function Calling messages 是结构化协议，但额外 history/context 有字符串拼接。结构化消息便于角色隔离、裁剪、引用、token 预算和防注入。
68. 当前同步链路没有完善取消传播。应生成 request/jobId，保存 cancellation token；HTTP、进程和工具客户端要支持 deadline/interrupt，副作用开始后则只能查询或补偿。

## 三、Agent Loop 与工具协议（69-104）

69. 消息角色包括 system、user、assistant（含 tool_calls）和 tool（含对应 call id）。call id 让模型把并行调用结果与原请求配对，错配会破坏协议和推理。
70. assistant tool call 是模型决策，tool message 是外部执行结果；二者共同形成完整历史。遗漏 assistant 消息会让 tool result 无来源，遗漏 result 会让模型认为工具未完成。
71. 5 是成本与复杂度保护，不是正确性证明。应同时检测重复调用、总 deadline、token/费用、工具调用数，并在上限时返回已完成部分和可继续方式。
72. 当前对同轮 `toolCalls` 用 for 循环串行执行。优点是可把前一个结果带给后一个，缺点是独立查询无法并发，且模型声明的“并行”语义被改变。
73. 当前串行加 `previous_result` 可能碰巧满足依赖，但依赖不是显式的。更可靠做法是模型逐轮提出依赖调用，或计划中声明 dependsOn 并拓扑执行。
74. 会产生重复。应对 messages、recent turns、summary、rolling tool output 统一 token 预算；工具结果保存原文，给模型只放摘要和 artifact id。
75. 有风险。只应在 schema 明确允许 `previous_result` 时注入，不能覆盖显式参数；敏感结果应按数据分类限制跨工具传播。
76. 参数可修正、暂时查询失败可把结构化错误回传模型；需要用户确认、权限不足、重复副作用或确定性业务错误应立即终止并提示用户。
77. 缺少通用 control signal。工具结果应有 directive：CONTINUE、FINAL、AWAIT_USER、RETRYABLE_FAILURE、FATAL，并由 loop 统一处理，避免按工具名特判。
78. 签名由工具名、voice、目标文本规范化组成，主要合并空白；标点和语义等价改写仍会视为不同。可对副作用调用使用 request id，而非模糊文本哈希。
79. 这是关键缺口。图片生成涉及费用，保存/下单/支付有副作用；应使用 toolCallId + conversation requestId + 业务参数哈希的幂等记录，并在执行前原子占位。
80. `Map.toString()` 取决于 Map 类型/迭代顺序，不应作稳定签名。应按 key 排序后做 canonical JSON，再加工具版本并哈希。
81. 不存在工具返回确定错误；缺字段/错类型由 validator 拒绝；多余字段是否拒绝取决于 schema。错误应结构化回传，允许模型修正一次，同时限制重复失败。
82. `finalReply` 应对空 content 提供兜底；若实现未处理，可能返回空文本。正确做法是协议校验并返回“模型未生成有效回复”，同时记录异常指标。
83. client 应区分网络超时、限流、服务错误、协议错误。只对可重试错误做带 jitter 的指数退避并遵守 Retry-After；非法响应应快速失败和留脱敏样本。
84. 设置每请求总 token、模型轮数、工具次数、工具类型费用、用户日配额和全局预算。副作用/高费用工具还需确认与 price preview。
85. 为请求生成 traceId，记录每轮模型、tool call id、规范化参数、状态、耗时、token、费用和错误；prompt/结果需脱敏并按权限访问。
86. Java 常量难以独立版本、灰度和回滚。应使用带版本的 prompt 资源/配置中心，把 promptVersion 写入 trace，并通过离线评测和小流量实验发布。
87. system 优先级不是安全边界。工具权限必须由 Java 侧白名单、参数校验、用户授权和确认保证；绝不允许模型直接决定任意 URL/文件/shell 权限。
88. 工具内容标记为不可信数据，使用清晰分隔和最小必要片段；对 URL/文档做注入检测只能辅助，真正防线是模型无权绕过的执行策略。
89. 工具声明风险等级，loop 遇到确认型调用只创建 pending action 和摘要，不执行；用户明确确认后以不可篡改 actionId 执行一次，并设 TTL 和参数回显。
90. 规划器可显式表达依赖、并行和预算，执行器更可控；代价是计划可能错误、延迟更高。简单任务保留 loop，复杂多步骤任务才使用计划模式。
91. 工具需提供名称、描述/schema 和 execute；Spring 注入的工具集合由 registry 建索引和 definitions。新增后必须有 schema/实现一致性测试。
92. 安全选择是启动失败并明确列出重复名，不能静默覆盖，否则模型看到的 schema 和实际执行者可能不一致。
93. 最好从强类型参数类生成 schema，并由同一反序列化器执行；至少增加契约测试，遍历每个 definition 的 required/type 与工具解析逻辑。
94. 字符串 Map 丢失类型和嵌套结构，解析失败常被静默回退。使用 `JsonNode` 或 typed record + Jackson validation，兼容期可把旧字符串 adapter 放在边界。
95. 应核对当前 validator 代码，不能宣称完整 JSON Schema。生产至少支持 object/property type、required、enum、范围、长度、数组项和禁止未知字段。
96. 独立校验提供统一安全边界、可观测错误和零副作用保证。工具自行校验容易漏项、错误格式不一致，甚至先执行部分动作再发现参数错误。
97. 工具越多越容易选错且增加 token。按 capability/意图预筛选，常用核心工具常驻；筛选器应高召回，低置信度时扩大集合而不是强行排除。
98. 可以动态裁剪，但不能用同一个不可靠模型做不可恢复的硬裁剪。规则 + 轻量分类器召回候选，保留 chat/fallback，并监测“未知工具需求”。
99. modelText 给模型继续推理；visibleParts 给微信用户；status 控制流程和指标；errorMessage 供诊断。分离可避免把二进制路径或内部异常直接暴露给模型/用户。
100. 模型需要紧凑、结构化事实，用户需要自然语言和媒体；两者混用会泄漏内部信息、浪费 token，并让模型重复展示已经发送的媒体。
101. 为工具增加 READ_ONLY/WRITE/FINANCIAL、idempotency、confirmation、costClass。策略引擎在执行前检查，而不是依赖 prompt 自觉。
102. 当前需逐 client 检查超时，不能假设统一。应由 Agent deadline 派生每工具 timeout，通过 Future/HTTP cancellation 终止；超时不能只返回而让后台继续副作用。
103. 查询类可有限重试；生成图片需先查询原任务；保存/支付/下单只能带幂等键重试；参数和权限错误不重试。
104. 每供应商独立连接池、semaphore、rate limiter 和 circuit breaker；熔断时从 registry 暂时降级或返回可解释错误，避免一个服务耗尽全局 worker。

## 四、记忆、数据库与 RAG（105-148）

105. 近期轮次保持对话连贯；state 保存待确认与最近资源；摘要压缩长期上下文；明确偏好保存稳定选择；工具日志用于审计和恢复。它们保留期、可信度和注入 prompt 的方式应不同。
106. 两线程都可能查不到后插入。代码依靠 wechat_user_id 唯一约束，使一个 insert 抛 `DuplicateKeyException`，再查询已插入 ID；数据库唯一约束才是最终正确性边界。
107. 有可能，因为“查 active 再 insert”若无唯一约束/锁可并发创建两个。可增加用户+通道的 active 唯一性设计、事务锁或单独 current_session 表并条件更新。
108. 摘要是关闭会话后跨会话记忆来源，失败就关闭会丢上下文。一直不关闭则积累 active 会话和反复重试；应记录 summary failed、退避、达到阈值后降级关闭并保留原文。
109. `min(userCount, assistantCount)` 近似完整轮次。连续用户消息、缺失回复会低估，工具消息被忽略。更准确是在消息上记录 turnId/requestId 和 completion 状态。
110. 它只在 USER 后遇到 ASSISTANT 时形成 turn；连续 USER 会覆盖前一条，孤立 ASSISTANT 被忽略。应按 request/parent message 关联，而不是靠行序启发式配对。
111. 会。摘要是有损且模型可能添加事实。应要求引用 messageId、抽取结构化事实、置信度和用户确认；关键偏好只从用户明确表达写入，并支持纠正。
112. 当前需检查是否有完整的查询/删除入口；生产必须提供记忆列表、单项纠正、全部删除和导出，并同步清理消息、摘要、向量、文件与备份生命周期。
113. 只取最新摘要实现简单、token 可控，但可能带入无关内容并漏掉更早相关事实。可将摘要/事实 embedding 后按当前 query 检索，同时保留最近会话摘要。
114. 建立优先规则：本轮明确指令 > 最近明确表达 > 已确认偏好 > 摘要推断。冲突应提示用户确认，并用时间/version 更新旧偏好。
115. 降级保证临时可答，但产生“本轮记住、重启丢失”及多实例不一致。回复应提示记忆暂不可持久化，高风险操作不能在 fallback 中继续。
116. 当前 fallback 通常不会自动回灌，所以恢复后会形成记忆缺口。可将失败写入本地/持久重试队列并重放，或明确不承诺降级期间的持久记忆。
117. 用户、会话、消息、状态、工具日志之间可能部分成功。对单次本地状态变更使用 `@Transactional`；外部调用放在事务外，通过状态和 outbox 衔接。
118. update 后 insert 仍会并发冲突。MySQL 用 `INSERT ... ON DUPLICATE KEY UPDATE`，前提是 `(user_id, preference_key)` 唯一索引；同时定义版本/更新时间覆盖语义。
119. `expires_at` 只是标记，需 scheduler 分批按索引删除。按主键范围小批提交、避开高峰，必要时分区表按日期 drop，避免一次大 delete 产生长事务。
120. 至少凭据/token 应应用层加密；高敏正文可字段/磁盘加密并严格授权。数据库加密不防有查询权限的管理员，需 KMS、审计和最小权限。
121. 写日志前做字段级分类与 redaction；工具结果只保存必要摘要，token/支付签名永不进入 prompt 和普通日志；对历史数据设置清理与扫描任务。
122. 需按 user/conversation/created_at 建复合索引，冷热分层、时间分区和归档；深分页用 keyset，摘要/工具日志分表。容量设计以实际查询计划和增长率验证。
123. 会重复摘要、重复清理甚至竞态关闭。使用 ShedLock/DB lease 或任务分片；写入摘要还应有 `(conversation_id, covered_message_id)` 唯一键实现幂等。
124. 构造多轮对话集，标注应保留事实、禁止保留内容、更新/冲突/删除。测事实召回、错误记忆、过期信息、跨用户泄漏和 token 成本。
125. V1 是会话记忆，V2 文档，V3 图片，V4 知识库，V5 Web，V6 网盘，V7 支付，V8 打车位置确认，V9 打车/行程表。它反映从聊天向可执行工具和交易扩展。
126. Flyway 提供有序、可审计 schema 演进。小型应用可启动迁移；生产更稳妥是发布流水线独立迁移、权限分离，应用账号不持有 DDL 权限。
127. 已执行 migration 的 checksum 已记录，修改会导致环境不一致。修复应新增更高版本迁移；仅在明确纠正开发环境且可重建时 repair。
128. utf8mb4 支持完整 Unicode/emoji，适合微信消息。collation 决定大小写、重音和排序等价性，会影响唯一键与搜索，选型需保持各表/连接一致。
129. 应以 migration 为准逐表回答。外键保证引用一致性但增加写入/迁移耦合；不用外键则必须由应用、对账和清理任务保证，不能只说“性能”。
130. 需定义法律保留例外。一般先禁用用户，再异步删除/匿名化消息摘要、文档、向量和文件；支付审计可能需按法规保留但去标识化。
131. 不能原子提交。先写 staging 文件再事务写元数据并原子 rename，失败清理；或用对象存储上传状态机和定期 orphan reconciliation。
132. 使用 saga：先建 PENDING 元数据，写对象/向量，成功改 ACTIVE；失败记录步骤并重试/补偿删除。所有外部写带稳定 documentId/chunkId 幂等。
133. 状态机条件更新最重要：`UPDATE ... WHERE status=EXPECTED AND version=?`。竞争激烈才用短悲观锁；外部网络调用不能持锁。
134. 定期全量+增量备份并做恢复演练；migration 遵循向后兼容的 expand/migrate/contract，两版本应用均可读写过渡 schema。
135. 输入被规范化并记录文档元数据，切成 chunk；调用 DashScope embedding；向量和用户/document payload 写 Qdrant；查询时 embedding query、按用户过滤 topK，再把命中片段与来源交给模型。
136. 固定长度简单稳定，段落保持语义，重叠避免边界信息丢失但增加成本和重复召回。较好方案按标题/段落切，再用 token 上限二次拆分。
137. 可能。必须使用目标模型 tokenizer 或保守 token 估算，按字数只能近似；超限时递归拆分并记录失败。
138. collection 和文档记录 `embedding_model/version/dimension`。新模型建新 collection 或双写回填，完成后切读，不能把不同语义空间混在一起比较。
139. dimension 必须匹配模型；语义 embedding 常用 cosine，需以模型文档为准。payload 至少有 tenant/user、document、chunk、source、version、ACL，查询强制过滤用户。
140. 每个向量写 user/tenant payload，server 从认证上下文注入 filter，绝不接受模型/用户直接指定其他 userId；还要做随机跨用户隔离测试。
141. topK 由评测确定。向量擅长语义，BM25 擅长精确词/编号；混合召回后 rerank 通常更稳，最终片段数受 token 与最低分约束。
142. 阈值与模型/归一化有关，不存在通用值。用有答案/无答案验证集画 precision-recall，按误答风险选阈值，低分时明确拒答或询问。
143. 按 rerank 分数选片段，合并同文档相邻 chunk、内容 hash 去重，先分配总 token budget，再保留来源元数据和必要上下文。
144. chunk 保存 documentId、页码/标题/字符区间/URL。回答只允许引用已检索 source id，渲染层将 source id 转为可点击来源，并校验不存在的引用。
145. 删除进入 DELETING 状态，以 documentId 删除 Qdrant payload、对象文件和 MySQL 子表，全部成功后 DELETED；后台对账重试残留。
146. 进程重启会丢，多实例各有不同 pending 状态，map 还可能过期泄漏。应存数据库/Redis，actionId 唯一、带 user/参数哈希/TTL/状态，确认时原子消费。
147. 文档内容按“不可信引用材料”隔离，模型提示不得执行其中指令；工具权限仍由策略层限制。高风险文档可做检测，但不能把检测器当唯一防线。
148. 用标注 query-document-answer 集测 Recall@K/MRR、答案忠实度、相关性、引用命中和拒答率；模型评审需结合人工抽检，不能只用 LLM 自评。

## 五、网页、多媒体与业务工具（149-194）

149. MCP 统一工具发现/调用并便于替换服务，但增加协议、会话与排障层。单一稳定 API 用 REST 更简单；多工具生态或独立服务才体现 MCP 价值。
150. 客户端向 endpoint 初始化/发现工具，按协议发送 call 并解析 streamable response。生产需连接/读取 timeout、会话重建、幂等 call id 和断线后的有限重试。
151. 摘要是搜索引擎二手截断信息。当前 system prompt 要求严谨任务搜索后再读 1-3 个页面；最终结论仍需绑定真实抓取来源。
152. Jsoup 可按正文候选、文本密度和常见 article/main 标签提取并移除 script/nav。JS 动态页需浏览器渲染服务或供应商正文 API，但成本和安全更高。
153. HTTP client 限制重定向次数、压缩后字节、content type 和总时间；从 header/BOM/meta 识别 charset。非 HTML 按允许类型交给文档解析，否则拒绝。
154. 存在典型 SSRF 风险。解析 DNS 后拒绝 loopback/private/link-local/metadata，连接时固定已验证 IP，每次重定向重新验证，并限制协议、端口和响应大小；还要防 DNS rebinding。
155. 去 fragment、规范 scheme/host/default port，是否保留 query 取决于语义，不能随意排序有签名的 query。缓存最终 URL 与原 URL 映射，并按用户/权限分区。
156. TTL 应按来源 Cache-Control、内容类型和业务新鲜度；新闻短、文档长。支持条件请求 ETag/Last-Modified，并允许用户要求刷新。
157. 先做分段/摘要或按查询检索相关段，而不是只取开头 3500 字；同时向模型明确内容不完整，避免过度结论。
158. 尊重 robots/服务条款、限速和版权，仅缓存必要片段与元数据。登录墙内容不能绕过授权，私人页面需用户明确授权且隔离凭据。
159. 搜索/阅读工具返回稳定 sourceId、URL、title、抓取时间；模型只能引用 sourceId，最终渲染从结构化 source map 生成，杜绝自由编造 URL。
160. 降级可换备用 provider、直接读取用户给定 URL、使用缓存或明确说明暂不能检索；不能把模型旧知识伪装成实时搜索结果。
161. 附件可能是恶意文件，URL 有 SSRF，data URI 可造成内存膨胀。统一进入受限 resolver：白名单类型、magic bytes、大小/像素、timeout 和隔离存储。
162. 扩展名/MIME 可伪造，magic bytes 更可信但仍非完整安全检测。综合文件头、解析器实际结果、扩展名一致性，冲突时拒绝或隔离。
163. 流式下载并设连接/读取/总 timeout、最大字节和 redirect 次数；先检查 Content-Length 但不信任它，超过限制立即中止并删除临时文件。
164. 设置压缩展开比、页/行/单元格/像素/解析时间限制，在隔离 worker/容器中解析，禁用宏与外部实体，OOM/超时只影响任务进程。
165. 归档生成 immutable imageId，记忆保存最近图片列表及来源消息；后续指代解析返回 imageId，工具通过 ID 获取，不依赖临时 URL。
166. 具体 client 需按代码回答；生产更适合提交异步任务并轮询/回调。会话只保存 generationTaskId，完成后下载一次并主动通知。
167. 保存供应商 taskId 和结果 URL；下载失败应在 URL 有效期内重试下载，不能重新发起生成。必要时把结果立即转存对象存储。
168. SDK 文本免去下载/转码和 ASR 成本，延迟更低；但要记录来源与置信度，低置信度或用户质疑时可重新 ASR。
169. `ProcessBuilder` 参数数组避免 shell 拼接；文件路径由系统生成。超时后先 destroy，再强制杀死，并清理 stdout/stderr 读取线程和临时文件。
170. 使用 try/finally 或资源对象在所有成功、异常、取消路径删除；持久归档与临时文件分目录，后台扫描过期孤儿文件。
171. 按句号/语义段落且受模型字符上限切分，固定 voice/参数；生成 part 编号并串行发送。可在句间添加自然停顿并限制最大总长度。
172. preview 后只允许同一用户确认同一个 voiceId，状态含 createdAt/TTL。新预览覆盖旧操作；确认使用 actionId，防止“是”误确认其他会话动作。
173. 解析器对象和 workbook/document 一般应每次请求局部创建，所有 stream/document 用 try-with-resources。不要共享可变 parser；并发安全需依据库文档和压力测试。
174. 扫描 PDF 只有图像，需要 OCR。先检测文本密度，按页 OCR，限制页数/像素/成本并提示用户；OCR 结果标记可能有误。
175. 使用嵌入/可用中文字体、明确页面尺寸和表格列宽；渲染到图片/PDF做视觉回归。仅“文件能打开”不能证明排版正确。
176. 本地磁盘不共享且容器易失。持久内容迁到带加密、生命周期和签名 URL 的对象存储；本地只作有限临时缓存。
177. 模型猜错地点会导致路线、打车等真实后果。候选需要展示城市/区域/地址并由用户选定，确认后保存 POI id/坐标而非模糊名称。
178. 高德通常使用 GCJ-02；GPS/其他供应商可能 WGS84。混用会产生数百米偏移，转换和模型字段应显式标记 coordinate system。
179. 以具体 MapService 实现和 API 文档为准。多点最短路若本地实现可用 TSP 启发式；有时间窗则是 VRP，不能宣称简单排序是最优解。
180. 参数需 UTF-8 URL 编码，签名按供应商规则对规范参数计算。key 不应出现在客户端可见日志；导航链接若必须带 key 要评估泄漏。
181. 快递公司用后四位验证隐私。只在调用时使用，不写普通日志、摘要或模型上下文；展示时掩码，并限制尝试次数。
182. 签名通常由 customer/key/param 按文档拼接哈希，具体以 client 为准。排查记录脱敏 canonical string、charset、时间和响应码，不能打印 key。
183. `NetdiskTokenCryptoService` 应用 AEAD（如 AES-GCM）并保存 keyVersion/nonce/tag。轮换时新写用新 key，读兼容旧 key，后台逐步重加密。
184. state 是一次性随机值，绑定 user/session/redirect intent 且短期有效，回调原子消费。redirect URI 使用服务器白名单；refresh token 刷新按用户加锁/CAS，避免旧 token 覆盖新 token。
185. 官方 API 负责真实 OAuth/数据操作，MCP 可提供统一工具封装。MCP 是额外信任边界，需 TLS、认证、工具白名单、参数校验和最小 scope。
186. 只申请所需 scope；保存/分享前回显目标路径、文件和有效期并确认；记录 user、actionId、参数 hash、供应商 request id 和结果。
187. 用显式状态机：DRAFT/LOCATION_PENDING/QUOTED/CONFIRM_PENDING/CREATING/CREATED/CANCELLED/FAILED。每次操作条件更新 expected status，非法跳转返回冲突并重新查询。
188. taxi 是对话式阶段，继续模型循环可能重复查点/报价/下单，所以直接结束。问题是 loop 依赖工具名；应由结果返回 `AWAIT_USER`/`FINAL` directive。
189. 超时属于未知结果，不能直接重下。用客户端幂等 requestId 调供应商，随后按 requestId/订单查询对账；无法确认时进入 UNKNOWN 并人工/定时核对。
190. 创建本地订单并生成预支付；回调验平台证书签名、解密并条件更新；前端/用户查询以服务端支付状态为准；退款建立独立请求和回调/轮询状态。
191. 验签还要验证商户号、appid、金额和订单号。以 transactionId/outTradeNo 唯一，状态只允许合法单向迁移；重复回调返回成功，乱序用最终态规则处理。
192. double 是二进制浮点，会产生金额舍入误差。存整数分最简单；涉及税率/汇率用 BigDecimal 并显式 rounding mode。
193. 订单创建以 user + business requestId 唯一；按钮/确认携带 actionId 且只能消费一次。消息失败后重新展示同一订单，不创建新订单。
194. 登录、授权、知识删除、网盘写入、订单/支付/退款必须审计。审计采用追加写、独立权限和防篡改存储/哈希链，业务更新不能覆盖历史。

## 六、安全与隐私（195-206）

195. `EnvFileLoader` 在 Spring 配置解析早期加载 `.env`，`.gitignore` 排除真实文件；仍需 secret scanning、部署 secret manager 和日志脱敏。不能把“不提交”当完整密钥管理。
196. `SecretMasker` 只覆盖已知模式，无法自动识别所有嵌套/编码形式。日志应从源头不记录敏感字段，并对 URI、headers、JSON 使用结构化 allowlist，而非事后正则兜底。
197. preview 仍可能包含手机号、地址、token 或健康信息。生产默认不记正文，只记长度/hash/分类；受控 debug 采样需脱敏、短保留和访问审计。
198. 正常入口依赖 SDK 已认证通道，但服务端仍应验证 callback 来源/签名，并把 userId 从可信上下文取得，不能接受 HTTP 参数覆盖。
199. 若未引入 Spring Security，就是明显生产缺口。管理与 OAuth 入口应认证授权，状态改变用 CSRF 防护/同站 cookie，所有接口有速率限制与审计。
200. 文件名只作展示元数据，实际路径使用随机 ID；`resolve(...).normalize()` 后验证仍在允许根目录，拒绝绝对路径、`..`、特殊设备名和符号链接逃逸。
201. 上传与生成文件要限制类型、移除/拒绝宏、恶意软件扫描，使用 `Content-Disposition: attachment`；复杂解析在隔离环境，不能信任“由模型生成”就是安全的。
202. 工具按 capability allowlist，URL/路径经过专用 resolver；应用不暴露通用 shell/任意文件读取。即使模型要求，也必须经过代码策略和用户授权。
203. 模型会看到用户输入、选取的历史和工具结果。需要隐私声明、数据最小化、敏感字段脱敏、供应商数据处理协议；高敏场景可禁用云模型或要求明确同意。
204. 以用户身份汇总 MySQL、Qdrant、对象存储和审计索引；导出生成受保护临时文件；删除用异步任务和对账，备份按到期策略自然清除并记录完成证明。
205. 服务端通常共享 key，但配额按 user/tenant 计数。用网关/应用 rate limit、费用 ledger、日限额和异常检测；允许自带 key 时必须加密隔离。
206. 锁定依赖版本，CI 跑 SCA/CVE、SBOM 和许可证检查；私有/本地 SDK 固定来源与 checksum，ffmpeg 使用受维护镜像并及时修补。

## 七、异常、可观测性与成本（207-218）

207. 至少分 Validation/UserActionRequired、Unauthorized、DependencyTransient、DependencyPermanent、Internal。分类决定用户文案、HTTP/工具状态、是否重试、告警等级和是否计入供应商 SLA。
208. 网络瞬断、429、部分 5xx可重试；参数、认证、额度耗尽不重试；副作用必须有幂等键才重试。指数退避加随机 jitter，受总 deadline 和 Retry-After 限制。
209. 入站创建 traceId/requestId，放 MDC 和 OpenTelemetry context，传到 executor 需显式传播；外部 HTTP 加 trace header，工具日志、消息、订单/outbox 都保存关联 ID。
210. 记录入站量、队列等待/深度、端到端和分阶段延迟、模型 token/首响应/错误、各工具成功率/限流、数据库池、媒体字节、用户费用和重复副作用数。
211. trace span 分为 dispatcher wait、memory load、每轮 LLM、每个 tool、DB commit、每个 part send；看最长 span 与资源饱和指标，而不是只靠一条总耗时日志。
212. 结构化日志只记 ID、状态、耗时和错误码；正文/参数按 allowlist 脱敏。敏感 debug 单独权限、采样、短 TTL，审计谁访问过。
213. 按用户影响告警：端到端成功率/SLO burn rate、队列持续增长、支付验签失败等。使用时间窗口、聚合、去重和供应商维护抑制，避免每个 5xx 一条告警。
214. liveness 只表示进程未死，不依赖外部服务；readiness 表示能否接流量，应检查关键线程池/通道和核心依赖。非核心工具状态单独暴露 capability health。
215. 连接 timeout 防握手卡住，读取 timeout 防无数据，总 deadline 控制整个请求；还需连接池获取 timeout、DNS 和写 timeout。各阶段之和必须不超过用户请求 deadline。
216. 在 request cost context 中累加 prompt/completion token、模型单价、工具固定/按量费用和媒体存储流量，最终写 cost ledger，按 user/tool/model/version 聚合。
217. 执行前原子预占预算，完成后结算；用户/租户/全局多级配额。配置中心提供 kill switch，registry 可立即隐藏或拒绝高成本工具。
218. 本地命令、缓存和部分数据库功能仍可用；模型依赖功能应明确“智能服务暂不可用”，可提供确定性命令/查询降级。不得静默换成不可靠假答案。

## 八、测试与代码质量（219-234）

219. 仓库同时有纯单元、Mock HTTP/client 和数据库相关测试。是否真实访问服务应依据各测试 profile/guard 回答；默认 CI 不应依赖真实第三方账号。
220. Guard 防止测试连接非测试库并执行破坏操作。可靠 guard 应同时验证 profile、数据库名白名单、host、显式 opt-in；最安全仍是 Testcontainers 每次创建临时库。
221. 注入 fake `DashScopeFunctionCallingClient`，按调用轮次返回预设 response；注入 fake registry/tool，断言完整 message 序列、执行次数、结果和终止条件。
222. 覆盖最终文本、单/多工具、多轮、未知工具、schema 错误、失败后修正、重复语音/副作用、最大轮数、空 response、媒体合并、AWAIT_USER 和超时。
223. 使用 CountDownLatch、CyclicBarrier、CompletableFuture 和可控 fake executor/service；等待明确条件并设短超时，不靠固定 sleep 猜调度完成。
224. 同 key 任务记录 start/end 序列并用 latch 阻塞第一项，断言第二项未开始；不同 key 同时到达 barrier，证明重叠执行。循环多次放大竞态并检查不丢不重。
225. H2 与 MySQL 在 upsert、锁、时间、JSON、索引和字符规则上不同。Testcontainers 运行真实 MySQL，能验证 migration 与并发语义，代价是测试较慢。
226. CI 至少测试空库 migrate 到 latest；再准备每个受支持历史版本快照逐级 migrate，校验关键数据未丢、约束和索引存在，重复启动 migration 幂等。
227. 用 MockWebServer/WireMock 编排响应和延迟，断言分类、次数、退避、deadline、连接释放和脱敏日志；契约测试再少量验证真实供应商格式。
228. 空文件、伪扩展名、超大文件、压缩炸弹、损坏 PDF、加密文档、宏、公式、超多行/页、路径穿越名、中文/emoji、扫描 PDF 和解析超时。
229. 断言用户过滤、命中文档/chunk、排序、阈值拒答、引用映射、删除后不可检索、模型版本隔离，以及 Recall@K/忠实度基线。
230. 固定真实业务输入和期望工具/参数/关键事实；离线批跑新 prompt/model，多次采样，比较完成率、错误副作用、token/费用和延迟，未过阈值不得全量切换。
231. 使用 SDK adapter fake 做大部分 E2E；少量真实沙箱账号在隔离群/白名单用户运行，凭据放 secret manager，禁止向真实联系人发消息。
232. 需实际运行覆盖率工具才能给数字，不能编造。优先看支付/打车幂等、dispatcher 竞态、Agent 终止、SSRF 和跨存储补偿，分支覆盖比总行覆盖更有意义。
233. 当前 pom 主要是构建和测试，未见完整质量门禁。可加入 SpotBugs/Checkstyle或Error Prone、formatter、OWASP dependency check/Dependabot、SBOM 和许可证扫描。
234. 静默 fallback 会把模型错误参数变成看似正常结果，例如页数变默认值。应返回明确 validation error 给模型修正，只有真正 optional 且缺省时才使用默认值。

## 九、部署与扩展（235-246）

235. Maven 构建 Spring Boot jar，运行需 Java 17、MySQL、可选 Qdrant/ffmpeg、外部 API 凭据和数据目录。启动文档还要求 iLink SDK 依赖可解析。
236. 本机绝对路径不可复现，CI 和其他开发者无法构建。应将合法依赖发布到私有 Maven 仓库/GitHub Packages，或作为固定 commit 的模块并验证许可证。
237. 多阶段构建产出 jar，运行镜像用精简 JRE 17；按需安装固定版本 ffmpeg 和中文字体，非 root 用户、只读根文件系统、临时目录限额，并生成 SBOM。
238. 容器重建会丢本地数据，多实例也看不到彼此文件。持久文件用对象存储，数据库保存元数据；临时目录可丢且有 size/TTL 限制。
239. 会话/确认放 DB/Redis，任务放分区 MQ，Bot 连接用 ownership lease 和路由，本地文件迁对象存储，定时任务加 leader/分布式锁。不能只加负载均衡器。
240. readiness 先摘流，停止接收新消息，等待 mailbox/Agent 到 deadline；持久任务释放 lease 由新实例接管。schema 和消息格式需前后兼容。
241. 使用 profiles 配非敏感项，Vault/KMS/Kubernetes Secrets 管密钥；环境间完全隔离账号。支持轮换的 client 从版本化 secret 重建连接，短期兼容新旧 key。
242. 先 expand 添加 nullable 字段/新表，部署兼容新旧 schema 的应用并回填，再切读写，最后下一版本 contract 删除旧结构。不可同一发布直接 rename/drop。
243. 先基于每消息平均模型/工具耗时用 Little's Law 估算并发，再受供应商 QPS约束。1000 msg/min 若平均占用10秒约需167个并发任务；必须用异步任务和压测修正假设。
244. HTTP/JDBC pool、worker/每用户队列、并发模型调用、下载/解压大小、token/轮数、文件/磁盘、请求 deadline 和用户费用都要有限额，且限额之间匹配。
245. 先定义业务 RPO/RTO；MySQL 做 PITR 并演练，Qdrant snapshot/可从源文档重建，对象存储版本化跨区。恢复后执行跨存储对账而非只确认服务启动。
246. feature flag/策略中心按工具名、租户、风险级别关闭；registry 在调用前实时检查，正在执行的高风险任务支持取消或阻止进入副作用阶段，并记录操作审计。

## 十、现场设计题参考（247-260）

247. 在 definition 增加枚举 RiskLevel、boolean idempotent/requiresConfirmation，默认 READ_ONLY/false 只为兼容但应逐工具显式配置；schema converter 暴露给策略层，loop 执行前统一判断并加契约测试。
248. `AgentToolExecutionResult` 增加 directive enum（CONTINUE/FINAL/AWAIT_USER/FAILED）；taxi/map 返回对应 directive，loop switch 处理。删除工具名判断，测试任意工具都可触发同样控制流。
249. 表含 idempotency_key 唯一、user_id、tool、canonical_args_hash、status、result/error、provider_request_id、timestamps。执行前 INSERT PROCESSING；冲突读取原记录，相同参数返回既有结果，不同参数报冲突；UNKNOWN 走对账。
250. 重点检查 mailbox 队列空、running=false、map remove 与并发 submit 的窗口。用 barrier 卡在退出点同时 submit，断言任务恰好执行一次；修复需在同一同步/CAS 协议内决定移除并用 conditional remove。
251. 定义统一 ClientPolicy：connect/read/deadline、错误分类、retry budget、rate limiter、circuit breaker；HTTP interceptor 写 trace/metrics。副作用 client 通过方法元数据禁用无幂等重试。
252. `WechatToolRequest` 新增 JsonNode arguments，typed binder 转 record 并 Bean Validation；兼容期提供 `legacyStringArguments()`。definition 从 record schema 生成，逐工具迁移后删除字符串接口。
253. 同事务写 assistant_message 和 outbox(part payload, eventId, seq)。publisher 锁定未发送记录、调用通道、记录 SENT/providerId；失败退避。消费按 eventId/part seq 去重，平台无幂等时避免不确定超时后盲发。
254. 最推荐 MQ 以 conversationKey 分区，天然同 key 有序且可削峰；Redis 锁实现简单但锁过期/续租复杂；DB 锁一致但吞吐差。无论哪种都需消息幂等，因为锁不等于 exactly-once。
255. 仅允许 http/https；DNS 解析所有地址并拒私网/保留段；连接固定验证过的 IP 且保持 Host/TLS 校验；每次 redirect 重验；限制端口、次数、大小、timeout，阻断代理环境绕过并测试 rebinding。
256. 元数据先 PENDING，上传对象、写 chunk、upsert 向量，每步以 document/chunk ID 幂等，最后 ACTIVE。失败记录 checkpoint 重试；删除按反向补偿，reconciler 扫描 PENDING/orphan。
257. 数据集按单工具、多工具、缺参、歧义、拒绝、副作用、注入分类；标注期望工具、参数约束和最终事实。指标包括选择 F1、参数正确率、任务完成、危险执行率、轮数、token、费用和延迟。
258. 示例：99% 文本请求 30 秒内给有效结果，副作用重复率为 0；按队列、LLM、tool、send 分解 SLI。用 error-budget burn alert，dashboard 展示总览、依赖、成本和按版本回归。
259. 先裁剪工具/schema和重复上下文、摘要/缓存稳定结果、简单意图用小模型、独立工具并行、限制无效循环；高成本生成需确认。每项用质量评测验证，不能只降模型。
260. 100 DAU 保持模块化单体；1万 DAU 外置文件、状态和任务队列并做限流观测；10万 DAU 按连接 ownership 和 conversationKey 分区，独立扩缩媒体/RAG worker。仅在瓶颈和所有权明确时拆服务。

## 十一、压力追问标准回答（261-272）

261. 幂等必须给出稳定 key：如 userId + actionId/tool operation；数据库唯一索引原子保证。重复请求读取首次保存的状态/结果，PROCESSING/UNKNOWN 不能当失败重做。
262. 锁对象是同一 conversation/action/order，粒度以业务 key 为准。分布式锁有 owner token、租期和续租；释放用 compare owner。锁失效仍可能并发，所以数据库状态条件和幂等键是最终防线。
263. 本地事务只覆盖 MySQL，不能跨模型/微信/Qdrant。外部步骤采用 saga/outbox、PENDING 状态、幂等重试和补偿；绝不在长网络调用期间持数据库事务。
264. 缓存键包含 tenant/user、资源规范 ID、版本/权限；TTL按新鲜度；singleflight/互斥防击穿；负缓存短 TTL；写更新主动失效。敏感缓存加密并强制用户隔离。
265. 超时是未知结果。只在有幂等 requestId 时重试，并先查供应商状态；否则进入 UNKNOWN 对账。第一次结果返回丢失不能等同于执行失败。
266. 异步任务返回 jobId 和预计状态，用户可查询/取消，完成或失败主动通知。任务状态持久化；同会话通知带 sequence，worker 幂等消费，失败进入可重试/死信和人工处理。
267. 分区键用 conversationKey 保序；消费者先做 inbox 去重，offset 只在业务提交后确认。积压看 lag 并扩分区/消费者，但同一热 key 仍需单独限流和拆任务。
268. 降级要保住核心目标：搜索坏了可读用户 URL/缓存，TTS 坏了返回文本，记忆坏了可临时对话并明确不持久化。涉及支付/隐私时宁可拒绝，不能弱化安全。
269. 模型只做概率性意图和参数建议，Java 负责 schema、权限、状态机、金额、幂等和确认。任何不可逆动作都不能仅凭模型文本判断。
270. 先定义任务指标，例如工具选择准确率、参数 exact/constraint match、完成率、危险调用率。用固定标注集与旧版本基线，多次运行报告置信区间，同时看成本和延迟。
271. 没有线上数据就应坦白“当前未达到生产证明”。生产可用证据至少包含明确 SLO、峰值压测、故障演练、恢复 RPO/RTO、安全评审、监控告警和真实回归结果。
272. 可删旧 prompt-json 双路径、重复 legacy reply 字段、低价值且未闭环的高风险工具或散落的兼容解析。删除前用调用数据和测试证明无用，目标是减少协议、状态和外部依赖，而非按行数硬删。

## 使用建议

- 面试回答先讲当前实现，再讲问题，最后给渐进式改进；不要直接把理想架构说成已经完成。
- 遇到并发、事务、重试、支付等问题，主动说清楚唯一键、状态机、事务边界和未知结果处理。
- 任何无法从代码或测试证明的数据，例如线上 QPS、成功率、覆盖率，都应明确说明需要测量，禁止编造。
