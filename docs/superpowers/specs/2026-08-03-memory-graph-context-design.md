# Memory Graph 上下文机制设计

日期：2026-08-03  
范围：微信端 Agent 上下文、会话记忆、RAG 长期记忆、滑动窗口摘要、上下文预算压缩

## 背景

当前微信端上下文机制主要由 `WechatConversationService` 调用 `WechatAgentMemoryContextBuilder` 完成。它能把会话摘要、最近对话、媒体记忆、工具状态、资源上下文和待追问信息整理成结构化文本，并带有固定字符上限。

这套机制已经解决了“无限拼上下文”和“工具结果丢失”等问题，但它仍然是线性上下文拼装：最近轮次、摘要、RAG 和资源信息彼此缺少更明确的主题关系、来源关系和预算分配策略。

本设计将上下文系统升级为 Memory Graph：将对话轮次、摘要、活摘、历史主题、长期记忆、工具结果和资源引用表示成可检索、可压缩、可追溯的记忆节点，并根据当前用户消息与历史主题的相关性动态组装上下文。

## 目标

1. 用户发送消息时，先判断当前消息与上下文主题是强相关还是弱相关。
2. 强相关时保留最近 N 轮完整对话，默认 5 轮；窗口外但与当前主题相关的内容保留为活摘。
3. 弱相关时保留最近 1 轮和历史主题，不携带大量旧细节。
4. 长期记忆使用 RAG 存储和检索，内容限于稳定事实、长期偏好和持续项目背景。
5. 上下文进入模型前必须控制在所用模型可用输入预算的 80% 内。
6. 系统提示词和当前用户需求永远不压缩、不删除。
7. 历史对话使用滑动窗口持续摘要，避免上下文无限膨胀。
8. 新系统失败时回退到现有 `WechatAgentMemoryContextBuilder`，不影响主聊天链路。

## 非目标

1. 不替换现有 `conversation_messages` 原始消息存储。
2. 不在每轮用户回复同步写入长期记忆。
3. 不把所有闲聊都自动写入长期记忆。
4. 不在第一版实现完整通用知识图谱推理。
5. 不改变 Function Calling Agent Loop 的工具执行协议。

## 术语

- 强相关：当前消息与最近上下文属于同一主题，主题连续性明显。
- 弱相关：新话题、不相关话题、主题跨度很大，或无法可靠判断为同主题。
- 活摘：当前会话中不在最近完整窗口内、但与当前主题强相关的滚动摘要片段。
- 长期记忆：用户稳定事实、长期偏好、持续项目背景，由 RAG 存储和检索。
- 历史主题：用户之前聊过的话题摘要，用于弱相关时让模型知道用户历史兴趣，但不携带完整旧细节。
- 模型可用输入预算：模型总上下文窗口扣除输出预留和工具循环预留后的输入预算。

## 总体架构

新增 Memory Graph Context Layer，位于 `WechatConversationService` 和 `FunctionCallingAgentLoop` 之间。

```text
WechatConversationService
  -> WechatContextOrchestrator
      -> ConversationRelevanceClassifier
      -> MemoryGraphRetriever
      -> SlidingWindowSummarizer
      -> LongTermMemoryRetriever
      -> ContextBudgetManager
      -> ContextCompressor
      -> ContextAssembler
  -> FunctionCallingAgentLoop
```

现有 `WechatAgentMemoryContextBuilder` 保留，但职责降级为 fallback 和部分格式化器。新上下文入口由 `WechatContextOrchestrator` 承担。

## 核心组件

### WechatContextOrchestrator

上下文构建总入口。

职责：

- 接收 `sessionKey`、当前用户消息、当前模型预算、当前附件/图片/网页/视频资源。
- 调用相关性分类器判断强弱相关。
- 调用 Memory Graph 和 RAG 检索候选记忆。
- 应用强相关或弱相关策略。
- 按预算分配上下文片段。
- 必要时压缩摘要、活摘、长期记忆或历史主题。
- 输出最终 `WechatContextPackage`。

建议接口：

```java
WechatContextPackage build(ContextBuildRequest request);
```

建议返回：

```java
record WechatContextPackage(
        RelevanceLevel relevance,
        String finalContextText,
        List<MemoryNode> selectedNodes,
        ContextBudgetReport budgetReport) {
}
```

### ConversationRelevanceClassifier

判断当前用户消息和上下文主题是强相关还是弱相关。

