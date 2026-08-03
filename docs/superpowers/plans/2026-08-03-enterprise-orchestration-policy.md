# 企业级编排策略层实现计划

目标：从 `FunctionCallingAgentLoop` 中抽出停止策略与工具执行策略，并保持主循环行为稳定。

架构：新增 `com.example.spring.wechat.conversation.agent.policy` 包。Loop 继续作为执行器，policy 负责工具停止判断、RAG/web_search 跳过判断、工具调用签名、语音去重签名和失败签名。

技术栈：Java 17、Spring Boot、JUnit 5、AssertJ、Maven。

## Task 1：AgentStopPolicy

文件：

- 新增：`src/main/java/com/example/spring/wechat/conversation/agent/policy/AgentStopPolicy.java`
- 测试：`src/test/java/com/example/spring/wechat/conversation/agent/policy/AgentStopPolicyTests.java`

步骤：

- [x] 先写 terminal tools 与 non-terminal tools 的失败测试。
- [x] 运行 `mvn "-Dtest=AgentStopPolicyTests" test`，确认因缺少生产类失败。
- [x] 实现 `AgentStopPolicy`。
- [x] 再次运行测试并确认通过。

## Task 2：ToolExecutionPolicy

文件：

- 新增：`src/main/java/com/example/spring/wechat/conversation/agent/policy/ToolExecutionPolicy.java`
- 测试：`src/test/java/com/example/spring/wechat/conversation/agent/policy/ToolExecutionPolicyTests.java`

步骤：

- [x] 先写 RAG 已有证据时跳过 `web_search` 的失败测试。
- [x] 先写用户要求最新/联网资料时允许 `web_search` 的失败测试。
- [x] 先写语音合成签名、工具调用签名、失败签名的失败测试。
- [x] 实现 `ToolExecutionPolicy`。
- [x] 运行 `mvn "-Dtest=ToolExecutionPolicyTests" test` 并确认通过。

## Task 3：Loop 集成

文件：

- 修改：`src/main/java/com/example/spring/wechat/conversation/agent/FunctionCallingAgentLoop.java`
- 回归：`src/test/java/com/example/spring/wechat/conversation/agent/FunctionCallingAgentLoopTests.java`

步骤：

- [x] 将两个 policy 注入 Loop。
- [x] 用 policy 调用替换 Loop 内部私有策略方法。
- [x] 运行 `mvn "-Dtest=FunctionCallingAgentLoopTests" test`，确认行为未破坏。

## Task 4：验证与提交

步骤：

- [x] 运行 `mvn "-Dtest=AgentStopPolicyTests,ToolExecutionPolicyTests,FunctionCallingAgentLoopTests,ApplicationContextTests" test`。
- [x] 运行 `git diff --check`。
- [ ] 提交：`feat(orchestration): extract agent loop policies`。
