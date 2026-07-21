# Agent 记忆、工具边界、配置与包结构优化实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在不改变现有微信端 Agent 主流程的前提下，升级上下文记忆、补充媒体工具能力边界说明、整理配置密钥，并让旧的 prompt-json 模式与新的 function-calling 模式在包结构上更容易区分。

**Architecture:** 保留当前 `WechatConversationService -> ConversationToolPlanner -> FunctionCallingAgentLoop/旧规划器 -> WechatToolRegistry -> WechatTool` 的主链路。新增小型上下文构造器与工具能力模型，把复杂说明前置到工具定义和 Agent 请求上下文里；包结构采用兼容迁移，优先新增清晰包名和少量 import 调整，不做大规模重写。

**Tech Stack:** Java 17、Spring Boot、Maven、JUnit 5、Mockito、AssertJ、MySQL/Flyway、DashScope Function Calling。

---

### Task 1: 写上下文记忆构造测试

**Files:**
- Create: `src/test/java/com/example/spring/wechat/conversation/memory/WechatAgentMemoryContextBuilderTests.java`
- Create: `src/main/java/com/example/spring/wechat/conversation/memory/WechatAgentMemoryContextBuilder.java`

- [ ] **Step 1: Write the failing test**

测试目标：构造给 Agent 的上下文时，需要包含最近对话、滚动摘要、最近媒体摘要和工具调用摘要。

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q "-Dtest=WechatAgentMemoryContextBuilderTests" test`

Expected: FAIL，因为 `WechatAgentMemoryContextBuilder` 还不存在。

- [ ] **Step 3: Write minimal implementation**

实现一个只负责拼接上下文文本的小类，不直接访问数据库，避免与现有记忆服务强耦合。

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -q "-Dtest=WechatAgentMemoryContextBuilderTests" test`

Expected: PASS。

### Task 2: 工具能力边界说明

**Files:**
- Create: `src/main/java/com/example/spring/wechat/conversation/tools/WechatToolCapability.java`
- Modify: `src/main/java/com/example/spring/wechat/conversation/tools/WechatToolDefinition.java`
- Modify: `src/main/java/com/example/spring/wechat/conversation/tools/WechatTool.java`
- Modify: `src/main/java/com/example/spring/tool/protocol/function/FunctionCallingToolSchemaMapper.java`
- Test: `src/test/java/com/example/spring/wechat/conversation/tools/WechatToolRegistryTests.java`
- Test: `src/test/java/com/example/spring/tool/protocol/function/FunctionCallingToolSchemaMapperTests.java`

- [ ] **Step 1: Write failing tests**

测试目标：
- 工具定义中带有“能力、边界、需要追问的信息、输出形式”。
- Function Calling schema 的工具描述包含这些边界说明。

- [ ] **Step 2: Run tests to verify failure**

Run: `mvn -q "-Dtest=WechatToolRegistryTests,FunctionCallingToolSchemaMapperTests" test`

Expected: FAIL，因为能力模型还没有接入。

- [ ] **Step 3: Implement capability model**

为 `WechatTool` 增加默认 `capability()` 方法，旧工具不实现也能工作；媒体工具和天气工具逐个覆盖清晰边界说明。

- [ ] **Step 4: Run tests to verify pass**

Run: `mvn -q "-Dtest=WechatToolRegistryTests,FunctionCallingToolSchemaMapperTests" test`

Expected: PASS。

### Task 3: 配置和密钥整理

**Files:**
- Modify: `src/main/resources/application.properties`
- Create: `.env.example`
- Modify: `docs/PROJECT_STRUCTURE.md`

- [ ] **Step 1: Write documentation/config check**

通过静态检查确认 `.env.example` 包含 MySQL、DashScope、天气、Agent、语音和图片相关配置项。

- [ ] **Step 2: Implement grouped config**

按模块重排 `application.properties`，保留旧配置 key 的兼容写法，不把真实密钥写入示例文件。

- [ ] **Step 3: Verify**

Run: `mvn -q "-Dtest=ApplicationContextTests" test`

Expected: PASS。

### Task 4: 旧模式和新模式包结构区分

**Files:**
- Move: `src/main/java/com/example/spring/tool/protocol/ToolCallPlanner.java` -> `src/main/java/com/example/spring/tool/protocol/legacy/ToolCallPlanner.java`
- Move: `src/main/java/com/example/spring/tool/protocol/ToolCallPlanParser.java` -> `src/main/java/com/example/spring/tool/protocol/legacy/ToolCallPlanParser.java`
- Move: `src/main/java/com/example/spring/tool/protocol/ToolPlan.java` -> `src/main/java/com/example/spring/tool/protocol/legacy/ToolPlan.java`
- Move: `src/main/java/com/example/spring/tool/protocol/ToolCall.java` -> `src/main/java/com/example/spring/tool/protocol/legacy/ToolCall.java`
- Keep: `src/main/java/com/example/spring/tool/protocol/function/*`
- Keep: `src/main/java/com/example/spring/tool/protocol/validation/*`

- [ ] **Step 1: Compile to find import changes**

Run: `mvn -q "-DskipTests" test`

Expected: FAIL until imports are adjusted.

- [ ] **Step 2: Move classes and update imports**

把旧的 prompt-json 规划器移动到 `legacy` 包，新的 Function Calling 代码继续留在 `function` 包，校验器留在 `validation` 包。

- [ ] **Step 3: Run mode tests**

Run: `mvn -q "-Dtest=ToolCallPlannerTests,ConfigurableConversationToolPlannerTests,FunctionCallingAgentLoopTests" test`

Expected: PASS。

### Task 5: 全量验证

**Files:**
- All modified files.

- [ ] **Step 1: Run focused tests**

Run: `mvn -q "-Dtest=WechatAgentMemoryContextBuilderTests,WechatToolRegistryTests,FunctionCallingToolSchemaMapperTests,ConfigurableConversationToolPlannerTests,FunctionCallingAgentLoopTests" test`

Expected: PASS。

- [ ] **Step 2: Run full tests**

Run: `mvn -q test`

Expected: PASS。

- [ ] **Step 3: Report changed files and behavior**

说明改了哪些文件、为什么改、怎么验证。
