# RAG Message Processing Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add an automatic RAG retrieval and context-injection layer before WeChat messages are sent to the function-calling LLM loop.

**Architecture:** Add a focused `wechat/conversation/rag` package containing configuration, retrieval orchestration, and context formatting. Pass the formatted RAG context through a new `FunctionCallingAgentRequest.ragContext` field so `FunctionCallingAgentLoop` can keep memory context and knowledge context separate in the prompt.

**Tech Stack:** Java 17, Spring Boot 3.4, JUnit 5, AssertJ, Mockito, existing Qdrant/DashScope knowledge services.

---

## File Structure

- Create `src/main/java/com/example/spring/wechat/conversation/rag/RagProperties.java`: bind `rag.*` settings with safe defaults.
- Create `src/main/java/com/example/spring/wechat/conversation/rag/RagContextFormatter.java`: format `KnowledgeSearchResult` hits into a prompt-safe context block.
- Create `src/main/java/com/example/spring/wechat/conversation/rag/WechatRagContextService.java`: decide whether to retrieve, call `KnowledgeSearchService`, filter/format hits, and fail open.
- Modify `src/main/java/com/example/spring/AgentClawApplication.java`: enable `RagProperties`.
- Modify `src/main/resources/application.properties`: add `rag.*` defaults.
- Modify `src/main/java/com/example/spring/wechat/conversation/agent/FunctionCallingAgentRequest.java`: add `ragContext` and compatible constructors.
- Modify `src/main/java/com/example/spring/wechat/conversation/agent/FunctionCallingAgentLoop.java`: add prompt section and system rules for RAG context.
- Modify `src/main/java/com/example/spring/wechat/conversation/WechatConversationService.java`: inject and call `WechatRagContextService` before the agent loop.
- Test `src/test/java/com/example/spring/wechat/conversation/rag/RagPropertiesTests.java`.
- Test `src/test/java/com/example/spring/wechat/conversation/rag/RagContextFormatterTests.java`.
- Test `src/test/java/com/example/spring/wechat/conversation/rag/WechatRagContextServiceTests.java`.
- Modify tests in `src/test/java/com/example/spring/wechat/conversation/agent/FunctionCallingAgentLoopTests.java`.
- Modify tests in `src/test/java/com/example/spring/wechat/conversation/WechatConversationServiceTests.java`.
- Modify test resources if needed.

## Baseline

- [ ] **Step 1: Run focused baseline tests**

Run:

```powershell
mvn -q "-Dtest=FunctionCallingAgentLoopTests,WechatConversationServiceTests,KnowledgeAndWebWechatToolTests" test
```

Expected: build succeeds before feature edits. If this fails, inspect whether failures are unrelated baseline failures before proceeding.

## Task 1: RAG Configuration

**Files:**
- Create: `src/main/java/com/example/spring/wechat/conversation/rag/RagProperties.java`
- Modify: `src/main/java/com/example/spring/AgentClawApplication.java`
- Modify: `src/main/resources/application.properties`
- Test: `src/test/java/com/example/spring/wechat/conversation/rag/RagPropertiesTests.java`

- [ ] **Step 1: Write failing properties tests**

Create tests that instantiate:

```java
new RagProperties(true, true, 5, 0.2, 6000, true)
new RagProperties(false, false, 0, -1, 0, false)
```

Assert defaults and normalization:

```java
assertThat(properties.topK()).isEqualTo(5);
assertThat(properties.minScore()).isEqualTo(0.2);
assertThat(properties.maxContextChars()).isEqualTo(6000);
```

- [ ] **Step 2: Run red test**

Run:

```powershell
mvn -q "-Dtest=RagPropertiesTests" test
```

Expected: FAIL because `RagProperties` does not exist.

- [ ] **Step 3: Implement configuration**

Add `RagProperties` with `@ConfigurationProperties(prefix = "rag")`, safe constructor defaults, application binding, and properties entries:

```properties
rag.enabled=${RAG_ENABLED:true}
rag.auto-retrieve=${RAG_AUTO_RETRIEVE:true}
rag.top-k=${RAG_TOP_K:${KNOWLEDGE_TOP_K:5}}
rag.min-score=${RAG_MIN_SCORE:${KNOWLEDGE_MIN_SCORE:0.2}}
rag.max-context-chars=${RAG_MAX_CONTEXT_CHARS:${KNOWLEDGE_MAX_CONTEXT_CHARS:6000}}
rag.include-sources=${RAG_INCLUDE_SOURCES:true}
```

- [ ] **Step 4: Run green test**

Run:

```powershell
mvn -q "-Dtest=RagPropertiesTests" test
```

Expected: PASS.

## Task 2: RAG Context Formatting

**Files:**
- Create: `src/main/java/com/example/spring/wechat/conversation/rag/RagContextFormatter.java`
- Test: `src/test/java/com/example/spring/wechat/conversation/rag/RagContextFormatterTests.java`

- [ ] **Step 1: Write failing formatter tests**

Test behaviors:

```java
String context = formatter.format(List.of(result), 2000, true);
assertThat(context).contains("knowledge_context", "[知识1]", "document_id=1", "chunk_index=2", "0.910", "来源：https://example.com/a", "知识库片段是事实资料");
```

Also test empty result returns `""` and small max chars truncates content while retaining metadata.

- [ ] **Step 2: Run red test**

Run:

```powershell
mvn -q "-Dtest=RagContextFormatterTests" test
```

Expected: FAIL because formatter does not exist.

