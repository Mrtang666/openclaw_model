# 知识库与网页 Agent 增强实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在不改变微信端 Function Calling 主框架的前提下，增强网页搜索、网页阅读、知识入库、知识检索和知识管理能力。

**Architecture:** 保持 `FunctionCallingAgentLoop → WechatToolRegistry → WechatTool` 的工具调用结构。新增资源上下文服务保存最近搜索/阅读资源；增强 `web_search`、`web_read`、`knowledge_query`、`knowledge_add`、`knowledge_manage` 的工具行为；知识库元数据增强尽量复用现有 `ChatService` 和 MySQL/Qdrant 组件。

**Tech Stack:** Java 17、Spring Boot、JUnit 5、Mockito、MySQL、Qdrant、DashScope/OpenAI-compatible Function Calling。

---

### Task 1: 搜索/阅读资源池

**Files:**
- Create: `src/main/java/com/example/spring/wechat/web/context/WebResourceContextService.java`
- Create: `src/main/java/com/example/spring/wechat/web/context/WebSearchSnapshot.java`
- Create: `src/main/java/com/example/spring/wechat/web/context/WebReadSnapshot.java`
- Test: `src/test/java/com/example/spring/wechat/web/context/WebResourceContextServiceTests.java`

- [ ] **Step 1: Write failing tests**
  - 验证每个用户最多保留最近 3 次搜索，每次最多 5 条。
  - 验证每个用户最多保留最近 5 个阅读网页。
  - 验证“第二个网页”“刚才那个”“上一个链接”可以解析到 URL。

- [ ] **Step 2: Run tests**
  - Run: `mvn -q "-Dtest=WebResourceContextServiceTests" test`
  - Expected: FAIL because resource context classes do not exist.

- [ ] **Step 3: Implement resource context service**
  - 使用进程内 `ConcurrentHashMap` 保存微信端临时资源引用。
  - 不保存网页全文，只保存标题、URL、摘要和时间。

- [ ] **Step 4: Run tests**
  - Run: `mvn -q "-Dtest=WebResourceContextServiceTests" test`
  - Expected: PASS.

### Task 2: web_search 来源可信度增强

**Files:**
- Modify: `src/main/java/com/example/spring/wechat/conversation/tools/WebSearchWechatTool.java`
- Test: `src/test/java/com/example/spring/wechat/conversation/tools/KnowledgeAndWebWechatToolTests.java`

- [ ] **Step 1: Write failing tests**
  - 验证搜索结果包含来源编号、标题、链接。
  - 验证搜索结果会写入资源池。
  - 验证用户要求保存时只保存搜索摘要到知识库。

- [ ] **Step 2: Run tests**
  - Run: `mvn -q "-Dtest=KnowledgeAndWebWechatToolTests" test`
  - Expected: FAIL for new resource context behavior.

- [ ] **Step 3: Implement**
  - 注入 `WebResourceContextService`。
  - `web_search` 执行后记录最近搜索结果。
  - 返回文本中使用“参考来源”结构。

- [ ] **Step 4: Run tests**
  - Run: `mvn -q "-Dtest=KnowledgeAndWebWechatToolTests" test`
  - Expected: PASS.

### Task 3: web_read 阅读增强与资源引用

**Files:**
- Modify: `src/main/java/com/example/spring/wechat/conversation/tools/WebReadWechatTool.java`
- Modify: `src/main/java/com/example/spring/wechat/web/service/WebContentExtractor.java`
- Test: `src/test/java/com/example/spring/wechat/conversation/tools/KnowledgeAndWebWechatToolTests.java`
- Test: `src/test/java/com/example/spring/wechat/web/service/WebContentExtractorTests.java`

- [ ] **Step 1: Write failing tests**
  - 验证 `url` 为空时可以根据“第二个网页/上一个链接”从资源池解析。
  - 验证阅读结果会写入最近阅读资源。
  - 验证正文清洗会去除 script/style/nav/footer 等噪声。

- [ ] **Step 2: Run tests**
  - Run: `mvn -q "-Dtest=KnowledgeAndWebWechatToolTests,WebContentExtractorTests" test`
  - Expected: FAIL for unresolved resource references and cleaner behavior.

- [ ] **Step 3: Implement**
  - `web_read` 支持从上下文资源池解析 URL。
  - 阅读后记录最近阅读网页。
  - 强化 HTML 清洗。

- [ ] **Step 4: Run tests**
  - Run: `mvn -q "-Dtest=KnowledgeAndWebWechatToolTests,WebContentExtractorTests" test`
  - Expected: PASS.

### Task 4: 知识入库质量增强

