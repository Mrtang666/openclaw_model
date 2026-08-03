# 编排系统 Trace 诊断脱敏设计

## 背景

当前 Trace 查询 API 已经可以通过 HTTP 暴露 Agent Run 的完整轨迹，包括用户原文、上下文摘要、工具输入/输出摘要和策略 metadata。这对排障很有价值，但在企业化项目里，诊断出口不应该直接返回内部原始 Trace View。

本阶段目标是增加“诊断视图分层”和“轻量脱敏策略”：内部查询层仍保留完整数据，HTTP API 默认返回脱敏后的 Diagnostic View。

## 目标

1. 保留内部 `AgentRunTraceView / AgentRunSummaryView / AgentRunStepView` 的完整语义，不影响后端服务和测试。
2. 新增面向 HTTP 诊断出口的 Diagnostic DTO。
3. 新增独立脱敏策略，统一处理用户原文、上下文摘要、工具输入/输出摘要、最终回复摘要和 metadata JSON。
4. 默认识别并打码：
   - 邮箱地址
   - 常见手机号/长数字串
   - `password / token / secret / api_key / access_key` 等敏感键值
5. 对过长诊断文本做展示截断，避免接口一次性返回过大内容。
6. Controller 只返回 Diagnostic View，不再直接暴露内部 Trace View。

## 非目标

1. 本阶段不实现完整合规级 DLP。
2. 本阶段不新增鉴权、角色、审计日志；这些应作为后续独立阶段处理。
3. 本阶段不修改数据库，不改变 Trace 写入链路。
4. 本阶段不删除内部完整查询能力。

## 设计方案

### 组件边界

- `AgentTraceRedactionPolicy`
  - 负责纯文本脱敏和截断。
  - 不依赖 Spring 容器，便于单元测试。

- `AgentRunDiagnosticStepView`
  - 对外展示单个 step。
  - 保留类型、状态、轮次、工具名、时间等结构化排障字段。
  - 对输入、输出、metadata 做脱敏。

- `AgentRunDiagnosticTraceView`
  - 对外展示单次完整 run。
  - 对 userText、contextSummary、finalReplySummary 做脱敏。

- `AgentRunDiagnosticSummaryView`
  - 对外展示最近 run 摘要列表。
  - 对 userText、contextSummary、finalReplySummary 做脱敏。

- `AgentRunDiagnosticMapper`
  - 从内部 Trace View 映射到 Diagnostic View。
  - 聚合脱敏策略，避免 Controller 手写字段转换。

### 数据流

```mermaid
flowchart LR
    A["AgentRunTraceController"] --> B["AgentRunTraceQueryService"]
    B --> C["内部 Trace View"]
    C --> D["AgentRunDiagnosticMapper"]
    D --> E["AgentTraceRedactionPolicy"]
    E --> F["Diagnostic View"]
    F --> G["HTTP Response"]
```

### 脱敏规则

1. 邮箱：
   - `alice@example.com` 转为 `a***@example.com`。
2. 长数字串：
   - 11 位手机号或 10 位以上连续数字转为前 3 后 4 保留，中间 `****`。
3. 敏感键值：
   - `password=abc123`、`token: abc123`、`"api_key":"abc123"` 等转为对应 key 加 `[REDACTED]`。
4. 展示长度：
   - 默认最多保留 512 字符，超出追加 `... [TRUNCATED]`。

## 测试策略

采用 TDD：

1. 先写 `AgentTraceRedactionPolicyTests`，验证邮箱、手机号、敏感键值、长文本截断。
2. 运行测试确认红灯，因为策略类不存在。
3. 实现策略类。
4. 写 `AgentRunDiagnosticMapperTests`，验证完整 run、summary、steps 都使用脱敏字段。
5. 实现 Diagnostic DTO 和 Mapper。
6. 更新 `AgentRunTraceControllerTests`，验证 API 返回脱敏内容、不返回原始敏感值。
7. 更新 Controller 依赖 Mapper，运行相关测试。

## 后续扩展

1. 增加基于角色的 Trace 原文访问开关。
2. 增加审计日志，记录谁查询了哪个 runKey。
3. 支持不同诊断级别：`SAFE / INTERNAL / RAW`。
4. 使用结构化 JSON 脱敏器解析 metadata，而不是纯文本正则。