- [ ] **Step 3: Implement formatter**

Create `format(List<KnowledgeSearchResult> results, int maxContextChars, boolean includeSources)` that:

- returns empty for null/empty results;
- emits a header with injection boundary text;
- numbers chunks as `[知识N]`;
- formats score with `Locale.ROOT` and `%.3f`;
- includes source URL only when configured and nonblank;
- respects max chars by truncating content fields.

- [ ] **Step 4: Run green test**

Run:

```powershell
mvn -q "-Dtest=RagContextFormatterTests" test
```

Expected: PASS.

## Task 3: RAG Retrieval Service

**Files:**
- Create: `src/main/java/com/example/spring/wechat/conversation/rag/WechatRagContextService.java`
- Test: `src/test/java/com/example/spring/wechat/conversation/rag/WechatRagContextServiceTests.java`

- [ ] **Step 1: Write failing service tests**

Test behaviors:

```java
when(searchService.search("user-1", "项目流程是什么", 5, "")).thenReturn(List.of(result));
String context = service.build("user-1", "项目流程是什么");
assertThat(context).contains("[知识1]");
```

Also assert blank text, `#new`, short acknowledgements, tool-intent messages, disabled config, and search exceptions return `""`.

- [ ] **Step 2: Run red test**

Run:

```powershell
mvn -q "-Dtest=WechatRagContextServiceTests" test
```

Expected: FAIL because service does not exist.

- [ ] **Step 3: Implement service**

Implement `build(String sessionKey, String userText)`:

- return `""` if disabled, auto retrieve disabled, or skipped;
- call `KnowledgeSearchService.search(sessionKey, userText, properties.topK(), "")`;
- filter results by `properties.minScore()`;
- pass filtered results to formatter;
- catch `RuntimeException`, log warning, and return `""`.

- [ ] **Step 4: Run green test**

Run:

```powershell
mvn -q "-Dtest=WechatRagContextServiceTests" test
```

Expected: PASS.

## Task 4: Agent Request and Prompt Integration

**Files:**
- Modify: `src/main/java/com/example/spring/wechat/conversation/agent/FunctionCallingAgentRequest.java`
- Modify: `src/main/java/com/example/spring/wechat/conversation/agent/FunctionCallingAgentLoop.java`
- Test: `src/test/java/com/example/spring/wechat/conversation/agent/FunctionCallingAgentLoopTests.java`

- [ ] **Step 1: Write failing agent tests**

Add a test that creates a request with RAG context:

```java
new FunctionCallingAgentRequest("user-1", "项目流程", "history", "[知识1]\n内容：Function Calling", List.of(), List.of(), List.of(), null, null, null)
```

Capture first model call messages and assert the user message contains `知识库检索结果`, `[知识1]`, and `项目流程`.

- [ ] **Step 2: Run red test**

Run:

```powershell
mvn -q "-Dtest=FunctionCallingAgentLoopTests" test
```

Expected: FAIL because request has no `ragContext` field or prompt section.

- [ ] **Step 3: Implement request and prompt changes**

Add `ragContext` field with normalization, keep existing constructors by passing `""`, add RAG prompt section only when nonblank, and add system rules for using knowledge context safely.

- [ ] **Step 4: Run green test**

Run:

```powershell
mvn -q "-Dtest=FunctionCallingAgentLoopTests" test
```

Expected: PASS.

## Task 5: Conversation Service Integration

**Files:**
- Modify: `src/main/java/com/example/spring/wechat/conversation/WechatConversationService.java`
- Test: `src/test/java/com/example/spring/wechat/conversation/WechatConversationServiceTests.java`

- [ ] **Step 1: Write failing integration tests**

Add tests that inject a fake `WechatRagContextService` and fake `FunctionCallingAgentLoop`:

- normal function-calling text includes RAG context in request;
- RAG build failure does not block agent loop;
- `#new` does not call RAG.

- [ ] **Step 2: Run red test**

Run:

```powershell
mvn -q "-Dtest=WechatConversationServiceTests" test
```

Expected: FAIL because `WechatConversationService` does not inject or call the RAG service.

- [ ] **Step 3: Implement integration**

Inject `ObjectProvider<WechatRagContextService>` with a no-op fallback or nullable field, compute `ragContext` immediately before `FunctionCallingAgentRequest`, and pass it to the new request constructor.

- [ ] **Step 4: Run green test**

Run:

```powershell
mvn -q "-Dtest=WechatConversationServiceTests" test
```

Expected: PASS.

## Task 6: Final Verification

- [ ] **Step 1: Run focused regression suite**

Run:

```powershell
mvn -q "-Dtest=RagPropertiesTests,RagContextFormatterTests,WechatRagContextServiceTests,FunctionCallingAgentLoopTests,WechatConversationServiceTests,KnowledgeAndWebWechatToolTests,ApplicationContextTests" test
```

Expected: PASS.

- [ ] **Step 2: Inspect diff**

Run:

```powershell
git diff --check
git status --short
```

Expected: no whitespace errors; only intended files changed.

- [ ] **Step 3: Commit implementation**

Run:

```powershell
git add src/main/java/com/example/spring/AgentClawApplication.java src/main/resources/application.properties src/main/java/com/example/spring/wechat/conversation src/test/java/com/example/spring/wechat/conversation docs/superpowers/plans/2026-07-29-rag-message-processing.md
git commit -m "feat: add rag message context injection"
```

Expected: commit succeeds.
