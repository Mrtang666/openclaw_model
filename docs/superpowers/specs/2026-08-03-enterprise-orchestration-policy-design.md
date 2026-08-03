# 企业级编排策略层设计

## 背景

上一阶段已经为 Function Calling 主链路补齐了 Agent Run / Model Round / Tool Call / Tool Result / Stop Reason 的 Trace 基础。当前 `FunctionCallingAgentLoop` 仍然混合承担两类职责：

- 循环编排职责：组织模型轮次、执行工具、写入 tool message、收敛最终回复。
- 策略判断职责：判断哪些工具执行后直接结束、什么时候跳过 `web_search`、如何生成工具调用去重签名、如何识别重复语音合成、如何生成失败签名。

从企业级 Harness 视角看，Loop 应该是稳定执行器；策略应沉淀为可独立测试、可后续配置化、可审计演进的 policy 层。

## 目标

1. 新增 `AgentStopPolicy`，集中管理 terminal tool 与 Agent 停止策略。
2. 新增 `ToolExecutionPolicy`，集中管理工具跳过、工具去重签名、语音去重签名、失败签名。
3. `FunctionCallingAgentLoop` 只调用 policy，不再维护硬编码工具名列表和复杂判断细节。
4. 第一阶段保持行为基本等价，不引入动态策略表，不改变 Function Calling 协议。

## 非目标

- 不改工具参数校验协议。
- 不改工具执行返回结构。
- 不引入数据库策略配置表。
- 不改变 Agent Trace 表结构。
- 不做多 Agent 编排器或 DAG 编排器重构；这会放到后续阶段。

## 核心组件

### AgentStopPolicy

职责：

- 暴露 `endsAgentTurnAfterExecution(toolName)`。
- 判断某个工具成功执行后是否应该直接结束本轮 Agent。

当前 terminal tools 保持与旧 Loop 一致：

- `taxi_service`
- `reminder_create`
- `reminder_create_after`
- `reminder_update`
- `reminder_cancel`
- `reminder_complete`
- `reminder_snooze`
- `food_delivery`
- `meituan_travel`
- `email_send`
- `email_text_send`
- `browser_screenshot`
- `care_agent`

这些工具属于外部副作用、供应商结果权威、媒体结果直接交付或业务流程托管类工具，执行完成后不应让模型继续改写或重复触发。

### ToolExecutionPolicy

职责：

- `shouldSkipWebSearchBecauseRagHasEvidence(request, toolCall, arguments)`：RAG 已提供证据且用户没有要求最新/联网资料时，跳过 `web_search`。
- `voiceSynthesisSignature(toolName, arguments)`：对 `voice_synthesis` 生成语音去重签名。
- `toolCallSignature(toolName, arguments)`：对普通工具调用生成稳定去重签名。
- `toolFailureSignature(toolName, arguments, errorMessage)`：对失败工具调用生成稳定签名，避免同一失败无限重试。

RAG 跳过规则：

- 仅作用于 `web_search`。
- `request.ragContext()` 为空时不跳过。
- 用户当前消息或工具参数中出现“最新、最近、今天、现在、当前、实时、联网、互联网、网页、官网、新闻、价格、搜索、公开资料、latest、current、today、recent、web、internet、official、news、price”等标记时，不跳过。

签名规则：

- 普通工具签名使用工具名 + 排序后的参数字符串。
- 语音签名优先读取 `target_text`、`text`、`message`、`previous_result`，并加入 voice。
- 签名前会压缩连续空白，降低格式差异带来的重复调用。

## 接入方式

- 两个 policy 均作为 Spring Bean 注册。
- `FunctionCallingAgentLoop` 构造器通过 `ObjectProvider` 注入 policy，并为测试构造器提供默认实例。
- Loop 内部调用 policy 方法，删除原本散落在 Loop 中的停止策略、RAG 跳过策略和签名方法。

## 测试策略

- `AgentStopPolicyTests`：覆盖 terminal / non-terminal / 空值工具名。
- `ToolExecutionPolicyTests`：覆盖 RAG 跳过、最新/联网请求放行、语音签名、工具签名、失败签名。
- `FunctionCallingAgentLoopTests`：验证抽离后主循环行为仍保持通过。
- `ApplicationContextTests`：验证 Spring Bean 注入链路可启动。

## 后续演进

本阶段先完成代码级策略对象抽离。后续可以继续企业化演进：

1. 增加 `ToolCapabilityPolicy`，按工具元数据声明 terminal、side-effect、media-output、requires-confirmation 等能力。
2. 将策略接入配置中心或数据库，实现租户/渠道/工具级策略覆盖。
3. 在 Trace 中记录 policy decision，例如 skip reason、stop policy source、signature hash。
4. 引入更高层的 Agent Orchestrator，把单 Loop 扩展成计划、执行、反思、回滚等阶段化编排。
