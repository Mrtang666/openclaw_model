# Trace Audit Query Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add read-only query capability and a diagnostic API for Trace access audit events.

**Architecture:** Reuse the existing `agent_trace_access_audit` table. Add query DTOs, a query repository, a safe query service, and a controller endpoint under `/api/agent-runs/access-audit` protected by the existing trace access policy.

**Tech Stack:** Java 17, Spring Boot MVC, Spring JDBC, JUnit 5, MockMvc, Mockito, AssertJ.

---

## File Structure

- Create `src/main/java/com/example/spring/agent/trace/AgentTraceAccessAuditView.java`: audit row DTO.
- Create `src/main/java/com/example/spring/agent/trace/AgentTraceAccessAuditQueryRepository.java`: query repository contract.
- Create `src/main/java/com/example/spring/agent/trace/JdbcAgentTraceAccessAuditQueryRepository.java`: JDBC query implementation.
- Create `src/main/java/com/example/spring/agent/trace/AgentTraceAccessAuditQueryService.java`: safe query service.
- Modify `src/main/java/com/example/spring/agent/trace/AgentRunTraceController.java`: add `/access-audit`.
- Create `src/test/java/com/example/spring/agent/trace/JdbcAgentTraceAccessAuditQueryRepositoryTests.java`: repository tests.
- Create `src/test/java/com/example/spring/agent/trace/AgentTraceAccessAuditQueryServiceTests.java`: service tests.
- Modify `src/test/java/com/example/spring/agent/trace/AgentRunTraceControllerTests.java`: controller tests.

## Task 1: Audit Query Repository

**Files:**
- Create: `src/test/java/com/example/spring/agent/trace/JdbcAgentTraceAccessAuditQueryRepositoryTests.java`
- Create: `src/main/java/com/example/spring/agent/trace/AgentTraceAccessAuditView.java`
- Create: `src/main/java/com/example/spring/agent/trace/AgentTraceAccessAuditQueryRepository.java`
- Create: `src/main/java/com/example/spring/agent/trace/JdbcAgentTraceAccessAuditQueryRepository.java`

- [ ] **Step 1: Write failing repository tests**

Write tests that insert events with `JdbcAgentTraceAccessAuditRepository`, then query by target and actor.

- [ ] **Step 2: Run RED**

Run: `mvn "-Dtest=JdbcAgentTraceAccessAuditQueryRepositoryTests" test`

Expected: compilation failure because query classes do not exist.

- [ ] **Step 3: Implement minimal query repository**

Use SQL:

```sql
SELECT id, actor, action, target_type, target_key, allowed, reason,
       remote_address, user_agent, created_at
FROM agent_trace_access_audit
WHERE target_type = ? AND target_key = ?
ORDER BY created_at DESC, id DESC
LIMIT ?
```

and actor variant.

- [ ] **Step 4: Run GREEN**

Run: `mvn "-Dtest=JdbcAgentTraceAccessAuditQueryRepositoryTests" test`

Expected: tests pass.

## Task 2: Audit Query Service

**Files:**
- Create: `src/test/java/com/example/spring/agent/trace/AgentTraceAccessAuditQueryServiceTests.java`
- Create: `src/main/java/com/example/spring/agent/trace/AgentTraceAccessAuditQueryService.java`

- [ ] **Step 1: Write failing service tests**

Verify default limit 20, max limit 100, blank parameters return empty lists, repository failures are swallowed.

- [ ] **Step 2: Run RED**

Run: `mvn "-Dtest=AgentTraceAccessAuditQueryServiceTests" test`

Expected: compilation failure because service does not exist.

- [ ] **Step 3: Implement minimal service**

Implement:

```java
List<AgentTraceAccessAuditView> findRecentByTarget(String targetType, String targetKey, int limit)
List<AgentTraceAccessAuditView> findRecentByActor(String actor, int limit)
```

- [ ] **Step 4: Run GREEN**

Run: `mvn "-Dtest=AgentTraceAccessAuditQueryServiceTests" test`

Expected: tests pass.

## Task 3: Controller Endpoint

**Files:**
- Modify: `src/main/java/com/example/spring/agent/trace/AgentRunTraceController.java`
- Modify: `src/test/java/com/example/spring/agent/trace/AgentRunTraceControllerTests.java`

- [ ] **Step 1: Update controller tests first**

Add tests for target query, actor query, bad request, and forbidden request.

- [ ] **Step 2: Run RED**

Run: `mvn "-Dtest=AgentRunTraceControllerTests" test`

Expected: tests fail because `/access-audit` does not exist.

- [ ] **Step 3: Implement endpoint**

Add `GET /api/agent-runs/access-audit`. Reuse access policy and audit service. Return no-store response.

- [ ] **Step 4: Run GREEN**

Run: `mvn "-Dtest=AgentRunTraceControllerTests" test`

Expected: tests pass.

## Task 4: Verification and Commit

- [ ] **Step 1: Run focused verification**

Run: `mvn "-Dtest=JdbcAgentTraceAccessAuditQueryRepositoryTests,AgentTraceAccessAuditQueryServiceTests,AgentRunTraceControllerTests,AgentTraceAccessAuditServiceTests,JdbcAgentTraceAccessAuditRepositoryTests,ApplicationContextTests" test`

Expected: all selected tests pass.

- [ ] **Step 2: Run whitespace verification**

Run: `git diff --check`

Expected: no whitespace errors. CRLF warnings are acceptable in this repository.

- [ ] **Step 3: Review diff scope**

Run: `git diff --stat` and `git diff --name-only`

Expected: only trace audit query and documentation files changed.

- [ ] **Step 4: Commit**

```bash
git add docs/superpowers/specs/2026-08-03-enterprise-trace-audit-query-design.md docs/superpowers/plans/2026-08-03-enterprise-trace-audit-query.md src/main/java/com/example/spring/agent/trace src/test/java/com/example/spring/agent/trace
git commit -m "feat(orchestration): query trace access audit"
```
