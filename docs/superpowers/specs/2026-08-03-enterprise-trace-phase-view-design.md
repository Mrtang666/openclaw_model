# 企业化 Trace 运行阶段视图设计

## 背景

当前 Agent Run Trace 已经记录了细粒度 step：

- `MODEL_ROUND`
- `TOOL_CALL`
- `TOOL_RESULT`
- `POLICY_DECISION`
- `STOP`

这些事件足够还原执行过程，但对运维和 review 来说仍偏底层。一个复杂 Agent Run 往往包含多轮模型调用、工具调用、策略跳过、最终停止。只看 step 列表时，需要人工把事件归类，才能判断执行大致卡在哪个阶段。

本阶段目标是在诊断响应层增加“运行阶段视图”，让 Trace 从“事件日志”进一步升级成“可理解的执行轨迹”。

## 设计目标

- 不修改数据库结构。
- 不改变 Trace 写入流程。
- 不破坏现有 `stepType` 字段。
- 在诊断响应中新增派生字段，便于前端、运维 API、后续指标系统直接消费。
- 阶段分类逻辑集中在独立组件中，避免散落在 Controller 或 Mapper。

## 阶段模型

新增 `AgentRunStepPhase` 枚举：

- `MODEL`：模型推理/模型回合，对应 `MODEL_ROUND`。
- `TOOL`：工具调用链路，对应 `TOOL_CALL`、`TOOL_RESULT`。
- `POLICY`：编排策略判断，对应 `POLICY_DECISION`。
- `TERMINAL`：停止/结束节点，对应 `STOP`。

如果未来新增未知 step type，分类器默认归到 `MODEL`，保证诊断响应不会因为新类型暂未分类而失败。后续新增 step type 时再显式补映射。

## 响应视图

### Step 级别

`AgentRunDiagnosticStepView` 新增字段：

- `stepPhase`

示例：

```json
{
  "stepIndex": 3,
  "stepType": "POLICY_DECISION",
  "stepPhase": "POLICY",
  "status": "SKIPPED"
}
```

### Trace 级别

`AgentRunDiagnosticTraceView` 新增字段：

- `phases`

`phases` 是按 step 顺序对连续相同 phase 的聚合视图，不合并非连续片段。这样可以表达真实执行节奏，例如：

```json
[
  {"phase": "MODEL", "startStepIndex": 1, "endStepIndex": 1, "stepCount": 1, "status": "SUCCESS"},
  {"phase": "TOOL", "startStepIndex": 2, "endStepIndex": 3, "stepCount": 2, "status": "SUCCESS"},
  {"phase": "POLICY", "startStepIndex": 4, "endStepIndex": 4, "stepCount": 1, "status": "SKIPPED"},
  {"phase": "MODEL", "startStepIndex": 5, "endStepIndex": 5, "stepCount": 1, "status": "SUCCESS"}
]
```

## 状态聚合规则

每个 phase 片段的状态由内部 steps 聚合：

- 任一 step 为 `FAILED`，片段状态为 `FAILED`。
- 否则，任一 step 为 `STARTED`，片段状态为 `STARTED`。
- 否则，如果所有 step 都是 `SKIPPED`，片段状态为 `SKIPPED`。
- 其他已完成混合情况为 `SUCCESS`。

这套规则偏向运维诊断：失败优先，其次展示仍在进行，跳过只在整个片段都跳过时才显示。

## 组件边界

### AgentRunPhaseClassifier

职责：

- 将 `AgentRunStepType` 映射为 `AgentRunStepPhase`。
- 将有序 step 列表聚合为 `AgentRunDiagnosticPhaseView` 列表。

它不负责：

- 脱敏。
- 查询数据库。
- 写入 Trace。
- HTTP 响应。

### AgentRunDiagnosticMapper

职责扩展：

- 映射 step 时填充 `stepPhase`。
- 映射完整 trace 时填充 `phases`。

Mapper 仍然负责脱敏，但阶段分类委托给 `AgentRunPhaseClassifier`。

## 错误处理

- step 列表为空：`phases` 返回空列表。
- step type 为 null：归类为 `MODEL`。
- phase 聚合只读、纯内存执行，不影响主查询。

## 测试策略

- 新增 `AgentRunPhaseClassifierTests`
  - 验证 step type 到 phase 的映射。
  - 验证连续相同 phase 会聚合。
  - 验证非连续相同 phase 不会被错误合并。
  - 验证失败优先的状态聚合。
- 扩展 `AgentRunDiagnosticMapperTests`
  - 验证 step 诊断视图包含 `stepPhase`。
  - 验证 trace 诊断视图包含 `phases`。
- 扩展 `AgentRunTraceControllerTests`
  - 验证 HTTP JSON 中包含 `steps[0].stepPhase` 和 `phases[0].phase`。

## 非目标

- 不新增数据库字段。
- 不修改 `AgentRunTraceService` 的写入方法。
- 不新增后台指标采集。
- 不实现前端可视化页面。
- 不改变已有诊断 API 的鉴权与审计规则。
