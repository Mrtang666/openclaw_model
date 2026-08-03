# 企业级编排决策 Trace 设计

## 背景

前几阶段已经完成：

1. Agent Run Trace 基础：记录 Agent Run、模型轮次、工具调用、工具结果和停止原因。
2. Agent Loop Policy 抽离：将停止策略、工具执行策略从 Loop 中抽出。
3. Tool Capability Policy：集中管理工具运行时能力，例如 terminal、失败识别、可见输出和运行时规则。

当前 Trace 仍然偏“发生了什么”，但缺少“为什么这么做”。例如：

- 为什么跳过一次重复工具调用。
- 为什么跳过 `web_search`。
- 为什么 terminal tool 执行后直接停止。
- 为什么重复失败后直接终止。

企业级 Harness 需要把这些策略决策记录下来，方便排障、审计、评估策略效果和后续做运营看板。

## 目标

1. 新增 `AgentRunStepType.POLICY_DECISION`。
2. 在 `AgentRunTraceService` 增加 `recordPolicyDecision(...)`。
3. 在 `FunctionCallingAgentLoop` 的关键策略分支记录决策事件。
4. 不新增数据库表，复用 `agent_run_steps.metadata_json` 承载结构化信息。

## 非目标

- 不做前端 Trace UI。
- 不做 Trace 查询 API。
- 不新增数据库迁移。
- 不改变现有 `TOOL_CALL`、`TOOL_RESULT`、`MODEL_ROUND` 语义。
- 不改变 Agent 行为，只新增审计记录。

## 决策事件模型

每条 policy decision 作为一条 `agent_run_steps` 记录：

- `step_type`：`POLICY_DECISION`
- `status`：通常是 `SUCCESS`，跳过类决策可用 `SKIPPED`
- `round_number`：当前模型轮次
- `tool_name`：相关工具名；没有工具时为空
- `input_summary`：决策输入摘要，例如签名、工具参数、失败签名
- `output_summary`：决策结果摘要，例如 `SKIP_DUPLICATE_TOOL_CALL`
- `metadata_json`：结构化字段
  - `decision_type`
  - `reason`
  - `signature`
  - `tool_name`

## 初始记录点

本阶段先覆盖最关键、最容易排查问题的分支：

1. 重复工具调用被跳过：
   - decision_type：`SKIP_DUPLICATE_TOOL_CALL`
   - status：`SKIPPED`
   - metadata：`signature`
2. 重复语音合成被跳过：
   - decision_type：`SKIP_DUPLICATE_VOICE_SYNTHESIS`
   - status：`SKIPPED`
   - metadata：`signature`
3. RAG 已有证据导致跳过 `web_search`：
   - decision_type：`SKIP_WEB_SEARCH_RAG_EVIDENCE`
   - status：`SKIPPED`
   - metadata：`reason=RAG_HAS_EVIDENCE`
4. terminal tool 执行后直接停止：
   - decision_type：`END_TURN_AFTER_TERMINAL_TOOL`
   - status：`SUCCESS`
   - metadata：`stop_reason=SPECIAL_TOOL_DONE`
5. 重复失败签名导致终止：
   - decision_type：`STOP_REPEATED_TOOL_FAILURE`
   - status：`FAILED`
   - metadata：`failure_signature`

## 接入方式

- `AgentRunTraceService.recordPolicyDecision(...)` 是唯一入口。
- `FunctionCallingAgentLoop` 只在已有分支旁边追加记录，不改业务决策本身。
- `AgentRunTraceService` 继续吞掉 repository 异常，避免 Trace 失败影响 Agent 主链路。

## 测试策略

- `AgentRunTraceServiceTests`
  - 验证 `recordPolicyDecision(...)` 委托 repository 写入 `POLICY_DECISION`。
- `JdbcAgentRunRepositoryTests`
  - 验证 `POLICY_DECISION` 可以写入 `agent_run_steps`。
- `FunctionCallingAgentLoopTests`
  - 验证 RAG 跳过 `web_search` 时记录 policy decision。
  - 验证 terminal tool 停止时记录 policy decision。
  - 验证重复工具调用跳过时记录 policy decision。
- `ApplicationContextTests`
  - 验证枚举和服务改动不破坏启动。

## 后续演进

后续可以继续扩展：

1. 增加 Trace 查询接口，按 run_key 返回完整决策链路。
2. 增加 policy decision 统计，例如跳过 web_search 节省次数、重复工具调用率。
3. 为上下文压缩、记忆召回、Agent 协作也加入同样的 decision trace。
