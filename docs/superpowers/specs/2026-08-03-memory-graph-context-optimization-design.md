# Memory Graph 上下文机制优化设计

## 背景

现有 Memory Graph 上下文链路已经接入微信 Function Calling 主流程，能够按强/弱相关选择上下文，并按模型窗口预算做压缩。本轮优化聚焦三个闭环能力：

1. 滑动窗口摘要不只作为独立服务存在，而是能写入 Memory Graph 与长期检索体系。
2. 强相关主题活摘不只被读取，也能根据当前主题自动生成并沉淀。
3. 上下文超预算时优先用模型做语义摘要压缩，失败或仍超限时再截断兜底。

## 目标

- 对最近 N 轮之外的旧对话生成摘要，落为 `CONVERSATION_SUMMARY` 与 `CONVERSATION_TOPIC` 节点，并同步历史主题到 RAG。
- 根据当前主题生成 `ACTIVE_EXTRACT` 节点，用于强相关上下文召回。
- 压缩器先对低优先级 section 做模型摘要压缩，保留关键信息；模型不可用时保持原有截断兜底。
- 保持现有实时请求链路稳定：任何维护/压缩异常都不能阻断微信回复。

## 方案

### 1. 维护服务闭环

扩展 `MemoryGraphMaintenanceService`：

- 新增 `maintainConversationWindow(sessionKey, conversationId, memory, currentTopic)`。
- 当历史轮次超过强相关保留窗口时，调用 `SlidingWindowSummaryService` 摘要窗口外内容。
- 摘要结果写入 Memory Graph：
  - `CONVERSATION_SUMMARY`：保存压缩后的旧对话。
  - `CONVERSATION_TOPIC`：保存历史主题索引。
  - `ACTIVE_EXTRACT`：当前主题不为空时，调用 `ActiveExtractService` 抽取主题活摘。
- 长期记忆继续通过 `ingestLongTermMemories` 写入 Memory Graph 和 RAG。

### 2. 语义压缩

新增 `SectionCompressionService`：

- 输入 section 标题、正文和目标 token 数。
- 调用模型生成更短摘要。
- 输出为空、失败、或摘要没有变短时，返回空，交给截断兜底。

改造 `ContextCompressor`：

- 保留原有按 `compressionPriority` 从高到低压缩的策略。
- 对每个可压缩 section，先尝试语义摘要。
- 语义摘要仍超预算时，再执行原截断逻辑。

### 3. 安全边界

- 所有模型调用失败都吞掉并降级，不影响主链路。
- 不把空摘要、空活摘写入 Memory Graph。
- 写入 RAG 时用明确标签区分：
  - `memory_type:conversation_summary`
  - `memory_type:conversation_topic`
  - `memory_type:active_extract`

## 测试策略

- `MemoryGraphMaintenanceServiceTests` 覆盖摘要、主题、活摘三类节点写入。
- `ContextCompressorTests` 覆盖语义压缩优先、失败截断兜底。
- 定向运行上下文相关测试和微信 Function Calling 回归测试。
