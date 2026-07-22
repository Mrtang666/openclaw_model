# 完整 Function Calling Agent Loop 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将当前 `function-calling` 从“只做工具规划”升级为完整的“模型调用工具 → Java 执行工具 → 工具结果回传模型 → 模型继续决策 → 最终回复”的 Agent 循环。

**Architecture:** 保留 `prompt-json` 作为旧版兜底；`function-calling` 直接代表完整 Agent Loop，不再保留中间规划模式。新增 Function Calling 消息/响应模型、Agent Loop 执行器，并让微信主流程在 function-calling 配置下进入循环执行。

**Tech Stack:** Java 17、Spring Boot、RestClient、Jackson、JUnit 5、Mockito、AssertJ。

---

### Task 1: 新增 Function Calling 响应模型和解析能力

**Files:**
- Create: `src/main/java/com/example/spring/tool/protocol/function/FunctionCallingMessage.java`
- Create: `src/main/java/com/example/spring/tool/protocol/function/FunctionCallingToolCall.java`
- Create: `src/main/java/com/example/spring/tool/protocol/function/FunctionCallingModelResponse.java`
- Modify: `src/main/java/com/example/spring/tool/protocol/function/FunctionCallingResponseParser.java`
- Test: `src/test/java/com/example/spring/tool/protocol/function/FunctionCallingResponseParserTests.java`

- [ ] **Step 1: Write the failing test**

新增测试：解析 assistant 的 `tool_calls` 时保留 `id`、`function.name`、`function.arguments`；解析无工具调用的 assistant 文本时返回 final content。

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q "-Dtest=FunctionCallingResponseParserTests" test`

Expected: FAIL because `parseModelResponse` and new model classes do not exist.

- [ ] **Step 3: Write minimal implementation**

新增消息、工具调用、模型响应 record，并在 parser 中新增 `parseModelResponse(String responseBody)`。

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -q "-Dtest=FunctionCallingResponseParserTests" test`

Expected: PASS.

### Task 2: 扩展百炼 Function Calling 客户端支持多轮 messages

**Files:**
- Modify: `src/main/java/com/example/spring/tool/protocol/function/DashScopeFunctionCallingClient.java`
- Test: `src/test/java/com/example/spring/tool/protocol/function/DashScopeFunctionCallingClientTests.java`

- [ ] **Step 1: Write the failing test**

新增测试：客户端可以接收完整 messages 数组，发送 `tools` 和 `tool_choice=auto`，并解析最终 assistant content。

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q "-Dtest=DashScopeFunctionCallingClientTests" test`

Expected: FAIL because `chat(List<FunctionCallingMessage>, List<WechatToolDefinition>)` does not exist.

- [ ] **Step 3: Write minimal implementation**

在客户端中新增 `chat(...)` 方法，旧的 `planDecision(...)` 保留兼容，但内部可复用新方法。

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -q "-Dtest=DashScopeFunctionCallingClientTests" test`

Expected: PASS.

### Task 3: 新增完整 Agent Loop 执行器

**Files:**
- Create: `src/main/java/com/example/spring/wechat/conversation/agent/FunctionCallingAgentLoop.java`
- Test: `src/test/java/com/example/spring/wechat/conversation/agent/FunctionCallingAgentLoopTests.java`

- [ ] **Step 1: Write the failing test**

测试一个两轮场景：第一轮模型返回 `weather` tool call；Java 执行 fake weather 工具；第二轮模型根据 tool result 返回最终文本“杭州今天适合出门”。

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q "-Dtest=FunctionCallingAgentLoopTests" test`

Expected: FAIL because `FunctionCallingAgentLoop` does not exist.

- [ ] **Step 3: Write minimal implementation**

循环最多执行 `agent.tool-calling.max-loop-rounds` 轮。每轮调用模型；若有 tool calls，按顺序执行工具，把工具结果转成 tool message 再进入下一轮；若无 tool calls，则返回最终文本和已收集的媒体 parts。

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -q "-Dtest=FunctionCallingAgentLoopTests" test`

Expected: PASS.

### Task 4: 微信主流程接入完整 function-calling 循环

**Files:**
- Modify: `src/main/java/com/example/spring/wechat/conversation/WechatConversationService.java`
- Modify: `src/main/resources/application.properties`
- Test: `src/test/java/com/example/spring/wechat/conversation/WechatConversationServiceTests.java`

- [ ] **Step 1: Write the failing test**

测试当配置为 `function-calling` 时，微信主流程使用完整 Agent Loop 的最终回复，而不是只返回工具原始结果。

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q "-Dtest=WechatConversationServiceTests" test`

Expected: FAIL because主流程尚未接入 Agent Loop。

- [ ] **Step 3: Write minimal implementation**

给 `WechatConversationService` 注入 `FunctionCallingAgentLoop` 和 `agent.tool-calling.mode`，当模式为 `function-calling` 时优先走 Agent Loop；`prompt-json` 仍走旧规划流程。

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -q "-Dtest=WechatConversationServiceTests" test`

Expected: PASS.

### Task 5: 全量验证

**Files:**
- No new files.

- [ ] **Step 1: Run focused tests**

Run:

```bash
mvn -q "-Dtest=FunctionCallingResponseParserTests,DashScopeFunctionCallingClientTests,FunctionCallingAgentLoopTests,WechatConversationServiceTests" test
```

Expected: PASS.

- [ ] **Step 2: Run full suite**

Run:

```bash
mvn -q test
```

Expected: PASS.

- [ ] **Step 3: Do not commit**

用户已明确表示提交仓库由自己完成，因此本计划不执行 git commit。