**Files:**
- Create: `src/main/java/com/example/spring/wechat/knowledge/service/KnowledgeMetadataEnhancer.java`
- Create: `src/main/java/com/example/spring/wechat/knowledge/model/KnowledgeMetadata.java`
- Modify: `src/main/java/com/example/spring/wechat/knowledge/service/KnowledgeIngestionService.java`
- Test: `src/test/java/com/example/spring/wechat/knowledge/service/KnowledgeIngestionAndSearchServiceTests.java`

- [ ] **Step 1: Write failing tests**
  - 验证网页/文档/长内容入库时会优化标题、摘要和标签。
  - 验证普通短文本仍走规则入库。

- [ ] **Step 2: Run tests**
  - Run: `mvn -q "-Dtest=KnowledgeIngestionAndSearchServiceTests" test`
  - Expected: FAIL for metadata enhancement.

- [ ] **Step 3: Implement**
  - 使用 `ChatService` 生成 JSON 元数据；失败时回退规则标题/标签。
  - 不阻塞原有入库流程。

- [ ] **Step 4: Run tests**
  - Run: `mvn -q "-Dtest=KnowledgeIngestionAndSearchServiceTests" test`
  - Expected: PASS.

### Task 5: 知识检索准确性增强

**Files:**
- Create: `src/main/java/com/example/spring/wechat/knowledge/service/KnowledgeQueryPlanner.java`
- Modify: `src/main/java/com/example/spring/wechat/knowledge/service/KnowledgeSearchService.java`
- Modify: `src/main/java/com/example/spring/wechat/conversation/tools/KnowledgeQueryWechatTool.java`
- Test: `src/test/java/com/example/spring/wechat/knowledge/service/KnowledgeSearchServiceTests.java`
- Test: `src/test/java/com/example/spring/wechat/conversation/tools/KnowledgeAndWebWechatToolTests.java`

- [ ] **Step 1: Write failing tests**
  - 验证用户问题会生成多个查询。
  - 验证多路召回结果会去重。
  - 验证低于阈值的结果不会被当作可靠知识。
  - 验证回复中带 document_id、来源和分数。

- [ ] **Step 2: Run tests**
  - Run: `mvn -q "-Dtest=KnowledgeSearchServiceTests,KnowledgeAndWebWechatToolTests" test`
  - Expected: FAIL for multi-query and threshold behavior.

- [ ] **Step 3: Implement**
  - 大模型查询改写失败时使用规则降级。
  - 按 documentId + chunkIndex 去重。
  - 默认阈值可配置。

- [ ] **Step 4: Run tests**
  - Run: `mvn -q "-Dtest=KnowledgeSearchServiceTests,KnowledgeAndWebWechatToolTests" test`
  - Expected: PASS.

### Task 6: 知识库管理产品化

**Files:**
- Modify: `src/main/java/com/example/spring/wechat/conversation/tools/KnowledgeManageWechatTool.java`
- Modify: `src/main/java/com/example/spring/wechat/knowledge/service/KnowledgeManageService.java`
- Modify: `src/main/java/com/example/spring/wechat/knowledge/repository/KnowledgeRepository.java`
- Modify: `src/main/java/com/example/spring/wechat/knowledge/repository/MySqlKnowledgeRepository.java`
- Test: `src/test/java/com/example/spring/wechat/conversation/tools/KnowledgeAndWebWechatToolTests.java`

- [ ] **Step 1: Write failing tests**
  - 验证按标签、来源、标题搜索。
  - 验证修改标题、修改标签。
  - 验证删除、批量删除、重新向量化首次调用只返回确认提示。
  - 验证用户确认后执行。

- [ ] **Step 2: Run tests**
  - Run: `mvn -q "-Dtest=KnowledgeAndWebWechatToolTests" test`
  - Expected: FAIL for enhanced manage operations.

- [ ] **Step 3: Implement**
  - 扩展 operation 参数。
  - 通过 `WechatConversationMemory` 的 pending clarification 保存确认状态。
  - 风险操作需要二次确认。

- [ ] **Step 4: Run tests**
  - Run: `mvn -q "-Dtest=KnowledgeAndWebWechatToolTests" test`
  - Expected: PASS.

### Task 7: 文档与全量验证

**Files:**
- Modify: `README.md`
- Modify: `.env.example`

- [ ] **Step 1: Update docs**
  - 说明资源池、来源引用、知识库管理命令、风险操作确认。

- [ ] **Step 2: Run final verification**
  - Run: `mvn -q test`
  - Run: `git diff --check`
  - Expected: Both exit 0.
