# 企业级编排决策 Trace 实现计划

目标：把关键编排策略决策写入 Agent Trace，让系统不仅能看到“发生了什么”，也能看到“为什么这么做”。

架构：复用现有 `agent_run_steps` 表，新增 `AgentRunStepType.POLICY_DECISION`。在 `AgentRunTraceService` 增加 `recordPolicyDecision(...)`，由 `FunctionCallingAgentLoop` 在重复调用跳过、RAG 跳过 web_search、terminal tool 停止、重复失败终止等分支记录决策事件。

技术栈：Java 17、Spring Boot、JUnit 5、Mockito、AssertJ、Maven。

## Task 1：Trace Service 支持 policy decision

文件：

- 修改：`src/main/java/com/example/spring/agent/trace/AgentRunStepType.java`
- 修改：`src/main/java/com/example/spring/agent/trace/AgentRunTraceService.java`
- 测试：`src/test/java/com/example/spring/agent/trace/AgentRunTraceServiceTests.java`

步骤：

- [x] 先写 `recordPolicyDecision(...)` 的失败测试。
- [x] 运行 `mvn "-Dtest=AgentRunTraceServiceTests" test`，确认缺少方法或枚举失败。
- [x] 新增 `POLICY_DECISION` step type。
- [x] 实现 `AgentRunTraceService.recordPolicyDecision(...)`。
- [x] 再次运行 `mvn "-Dtest=AgentRunTraceServiceTests" test` 并确认通过。

## Task 2：Repository 写入 policy decision

文件：

- 测试：`src/test/java/com/example/spring/agent/trace/JdbcAgentRunRepositoryTests.java`

步骤：

- [x] 增加 `POLICY_DECISION` 写入测试。
- [x] 运行 `mvn "-Dtest=JdbcAgentRunRepositoryTests" test`。

## Task 3：Agent Loop 记录关键决策

文件：

- 修改：`src/main/java/com/example/spring/wechat/conversation/agent/FunctionCallingAgentLoop.java`
- 测试：`src/test/java/com/example/spring/wechat/conversation/agent/FunctionCallingAgentLoopTests.java`

步骤：

- [x] 在重复工具调用跳过分支记录 `SKIP_DUPLICATE_TOOL_CALL`。
- [x] 在重复语音合成跳过分支记录 `SKIP_DUPLICATE_VOICE_SYNTHESIS`。
- [x] 在 RAG 跳过 `web_search` 分支记录 `SKIP_WEB_SEARCH_RAG_EVIDENCE`。
- [x] 在 terminal tool 停止分支记录 `END_TURN_AFTER_TERMINAL_TOOL`。
- [x] 在重复失败签名终止分支记录 `STOP_REPEATED_TOOL_FAILURE`。
- [x] 增加/更新 Loop 测试，验证关键分支调用 `recordPolicyDecision(...)`。
- [x] 运行 `mvn "-Dtest=FunctionCallingAgentLoopTests" test`。

## Task 4：验证与提交

步骤：

- [x] 运行 `mvn "-Dtest=AgentRunTraceServiceTests,JdbcAgentRunRepositoryTests,FunctionCallingAgentLoopTests,ApplicationContextTests" test`。
- [x] 运行 `git diff --check`。
- [ ] 提交：`feat(orchestration): trace policy decisions`。