二分类：

```java
enum RelevanceLevel {
    STRONG,
    WEAK
}
```

模型输入：

- 当前用户消息。
- 最近 5 轮对话。
- 当前活摘。
- 最近历史主题列表。
- 当前会话摘要。

模型输出 JSON：

```json
{
  "relevance": "STRONG",
  "confidence": 0.86,
  "currentTopic": "OpenClaw 微信端上下文机制优化",
  "reason": "用户仍在讨论上下文滑动窗口和长期记忆设计"
}
```

兜底规则：

- “继续、可以、确认、按刚才、第二个、上一个、改一下、就这样”等指代表达默认强相关。
- 当前消息出现明显新主题词且没有指代表达时默认弱相关。
- 模型调用失败或结果不可解析时默认弱相关，避免错误携带大量旧上下文。

### MemoryGraphNode

记忆图谱节点。

节点类型：

```java
enum MemoryNodeType {
    CONVERSATION_TURN,
    CONVERSATION_SUMMARY,
    ACTIVE_EXTRACT,
    CONVERSATION_TOPIC,
    LONG_TERM_MEMORY,
    TOOL_RESULT_MEMORY,
    RESOURCE_REFERENCE
}
```

建议字段：

```text
id
session_key
conversation_id
node_type
topic_key
title
content
summary
importance_score
relevance_score
confidence_score
source_message_start_id
source_message_end_id
source_type
source_ref
tags
created_at
updated_at
expires_at
deleted
```

节点含义：

- `CONVERSATION_TURN`：一轮完整用户/助手对话，可引用原始 `conversation_messages`。
- `CONVERSATION_SUMMARY`：滑动窗口摘要。
- `ACTIVE_EXTRACT`：当前主题相关的活摘。
- `CONVERSATION_TOPIC`：历史主题摘要。
- `LONG_TERM_MEMORY`：长期事实、偏好、持续项目背景。
- `TOOL_RESULT_MEMORY`：值得保留的重要工具结果。
- `RESOURCE_REFERENCE`：图片、文件、网页、视频等轻量引用。

### MemoryGraphEdge

记忆节点之间的关系。

边类型：

```java
enum MemoryEdgeType {
    NEXT,
    SUMMARIZES,
    DERIVED_FROM,
    SAME_TOPIC,
    REFERENCES,
    SUPERSEDES
}
```

典型关系：

```text
conversation_summary SUMMARIZES conversation_turn
active_extract DERIVED_FROM conversation_summary
conversation_topic SAME_TOPIC active_extract
long_term_memory DERIVED_FROM conversation_turn
resource_reference REFERENCES tool_result_memory
```

### MemoryGraphRepository

负责 Memory Graph 的 MySQL 元数据读写。

职责：

- 创建节点。
- 创建边。
- 按 session、conversation、type、topic 查询节点。
- 按消息范围查询摘要覆盖。
- 软删除或失效旧节点。
- 查询未摘要的对话窗口。

### SlidingWindowSummarizer

负责滑动窗口摘要。

策略：

- 强相关默认保留最近 5 轮完整对话。
- 窗口外对话按固定窗口生成 `CONVERSATION_SUMMARY`。
- 摘要覆盖范围记录 `source_message_start_id` 和 `source_message_end_id`。
- 新摘要可通过 `SUPERSEDES` 标记替代旧摘要。

摘要只保留：

- 用户明确事实。
- 用户偏好。
- 当前目标。
- 已完成事项。
- 未完成事项。
- 待确认问题。
- 关键工具结果。

摘要不保留：

- 寒暄。
- 重复确认。
- 无关情绪表达。
- 临时工具噪声。
- 系统提示词。
- 敏感凭据。

### ActiveExtractService

负责生成活摘。

活摘定义：

- 来源于窗口外对话和历史摘要。
- 必须和当前主题强相关。
- 不等同于长期记忆。
- 优先服务当前连续主题。

强相关时，活摘进入上下文；弱相关时，默认不带详细活摘，只带历史主题。

### LongTermMemoryExtractor

后台异步抽取长期记忆。

允许写入：

- 稳定事实。
- 长期偏好。
- 持续项目背景。
- 用户长期工作方式偏好。

禁止写入：

- 一次性任务。
- 临时提醒。
- 临时天气、价格、搜索结果。
- 医疗细节。
- 支付信息。
- token、账号、密码、密钥。
- 模型不确定的推断。

