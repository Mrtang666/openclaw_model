# 企业化编排系统收口设计

## 背景

编排系统已经完成 Trace 写入、策略抽取、工具能力策略、策略决策记录、诊断查询、脱敏、权限审计、审计查询、Controller 拆分和运行阶段视图。剩余必要项不是继续扩展功能，而是把系统收口成“可运维、可 review、可接手”的状态。

本阶段一次完成两个收口能力：

1. Trace 统计摘要：让最近 Run 列表和详情页都能直接看到执行复杂度与异常数量。
2. 编排系统运行手册：沉淀架构边界、接口、排障路径和扩展规范。

## 设计目标

- 不新增数据库表。
- 不改变 Trace 写入流程。
- 不改变现有 API 路径、鉴权、审计规则。
- 最近 Run 列表无需进入详情即可看到关键统计。
- 完整 Trace 详情统计与 step / phase 视图保持一致。
- 用一份中文手册作为编排系统阶段性收口文档。

## Trace 统计摘要

新增诊断统计视图 `AgentRunDiagnosticStatsView`：

- `totalStepCount`：step 总数。
- `modelRoundCount`：模型回合数量，统计 `MODEL_ROUND`。
- `toolCallCount`：工具调用数量，统计 `TOOL_CALL`。
- `failedStepCount`：失败 step 数量。
- `skippedStepCount`：跳过 step 数量。
- `phaseCount`：运行阶段片段数量，按连续 phase 聚合后的数量。

### 详情页统计

`AgentRunDiagnosticTraceView` 新增 `stats` 字段。统计从完整 `AgentRunStepView` 列表派生，`phaseCount` 使用 `AgentRunPhaseClassifier.phases(steps).size()`。

### 最近列表统计

`AgentRunSummaryView` 和 `AgentRunDiagnosticSummaryView` 新增 `stats` 字段。仓储查询最近 Run 时读取每个 run 的 steps 并计算统计。当前诊断列表 limit 已由 service 限制到最多 100，因此这种实现简单、稳定、易 review。

## 组件边界

### AgentRunStatsCalculator

职责：

- 从 step 列表计算 `AgentRunDiagnosticStatsView`。
- 复用 `AgentRunPhaseClassifier` 计算 `phaseCount`。

它不负责：

- 查询数据库。
- 脱敏。
- HTTP 响应。
- Trace 写入。

### JdbcAgentRunQueryRepository

职责扩展：

- 查询单个 Run 时保持读取完整 steps。
- 查询最近 Run 时为每个 summary 追加 stats。

## 文档收口

新增 `docs/orchestration/enterprise-orchestration-runbook.zh-CN.md`，覆盖：

- 编排系统模块划分。
- Agent Loop 主链路。
- Trace 数据流。
- 诊断 API 与访问审计。
- 运行阶段与统计字段含义。
- 常见排障路径。
- 后续扩展规范。

## 测试策略

- 新增 `AgentRunStatsCalculatorTests`
  - 验证 step 总数、模型回合、工具调用、失败、跳过、阶段片段数。
- 扩展 `AgentRunDiagnosticMapperTests`
  - 验证详情和 summary 都包含脱敏后的 stats。
- 扩展 `AgentRunTraceControllerTests`
  - 验证详情 API 和最近列表 API JSON 中包含 stats。
- 扩展 `JdbcAgentRunQueryRepositoryTests`
  - 验证最近 Run summary 会带统计摘要。

## 非目标

- 不实现 Prometheus 指标导出。
- 不实现 Replay / Debug UI。
- 不新增权限模型。
- 不修改上下文系统、记忆系统或工具执行协议。
