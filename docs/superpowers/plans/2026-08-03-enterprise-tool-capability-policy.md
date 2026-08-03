# 企业级工具运行时能力策略实现计划

目标：从 `FunctionCallingAgentLoop` 中抽出运行时工具能力策略，新增 `ToolCapabilityPolicy`，并保持现有行为稳定。

架构：在 `com.example.spring.wechat.conversation.agent.policy` 包下新增 `ToolCapabilityPolicy`。`AgentStopPolicy` 委托它判断 terminal 工具，`FunctionCallingAgentLoop` 委托它完成失败结果识别、可见输出过滤和运行时工具规则渲染。

技术栈：Java 17、Spring Boot、JUnit 5、AssertJ、Maven。

## Task 1：ToolCapabilityPolicy 测试与实现

文件：

- 新增：`src/main/java/com/example/spring/wechat/conversation/agent/policy/ToolCapabilityPolicy.java`
- 测试：`src/test/java/com/example/spring/wechat/conversation/agent/policy/ToolCapabilityPolicyTests.java`

步骤：

- [x] 先写 terminal / non-terminal 工具判断的失败测试。
- [x] 先写失败结果识别测试：
  - `map_search` + `地图查询失败：...` 应识别为失败。
  - `reminder_create` + `提醒操作未完成：...` 应识别为失败。
  - 普通文本不应识别为失败。
- [x] 先写可见输出过滤测试：
  - `map_search` 带图片时保留图片和非空文本。
  - 媒体工具保留图片、语音、文件。
  - 普通工具不暴露可见 parts。
- [x] 先写运行时工具规则测试：
  - 只有可用 `map_search` 时才渲染地图规则。
  - 只有可用 `email_send` 或 `email_text_send` 时才渲染邮件规则。
  - 可用提醒工具时渲染提醒规则。
- [x] 运行 `mvn "-Dtest=ToolCapabilityPolicyTests" test`，确认因缺少生产类失败。
- [x] 实现 `ToolCapabilityPolicy`。
- [x] 再次运行 `mvn "-Dtest=ToolCapabilityPolicyTests" test` 并确认通过。

## Task 2：AgentStopPolicy 委托 ToolCapabilityPolicy

文件：

- 修改：`src/main/java/com/example/spring/wechat/conversation/agent/policy/AgentStopPolicy.java`
- 测试：`src/test/java/com/example/spring/wechat/conversation/agent/policy/AgentStopPolicyTests.java`

步骤：

- [x] 保留 terminal 工具行为测试。
- [x] 将 `AgentStopPolicy` 内部 terminal 集合迁移到 `ToolCapabilityPolicy`。
- [x] 让 `AgentStopPolicy` 委托 `ToolCapabilityPolicy.endsAgentTurnAfterExecution(...)`。
- [x] 运行 `mvn "-Dtest=AgentStopPolicyTests,ToolCapabilityPolicyTests" test` 并确认通过。

## Task 3：FunctionCallingAgentLoop 集成

文件：

- 修改：`src/main/java/com/example/spring/wechat/conversation/agent/FunctionCallingAgentLoop.java`
- 回归：`src/test/java/com/example/spring/wechat/conversation/agent/FunctionCallingAgentLoopTests.java`

步骤：

- [x] 向 `FunctionCallingAgentLoop` 注入 `ToolCapabilityPolicy`。
- [x] 将 `isToolFailureReply(...)` 替换为 `toolCapabilityPolicy.isFailureReply(...)`。
- [x] 将 `visibleParts(...)` 替换为 `toolCapabilityPolicy.visibleParts(...)`。
- [x] 将 `availableToolRules(...)` 和提醒相关 runtime rules 替换为 `toolCapabilityPolicy.runtimeRules(...)`。
- [x] 删除 Loop 内已无调用的工具能力私有方法。
- [x] 运行 `mvn "-Dtest=FunctionCallingAgentLoopTests" test` 并确认通过。

## Task 4：验证与提交

步骤：

- [x] 运行 `mvn "-Dtest=ToolCapabilityPolicyTests,AgentStopPolicyTests,ToolExecutionPolicyTests,FunctionCallingAgentLoopTests,ApplicationContextTests" test`。
- [x] 运行 `git diff --check`。
- [ ] 提交：`feat(orchestration): add tool capability policy`。
