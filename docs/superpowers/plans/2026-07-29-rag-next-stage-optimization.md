# RAG Next Stage Optimization Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Upgrade the existing RAG layer with query rewrite, multi-query retrieval, reranking, and evidence-pack assembly before the LLM sees the context.

**Architecture:** Keep `WechatRagContextService` as the orchestration entrypoint. Add small focused services for query expansion, relevance scoring, and evidence-pack formatting, then wire them together so the conversation path still fails open and still reuses `KnowledgeSearchService` and Qdrant.

**Tech Stack:** Java 17, Spring Boot 3.4, JUnit 5, AssertJ, Mockito, existing DashScope/ChatService, existing Qdrant vector store.

---

### Task 1: Query Rewrite and Multi-Query Planning

**Files:**
- Create: `src/main/java/com/example/spring/wechat/conversation/rag/RagQueryPlanner.java`
- Modify: `src/main/java/com/example/spring/wechat/conversation/rag/WechatRagContextService.java`
- Test: `src/test/java/com/example/spring/wechat/conversation/rag/RagQueryPlannerTests.java`

- [ ] **Step 1: Write the failing test**

```java
@Test
void expandsQuestionIntoMultipleSearchQueries() {
    List<String> queries = planner.plan("这个项目的 Function Calling 流程是什么？");
    assertThat(queries).hasSizeBetween(2, 3);
    assertThat(queries).anyMatch(value -> value.contains("Function Calling"));
}
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```powershell
mvn -q "-Dtest=RagQueryPlannerTests" test
```

Expected: FAIL because `RagQueryPlanner` does not exist yet.

- [ ] **Step 3: Write minimal implementation**

```java
public List<String> plan(String question) {
    String text = normalize(question);
    return List.of(text, compact(text), rewritten(text)).stream()
            .filter(value -> !value.isBlank())
            .distinct()
            .limit(3)
            .toList();
}
```

- [ ] **Step 4: Run test to verify it passes**

Run:

```powershell
mvn -q "-Dtest=RagQueryPlannerTests" test
```

Expected: PASS.

### Task 2: Rerank and Evidence Pack

**Files:**
- Create: `src/main/java/com/example/spring/wechat/conversation/rag/RagRerankService.java`
- Create: `src/main/java/com/example/spring/wechat/conversation/rag/RagEvidencePackBuilder.java`
- Modify: `src/main/java/com/example/spring/wechat/conversation/rag/RagContextFormatter.java`
- Test: `src/test/java/com/example/spring/wechat/conversation/rag/RagRerankServiceTests.java`
- Test: `src/test/java/com/example/spring/wechat/conversation/rag/RagEvidencePackBuilderTests.java`

- [ ] **Step 1: Write the failing tests**

```java
@Test
void reranksByScoreAndTitleOverlap() {
    List<KnowledgeSearchResult> ranked = rerankService.rank("Function Calling 流程", List.of(lowMatch, highMatch));
    assertThat(ranked.get(0).title()).isEqualTo("Function Calling 设计");
}
```

```java
@Test
void buildsDeduplicatedEvidencePack() {
    String pack = evidencePackBuilder.build(List.of(dup1, dup2), 1200, true);
    assertThat(pack).contains("[知识1]").doesNotContain("重复两次的同一段");
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run:

```powershell
mvn -q "-Dtest=RagRerankServiceTests,RagEvidencePackBuilderTests" test
```

Expected: FAIL because the new classes are missing.

- [ ] **Step 3: Write minimal implementation**

Implement a deterministic reranker that scores title overlap, content overlap, and source priority, then have the evidence pack builder deduplicate by `documentId + chunkIndex`, merge neighbors, and cap total length.

- [ ] **Step 4: Run tests to verify they pass**

Run:

```powershell
mvn -q "-Dtest=RagRerankServiceTests,RagEvidencePackBuilderTests" test
```

Expected: PASS.

### Task 3: Wire Optimized Retrieval

**Files:**
- Modify: `src/main/java/com/example/spring/wechat/conversation/rag/WechatRagContextService.java`
- Modify: `src/main/java/com/example/spring/wechat/conversation/rag/RagContextFormatter.java`
- Test: `src/test/java/com/example/spring/wechat/conversation/rag/WechatRagContextServiceTests.java`

- [ ] **Step 1: Write the failing test**

```java
@Test
void usesRewriteRerankAndEvidencePackTogether() {
    String context = service.build("user-1", "这个项目的 Function Calling 流程是什么？");
    assertThat(context).contains("[知识1]").contains("Function Calling");
}
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```powershell
mvn -q "-Dtest=WechatRagContextServiceTests#usesRewriteRerankAndEvidencePackTogether" test
```

Expected: FAIL because the service does not yet orchestrate the new helpers.

- [ ] **Step 3: Write minimal implementation**

Call `RagQueryPlanner.plan(...)`, aggregate retrieval results, run `RagRerankService.rank(...)`, and pass the final evidence list to `RagEvidencePackBuilder.build(...)`.

- [ ] **Step 4: Run test to verify it passes**

Run:

```powershell
mvn -q "-Dtest=WechatRagContextServiceTests" test
```

Expected: PASS.

### Task 4: Regression and Commit

**Files:**
- Modify: `src/test/java/com/example/spring/wechat/conversation/WechatConversationServiceTests.java` if needed
- Modify: `src/test/java/com/example/spring/wechat/conversation/agent/FunctionCallingAgentLoopTests.java` if needed

- [ ] **Step 1: Run the focused regression suite**

Run:

```powershell
mvn -q "-Dtest=RagQueryPlannerTests,RagRerankServiceTests,RagEvidencePackBuilderTests,WechatRagContextServiceTests,FunctionCallingAgentLoopTests,WechatConversationServiceTests,ApplicationContextTests" test
```

Expected: PASS.

- [ ] **Step 2: Run diff checks**

Run:

```powershell
git diff --check
git status --short
```

Expected: only intended files changed, no whitespace errors.

- [ ] **Step 3: Commit**

Run:

```powershell
git add docs/superpowers/plans/2026-07-29-rag-next-stage-optimization.md src/main/java/com/example/spring/wechat/conversation/rag src/test/java/com/example/spring/wechat/conversation/rag src/main/java/com/example/spring/wechat/conversation/WechatConversationService.java src/main/java/com/example/spring/wechat/conversation/agent/FunctionCallingAgentLoop.java src/main/java/com/example/spring/wechat/conversation/agent/FunctionCallingAgentRequest.java
git commit -m "feat: optimize rag retrieval quality"
```

Expected: commit succeeds.
