# Trace Query Service Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a read-only backend query layer for Agent Run Trace so recorded orchestration traces can be retrieved by run key and session key.

**Architecture:** Keep the existing trace write path unchanged. Add read DTOs, a query repository interface, a JDBC implementation, and a service facade that normalizes inputs and safely falls back on query failures.

**Tech Stack:** Java 17, Spring Boot, Spring JDBC, JUnit 5, AssertJ, Mockito, MySQL test profile.

---

## File Structure

- Create `src/main/java/com/example/spring/agent/trace/AgentRunStepView.java`: immutable DTO for a single trace step.
- Create `src/main/java/com/example/spring/agent/trace/AgentRunTraceView.java`: immutable DTO for one run plus ordered steps.
- Create `src/main/java/com/example/spring/agent/trace/AgentRunSummaryView.java`: immutable DTO for recent run list entries.
- Create `src/main/java/com/example/spring/agent/trace/AgentRunQueryRepository.java`: read-only query contract.
- Create `src/main/java/com/example/spring/agent/trace/JdbcAgentRunQueryRepository.java`: Spring JDBC implementation.
- Create `src/main/java/com/example/spring/agent/trace/AgentRunTraceQueryService.java`: safe service facade.
- Create `src/test/java/com/example/spring/agent/trace/JdbcAgentRunQueryRepositoryTests.java`: integration tests for SQL query behavior.
- Create `src/test/java/com/example/spring/agent/trace/AgentRunTraceQueryServiceTests.java`: unit tests for parameter handling and failure fallback.

## Task 1: Repository Query Contract and JDBC Behavior

**Files:**
- Create: `src/test/java/com/example/spring/agent/trace/JdbcAgentRunQueryRepositoryTests.java`
- Create: `src/main/java/com/example/spring/agent/trace/AgentRunStepView.java`
- Create: `src/main/java/com/example/spring/agent/trace/AgentRunTraceView.java`
- Create: `src/main/java/com/example/spring/agent/trace/AgentRunSummaryView.java`
- Create: `src/main/java/com/example/spring/agent/trace/AgentRunQueryRepository.java`
- Create: `src/main/java/com/example/spring/agent/trace/JdbcAgentRunQueryRepository.java`

- [ ] **Step 1: Write failing repository tests**

Write tests that create runs through existing `JdbcAgentRunRepository`, then query through the new repository:

```java
@Test
void findsRunWithOrderedStepsByRunKey() {
    AgentRunHandle handle = writer.createRun("WECHAT", "session-a", "hello", "context");
    writer.appendStep(handle, AgentRunStepType.TOOL_CALL, AgentRunStepStatus.STARTED, 1, "weather", "city=hz", "", Map.of());
    writer.appendStep(handle, AgentRunStepType.TOOL_RESULT, AgentRunStepStatus.SUCCESS, 1, "weather", "city=hz", "sunny", Map.of("source", "tool"));
    writer.completeRun(handle, AgentRunStatus.SUCCEEDED, "FINAL_ANSWER", "ok");

    Optional<AgentRunTraceView> result = reader.findRun(handle.runKey());

    assertThat(result).isPresent();
    assertThat(result.get().runKey()).isEqualTo(handle.runKey());
    assertThat(result.get().steps()).extracting(AgentRunStepView::stepIndex).containsExactly(1, 2);
}
```

- [ ] **Step 2: Run test to verify RED**

Run: `mvn "-Dtest=JdbcAgentRunQueryRepositoryTests" test`

Expected: compilation failure because `JdbcAgentRunQueryRepository` and DTOs do not exist.

- [ ] **Step 3: Add minimal DTOs and query implementation**

Implement records and JDBC SQL:

```sql
SELECT * FROM agent_runs WHERE run_key = ?
SELECT * FROM agent_run_steps WHERE run_id = ? ORDER BY step_index ASC
SELECT * FROM agent_runs WHERE session_key = ? ORDER BY started_at DESC, id DESC LIMIT ?
```

- [ ] **Step 4: Run repository tests to verify GREEN**

Run: `mvn "-Dtest=JdbcAgentRunQueryRepositoryTests" test`

Expected: all tests pass.

## Task 2: Safe Query Service Facade

**Files:**
- Create: `src/test/java/com/example/spring/agent/trace/AgentRunTraceQueryServiceTests.java`
- Create: `src/main/java/com/example/spring/agent/trace/AgentRunTraceQueryService.java`

- [ ] **Step 1: Write failing service tests**

Write tests for input normalization, limit clamping, and repository failure fallback:

```java
@Test
void clampsRecentRunsLimitAndDelegatesToRepository() {
    AgentRunQueryRepository repository = mock(AgentRunQueryRepository.class);
    AgentRunTraceQueryService service = new AgentRunTraceQueryService(repository);

    service.findRecentRuns(" session ", 999);

    verify(repository).findRecentRuns("session", 100);
}
```

- [ ] **Step 2: Run test to verify RED**

Run: `mvn "-Dtest=AgentRunTraceQueryServiceTests" test`

Expected: compilation failure because `AgentRunTraceQueryService` does not exist.

- [ ] **Step 3: Implement minimal service**

Implement:

```java
public Optional<AgentRunTraceView> findRun(String runKey)
public List<AgentRunSummaryView> findRecentRuns(String sessionKey, int limit)
```

Use default limit 20 and max limit 100.

- [ ] **Step 4: Run service tests to verify GREEN**

Run: `mvn "-Dtest=AgentRunTraceQueryServiceTests" test`

Expected: all tests pass.

## Task 3: Verification and Commit

**Files:**
- Verify all files from Task 1 and Task 2.

- [ ] **Step 1: Run focused verification**

Run: `mvn "-Dtest=JdbcAgentRunQueryRepositoryTests,AgentRunTraceQueryServiceTests,JdbcAgentRunRepositoryTests,AgentRunTraceServiceTests,ApplicationContextTests" test`

Expected: all selected tests pass.

- [ ] **Step 2: Run whitespace verification**

Run: `git diff --check`

Expected: no whitespace errors. CRLF warnings are acceptable in this repository.

- [ ] **Step 3: Review git diff**

Run: `git diff --stat` and `git diff -- src/main/java/com/example/spring/agent/trace src/test/java/com/example/spring/agent/trace docs/superpowers`

Expected: only trace query and documentation files changed.

- [ ] **Step 4: Commit**

```bash
git add docs/superpowers/specs/2026-08-03-enterprise-trace-query-design.md docs/superpowers/plans/2026-08-03-enterprise-trace-query.md src/main/java/com/example/spring/agent/trace src/test/java/com/example/spring/agent/trace
git commit -m "feat(orchestration): add trace query service"
```