### ContextBudgetManager

负责计算上下文预算。

公式：

```text
availableInputTokens = modelContextWindowTokens - outputReserveTokens - toolLoopReserveTokens
contextBudgetTokens = availableInputTokens * maxInputRatio
```

默认配置：

```properties
wechat.agent.context.model-window-tokens=128000
wechat.agent.context.output-reserve-tokens=8000
wechat.agent.context.tool-loop-reserve-tokens=12000
wechat.agent.context.max-input-ratio=0.8
```

第一版可使用保守 token 估算器：

```java
interface TokenEstimator {
    int estimate(String text);
}
```

默认实现可按 `1 char ≈ 1 token` 保守估算。以后可替换为精确 tokenizer。

### ContextCompressor

超预算时按优先级压缩。

压缩顺序：

1. 压缩或裁剪 RAG 长期记忆和历史主题。
2. 压缩活摘。
3. 减少完整最近轮次，但最低保留最近 2 轮。
4. 压缩系统附加说明。
5. 系统提示词和当前用户需求不压缩。

强相关默认预算比例：

```text
recent_turns: 40%
active_extract: 20%
long_term_memory: 15%
resource_context: 15%
conversation_summary: 10%
```

弱相关默认预算比例：

```text
recent_turns: 15%
conversation_topics: 35%
long_term_memory: 25%
resource_context: 15%
conversation_summary: 10%
```

某部分为空时，预算可让给其他部分。

## 数据存储设计

### MySQL

新增表：`memory_graph_nodes`

