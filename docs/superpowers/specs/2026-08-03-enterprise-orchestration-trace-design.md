# 企业级编排 Trace 第一阶段设计

## 背景

项目已经具备微信入口、Function Calling Agent Loop、工具注册表、上下文编排、目标追踪和记忆系统，但一次 Agent 执行过程仍主要依赖日志与局部状态。企业级 Agent Harness 需要能回答这些问题：

- 这次请求为什么调用了这些工具？
- 每一轮模型返回了什么类型的结果？
- 工具调用是成功、失败、跳过，还是因为策略终止？
- 最终为什么停止？
- 失败时能否复盘、审查、后续重放？

第一阶段只做可观测地基：新增 Agent Run / Step / Trace 持久化，不重写主编排流程。

## 目标

1. 每次 Function Calling 请求创建一条 `agent_runs`。
2. 每轮模型响应、每次工具执行、最终停止原因写入 `agent_run_steps`。
3. Trace 失败不影响用户即时回复。
4. 现有 `AgentGoalTracker`、工具执行日志、Memory Graph 不被替换，只补充更细的编排层视图。

## 非目标

- 不在第一阶段重写 `WechatConversationService`。
- 不在第一阶段拆 `FunctionCallingAgentLoop` 为 Pipeline。
- 不实现异步恢复和 replay，只预留字段。
- 不改变现有工具调用行为。

## 数据模型

### agent_runs

- `id`：主键。
- `run_key`：一次编排运行的外部标识。
- `channel`：例如 `WECHAT`。
- `session_key`：用户/会话 key。
- `user_text`：本次用户需求。
- `context_summary`：上下文摘要或截断后的上下文说明。
- `status`：`RUNNING`、`SUCCEEDED`、`FAILED`、`STOPPED`。
- `stop_reason`：最终停止原因。
- `final_reply_summary`：最终回复摘要。
- `started_at`、`completed_at`。

### agent_run_steps

- `id`：主键。
- `run_id`：所属 run。
- `step_index`：顺序号。
- `step_type`：`MODEL_ROUND`、`TOOL_CALL`、`TOOL_RESULT`、`STOP`。
- `round_number`：模型循环轮次。
- `tool_name`：工具名，可空。
- `status`：`STARTED`、`SUCCESS`、`FAILED`、`SKIPPED`。
- `input_summary`：输入摘要。
- `output_summary`：输出摘要。
- `metadata_json`：扩展信息。
- `created_at`。

## 核心类

- `AgentRunStatus`：run 状态枚举。
- `AgentRunStepType`：step 类型枚举。
- `AgentRunStepStatus`：step 状态枚举。
- `AgentRunHandle`：运行句柄，包含 run id 和 run key。
- `AgentRunRepository`：持久化接口。
- `JdbcAgentRunRepository`：MySQL 实现。
- `AgentRunTraceService`：对编排层暴露的安全 trace 服务。

## 接入点

第一阶段只接入 `FunctionCallingAgentLoop`：

1. loop 开始时 `startWechatRun(...)`。
2. 每轮模型无响应 / 最终回复 / 工具调用 / 工具结果 / 跳过工具 / 最大轮数都写 step。
3. loop 结束时 `complete(...)` 或 `fail(...)`。

所有 trace 调用必须捕获异常并降级为 no-op，不能影响微信回复。

## 测试策略

- Repository 测试：验证 run 和 step 能写入、查询计数正确。
- TraceService 测试：验证仓储异常时不向外抛。
- FunctionCallingAgentLoop 测试：验证正常工具链路会创建 run、记录工具 step、写停止原因。
