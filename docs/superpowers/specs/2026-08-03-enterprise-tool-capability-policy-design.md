# 企业级工具运行时能力策略设计

## 背景

前两个编排阶段已经完成：

1. Agent Run Trace 基础，用来记录模型轮次、工具调用、工具结果和停止原因。
2. Agent Loop Policy 抽离，用 `AgentStopPolicy` 与 `ToolExecutionPolicy` 承接停止判断、RAG 跳过与签名去重。

当前 `FunctionCallingAgentLoop` 里仍然残留一批“工具运行时能力”硬编码：

- 哪些工具执行失败时应被识别为失败结果。
- 哪些工具的可见输出应回传给微信用户。
- 哪些工具有专属运行时提示规则。
- 哪些工具执行后属于 terminal action。

项目里已有 `WechatToolCapability`，但它主要是“给模型看的工具说明”，用于拼入 Function Calling 工具 schema；本阶段新增的 `ToolCapabilityPolicy` 是“给 Harness/Loop 用的运行时策略”。二者职责不同，暂不合并，避免影响所有工具实现。

## 目标

1. 新增 `ToolCapabilityPolicy`，集中描述工具的运行时能力。
2. 让 `AgentStopPolicy` 基于 `ToolCapabilityPolicy` 判断 terminal tool，避免重复维护 terminal 工具集合。
3. 让 `FunctionCallingAgentLoop` 委托 `ToolCapabilityPolicy`：
   - 判断工具执行结果是否失败。
   - 过滤工具可见输出。
   - 渲染可用工具对应的运行时规则。
4. 保持现有行为基本等价，先不引入动态配置表。

## 非目标

- 不修改 `WechatTool` 接口。
- 不修改 `WechatToolCapability` 结构。
- 不要求每个具体工具类声明运行时 capability。
- 不引入数据库、配置中心或租户级覆盖。
- 不重写工具 schema 转换逻辑。

## 核心设计

### ToolCapabilityPolicy

位置：

`src/main/java/com/example/spring/wechat/conversation/agent/policy/ToolCapabilityPolicy.java`

职责：

- `endsAgentTurnAfterExecution(toolName)`：判断工具成功执行后是否直接结束 Agent。
- `isFailureReply(toolName, modelText)`：判断工具返回文本是否代表执行失败。
- `visibleParts(toolName, parts)`：根据工具能力过滤可见输出。
- `runtimeRules(availableToolNames)`：根据当前可用工具渲染工具运行时规则。

初始能力来源仍然是代码内静态规则，保持简单可审计。后续可以把这些规则迁移到工具声明、配置中心或数据库。

### AgentStopPolicy

`AgentStopPolicy` 保持对外 API 不变，但内部委托 `ToolCapabilityPolicy`。

这样做的好处是：

- 老调用方不用改。
- terminal 工具列表只有一处事实来源。
- 后续如果 terminal 能力配置化，只需要替换 `ToolCapabilityPolicy`。

### FunctionCallingAgentLoop

Loop 不再直接维护以下工具规则：

- `isToolFailureReply`
- `visibleParts`
- `availableToolRules`
- terminal 工具集合

Loop 仍然保留与编排强相关的逻辑，例如：

- 模型轮次控制。
- tool message 回写。
- trace 记录。
- 最终回复组装。
- 对 map 歧义结果进行用户澄清的业务收敛。

## 行为保持

本阶段迁移后的行为应与旧实现一致：

- `map_search` 返回 `地图查询失败：` 时视为失败。
- `reminder_*` 返回 `提醒操作未完成：` 时视为失败。
- `map_search` 带图片时保留图片和文本。
- `image_generation`、`voice_synthesis`、`document_generation`、`browser_screenshot` 只暴露图片、语音、文件等媒体输出。
- 非媒体工具默认不暴露可见 parts。
- 地图、知识库、网页、旅行、邮件、照护、提醒相关规则仍按可用工具条件拼入系统提示词。

## 测试策略

- `ToolCapabilityPolicyTests`
  - 覆盖 terminal / non-terminal 判断。
  - 覆盖失败文本识别。
  - 覆盖 map 可见输出策略。
  - 覆盖媒体工具输出策略。
  - 覆盖普通工具不暴露可见输出。
  - 覆盖 runtime rules 按可用工具渲染。
- `AgentStopPolicyTests`
  - 保持原有测试，验证委托后行为不变。
- `FunctionCallingAgentLoopTests`
  - 保持现有回归测试，验证 Loop 行为不变。
- `ApplicationContextTests`
  - 验证 Spring 注入链路正常。

## 后续演进

后续可以继续企业化：

1. 将 `ToolCapabilityPolicy` 的静态规则拆成 `ToolRuntimeCapability` 元数据对象。
2. 让具体 `WechatTool` 可选择声明运行时 capability。
3. 将 capability decision 写入 Agent Trace，记录为什么停止、为什么暴露媒体、为什么识别为失败。
4. 支持租户级或渠道级覆盖，例如微信端、CLI 端、Web 端使用不同可见输出策略。