```sql
CREATE TABLE memory_graph_nodes (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    session_key VARCHAR(191) NOT NULL,
    conversation_id BIGINT NULL,
    node_type VARCHAR(32) NOT NULL,
    topic_key VARCHAR(191) NULL,
    title VARCHAR(255) NOT NULL,
    content MEDIUMTEXT NOT NULL,
    summary TEXT NULL,
    importance_score DOUBLE NOT NULL DEFAULT 0,
    relevance_score DOUBLE NOT NULL DEFAULT 0,
    confidence_score DOUBLE NOT NULL DEFAULT 0,
    source_message_start_id BIGINT NULL,
    source_message_end_id BIGINT NULL,
    source_type VARCHAR(64) NULL,
    source_ref TEXT NULL,
    tags VARCHAR(512) NULL,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    expires_at DATETIME(3) NULL,
    deleted TINYINT NOT NULL DEFAULT 0,
    KEY idx_memory_nodes_session_type_created (session_key, node_type, created_at),
    KEY idx_memory_nodes_session_topic (session_key, topic_key),
    KEY idx_memory_nodes_conversation_type (conversation_id, node_type),
    KEY idx_memory_nodes_deleted_expires (deleted, expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

新增表：`memory_graph_edges`

```sql
CREATE TABLE memory_graph_edges (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    session_key VARCHAR(191) NOT NULL,
    source_node_id BIGINT NOT NULL,
    target_node_id BIGINT NOT NULL,
    edge_type VARCHAR(32) NOT NULL,
    weight DOUBLE NOT NULL DEFAULT 1,
    created_at DATETIME(3) NOT NULL,
    UNIQUE KEY uk_memory_edges_unique (source_node_id, target_node_id, edge_type),
    KEY idx_memory_edges_source (source_node_id, edge_type),
    KEY idx_memory_edges_target (target_node_id, edge_type),
    KEY idx_memory_edges_session_type (session_key, edge_type),
    CONSTRAINT fk_memory_edges_source
        FOREIGN KEY (source_node_id) REFERENCES memory_graph_nodes(id),
    CONSTRAINT fk_memory_edges_target
        FOREIGN KEY (target_node_id) REFERENCES memory_graph_nodes(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

MySQL 用途：

- 保存节点生命周期。
- 保存来源关系。
- 保存摘要覆盖范围。
- 支持审计和回溯。
- 支持 fallback 检索。

### Qdrant / RAG

继续复用现有知识库管线，但新增 memory tags：

```text
memory_type:long_term_memory
memory_type:conversation_topic
memory_type:active_extract
memory_type:tool_result_memory
```

长期记忆写入示例：

```text
title: 用户偏好：技术方案呈现方式
sourceType: memory_graph
sourceUrl: memory://long_term_memory/{nodeId}
tags: memory_type:long_term_memory,scope:wechat
content: 用户偏好先确认设计方案，再进入实现计划；喜欢按模块拆解和给出测试策略。
```

历史主题写入示例：

```text
title: 历史主题：OpenClaw Memory Graph 上下文机制
sourceType: memory_graph
sourceUrl: memory://conversation_topic/{nodeId}
tags: memory_type:conversation_topic,scope:wechat
content: 用户讨论了微信端上下文强弱相关判断、滑动窗口摘要、活摘、长期记忆 RAG 存储、模型窗口 80% 预算控制。
```

## 上下文组装策略

### 强相关

输入：

- 系统提示词。
- 当前用户消息。
- 最近 5 轮完整对话。
- 当前主题活摘。
- 长期记忆。
- 当前资源上下文。
- 必要知识库证据。

示例：当前第 11 轮时，完整保留第 7-11 轮；第 1-6 轮进入摘要和活摘。

### 弱相关

输入：

- 系统提示词。
- 当前用户消息。
- 最近 1 轮完整对话。
- 历史主题列表。
- 长期记忆。
- 当前资源上下文。

弱相关默认不携带大量旧细节。用户后续明确回到某个旧主题时，再通过 RAG 检索恢复对应主题。

## 最终上下文格式

```text
【context_policy / 上下文策略】
相关性：STRONG
策略：保留最近 5 轮完整对话 + 当前主题活摘 + 长期记忆
预算：86400 tokens
实际：25300 tokens

【recent_turns / 最近完整对话】
用户：...
助手：...

【active_extract / 当前主题活摘】
- 用户正在设计 OpenClaw Memory Graph 上下文系统
- 已确认强弱相关为二分类
- 已确认长期记忆异步写入 RAG

【long_term_memory / 长期记忆】
- 用户偏好完整设计后再实现
- 用户项目为 OpenClaw 微信端 Agent

【conversation_topics / 历史主题】
- harness 六大系统审查
- 微信上下文滑动窗口机制
- 工具系统与 skills 对齐

【resource_context / 可用资源】
最近图片、文件、网页、视频引用...
```

## 与现有系统集成

### WechatConversationService

替换 `conversationContext(sessionKey)` 的内部实现：

```java
contextOrchestrator.build(request).finalContextText()
```

保留异常 fallback：

```java
memoryContextBuilder.build(memoryFor(sessionKey), combinedResourceContext(sessionKey))
```

### FunctionCallingAgentLoop

第一阶段不改变工具 loop 协议。

- `historyText` 传入 Memory Graph 生成的上下文。
- `ragContext` 暂时继续沿用现有 `WechatRagContextService`。
- 后续可将 RAG 证据整合进 `WechatContextPackage`，避免上下文来源分散。

### MySqlWechatMemoryService

保留现有会话原始消息、状态 JSON 和 `conversation_summaries`。

新增 Memory Graph 维护任务，不直接替换现有摘要维护逻辑。

## 配置项

```properties
wechat.agent.context.memory-graph-enabled=true
wechat.agent.context.relevance-classifier-enabled=true
wechat.agent.context.long-term-memory-ingestion-enabled=true
wechat.agent.context.strong-recent-turns=5
wechat.agent.context.weak-recent-turns=1
wechat.agent.context.min-recent-turns=2
wechat.agent.context.summary-window-size=5
wechat.agent.context.summary-overlap-turns=1
wechat.agent.context.model-window-tokens=128000
wechat.agent.context.output-reserve-tokens=8000
wechat.agent.context.tool-loop-reserve-tokens=12000
wechat.agent.context.max-input-ratio=0.8
```

## 实现计划

### 阶段 1：基础模型与配置

新增：

- `WechatContextProperties`
- `ModelContextBudgetProperties`
- `RelevanceLevel`
- `MemoryNodeType`
- `MemoryEdgeType`
- `MemoryGraphNode`
- `MemoryGraphEdge`
- `ContextBuildRequest`
- `WechatContextPackage`
- `ContextBudgetReport`

验收：

- 配置默认值正确。
- 无效配置自动回退安全默认值。

### 阶段 2：相关性判断

新增：

- `ConversationRelevanceClassifier`
- `ModelConversationRelevanceClassifier`
- `RuleBasedRelevanceFallback`

验收：

- 同主题延续判为强相关。
- 新话题判为弱相关。
- “继续/可以/按刚才”在模型失败时兜底强相关。
- 模型异常时默认弱相关。

### 阶段 3：Memory Graph 存储

新增 migration：

- `memory_graph_nodes`
- `memory_graph_edges`

新增：

- `MemoryGraphRepository`
- `MySqlMemoryGraphRepository`

验收：

- 节点创建、查询、软删除正常。
- 边创建和按类型查询正常。
- 按 session/topic/type 查询正常。
- 可以查询摘要覆盖的 message id 范围。

### 阶段 4：滑动窗口摘要与活摘

新增：

- `SlidingWindowSummaryService`
- `ActiveExtractService`
- `ConversationTopicExtractor`

验收：

- 第 11 轮时完整保留第 7-11 轮。
- 第 1-6 轮被摘要覆盖。
- 活摘只包含当前主题相关内容。
- 摘要不包含系统提示词或敏感字段。

### 阶段 5：长期记忆异步写入 RAG

新增：

- `LongTermMemoryExtractor`
- `ConversationTopicMemoryIngestionService`
- `MemoryGraphMaintenanceScheduler`

验收：

- 稳定偏好能入库。
- 临时任务不入库。
- 敏感内容不入库。
- 重复长期记忆不会重复写入。
- 写入 RAG 时包含 memory type tags。

### 阶段 6：预算管理与压缩

新增：

- `TokenEstimator`
- `ConservativeTokenEstimator`
- `ContextBudgetManager`
- `ContextCompressor`

验收：

- 未超预算时不压缩。
- 超预算时先压缩长期记忆和历史主题。
- 最近完整轮次最低保留 2 轮。
- 当前用户消息永不丢失。
- 输出预算报告。

### 阶段 7：接入微信会话主链路

改造：

- `WechatConversationService.conversationContext(...)`
- 相关测试中的上下文断言

验收：

- Function Calling Agent Loop 能收到 Memory Graph 上下文。
- 强相关上下文包含最近 5 轮和活摘。
- 弱相关上下文只包含最近 1 轮和历史主题。
- 新系统失败时回退旧 builder。
- 原 harness 定向测试继续通过。

## 风险与保护措施

风险：

1. 每轮相关性模型调用增加延迟。
2. 长期记忆抽取可能误记。
3. Memory Graph 数据模型较复杂，第一版容易扩大范围。
4. token 估算不准导致实际窗口仍超限。
5. RAG 检索历史主题时可能拉回无关旧话题。

保护措施：

1. 相关性判断失败默认弱相关。
2. 长期记忆异步写入，不阻塞微信回复。
3. 长期记忆写入使用过滤器和去重。
4. 第一版只接入上下文读取，不替换原始消息存储。
5. 保留旧 builder fallback。
6. 所有新能力提供配置开关。

## 测试策略

单元测试：

- 相关性分类。
- Memory Graph repository。
- 滑动窗口摘要覆盖范围。
- 活摘抽取。
- 长期记忆过滤。
- 预算计算。
- 压缩优先级。
- 上下文格式化。

集成测试：

- `WechatConversationService` 强相关上下文。
- `WechatConversationService` 弱相关上下文。
- Function Calling Agent Loop 接收新上下文。
- RAG 长期记忆检索。
- 新上下文系统失败 fallback。

回归测试：

- 现有 harness 定向测试继续通过。
- 全量测试若存在基线失败，需要单独列出，不归因于本功能。

## 验收标准

1. 第 11 轮强相关对话时，完整上下文保留第 7-11 轮。
2. 第 1-6 轮被压缩为摘要/活摘，不再完整进入 prompt。
3. 弱相关新话题只保留最近 1 轮和历史主题。
4. 长期记忆来自 RAG，且只包含稳定事实、长期偏好、持续项目背景。
5. 上下文不超过模型可用输入预算的 80%。
6. 系统提示词和当前用户需求不被压缩。
7. Function Calling 工具链保持可用。
8. 新系统失败时回退旧上下文机制。

## 推荐落地顺序

虽然目标架构是 Memory Graph，但实现应分阶段推进：

1. 先落地数据模型、配置和 repository。
2. 再实现相关性分类。
3. 再实现强/弱相关上下文组装。
4. 再实现滑动窗口摘要和活摘。
5. 再接入 RAG 长期记忆异步写入。
6. 最后接入严格 token 预算和压缩器。

这样每一步都有独立可测结果，并且不会一次性冲击微信主链路。
