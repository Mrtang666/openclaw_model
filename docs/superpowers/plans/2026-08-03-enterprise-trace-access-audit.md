# Trace Access Audit Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a lightweight access policy and audit trail for Agent Run Trace diagnostic APIs.

**Architecture:** Use an optional API key policy before trace queries. Record every allowed or denied trace query attempt into a dedicated audit table through a failure-tolerant audit service.

**Tech Stack:** Java 17, Spring Boot MVC, Spring JDBC, Flyway, JUnit 5, MockMvc, Mockito, AssertJ.

---

## File Structure

- Create `src/main/resources/db/migration/V36__create_agent_trace_access_audit.sql`: audit table.
- Create `src/main/java/com/example/spring/agent/trace/AgentTraceAccessDecision.java`: access decision DTO.
- Create `src/main/java/com/example/spring/agent/trace/AgentTraceAccessPolicy.java`: optional API key policy.
- Create `src/main/java/com/example/spring/agent/trace/AgentTraceAccessAuditEvent.java`: audit event DTO.
- Create `src/main/java/com/example/spring/agent/trace/AgentTraceAccessAuditRepository.java`: audit repository contract.
- Create `src/main/java/com/example/spring/agent/trace/JdbcAgentTraceAccessAuditRepository.java`: JDBC implementation.
- Create `src/main/java/com/example/spring/agent/trace/AgentTraceAccessAuditService.java`: safe audit facade.
- Modify `src/main/java/com/example/spring/agent/trace/AgentRunTraceController.java`: enforce policy and record audit.
- Create `src/test/java/com/example/spring/agent/trace/AgentTraceAccessPolicyTests.java`: policy tests.
- Create `src/test/java/com/example/spring/agent/trace/JdbcAgentTraceAccessAuditRepositoryTests.java`: repository tests.
- Create `src/test/java/com/example/spring/agent/trace/AgentTraceAccessAuditServiceTests.java`: service tests.
- Modify `src/test/java/com/example/spring/agent/trace/AgentRunTraceControllerTests.java`: controller policy/audit tests.

## Task 1: Access Policy

**Files:**
- Create: `src/test/java/com/example/spring/agent/trace/AgentTraceAccessPolicyTests.java`
- Create: `src/main/java/com/example/spring/agent/trace/AgentTraceAccessDecision.java`
- Create: `src/main/java/com/example/spring/agent/trace/AgentTraceAccessPolicy.java`

- [ ] **Step 1: Write failing policy tests**

Create tests for:

```java
@Test
void allowsAccessWhenApiKeyIsNotConfigured() {
    AgentTraceAccessDecision decision = new AgentTraceAccessPolicy("").authorize("");

    assertThat(decision.allowed()).isTrue();
    assertThat(decision.reason()).isEqualTo("API_KEY_NOT_CONFIGURED");
}
```

- [ ] **Step 2: Run RED**

Run: `mvn "-Dtest=AgentTraceAccessPolicyTests" test`

Expected: compilation failure because policy classes do not exist.

- [ ] **Step 3: Implement minimal policy**

Implement optional API key matching and reason codes.

- [ ] **Step 4: Run GREEN**

Run: `mvn "-Dtest=AgentTraceAccessPolicyTests" test`

Expected: tests pass.

## Task 2: Audit Repository and Service

**Files:**
- Create: `src/main/resources/db/migration/V36__create_agent_trace_access_audit.sql`
- Create: `src/test/java/com/example/spring/agent/trace/JdbcAgentTraceAccessAuditRepositoryTests.java`
- Create: `src/test/java/com/example/spring/agent/trace/AgentTraceAccessAuditServiceTests.java`
- Create: `src/main/java/com/example/spring/agent/trace/AgentTraceAccessAuditEvent.java`
- Create: `src/main/java/com/example/spring/agent/trace/AgentTraceAccessAuditRepository.java`
- Create: `src/main/java/com/example/spring/agent/trace/JdbcAgentTraceAccessAuditRepository.java`
- Create: `src/main/java/com/example/spring/agent/trace/AgentTraceAccessAuditService.java`

- [ ] **Step 1: Write failing audit tests**

Repository test inserts one allowed audit event and verifies database columns. Service test mocks repository failure and verifies no exception escapes.

- [ ] **Step 2: Run RED**

Run: `mvn "-Dtest=JdbcAgentTraceAccessAuditRepositoryTests,AgentTraceAccessAuditServiceTests" test`

Expected: compilation failure because audit classes/table do not exist.

- [ ] **Step 3: Implement migration, event, repository, service**

Add append-only audit write. Service catches runtime exceptions and logs warnings.

- [ ] **Step 4: Run GREEN**

Run: `mvn "-Dtest=JdbcAgentTraceAccessAuditRepositoryTests,AgentTraceAccessAuditServiceTests" test`

Expected: tests pass.

## Task 3: Controller Enforcement and Audit

**Files:**
- Modify: `src/main/java/com/example/spring/agent/trace/AgentRunTraceController.java`
- Modify: `src/test/java/com/example/spring/agent/trace/AgentRunTraceControllerTests.java`

- [ ] **Step 1: Update controller tests first**

Add tests for:

```java
@Test
void returnsForbiddenAndAuditsWhenAccessDenied() throws Exception {
    when(accessPolicy.authorize("bad")).thenReturn(new AgentTraceAccessDecision(false, "API_KEY_MISMATCH"));

    mockMvc.perform(get("/api/agent-runs/agent-run-1")
            .header("X-OpenClaw-Diagnostic-Key", "bad")
            .header("X-OpenClaw-Actor", "ops"))
        .andExpect(status().isForbidden());

    verify(auditService).record(any());
}
```

- [ ] **Step 2: Run RED**

Run: `mvn "-Dtest=AgentRunTraceControllerTests" test`

Expected: tests fail because controller does not enforce policy or record audit.

- [ ] **Step 3: Update controller**

Inject policy and audit service. Extract actor, remote address, user agent. Record audit before returning.

- [ ] **Step 4: Run GREEN**

Run: `mvn "-Dtest=AgentRunTraceControllerTests" test`

Expected: tests pass.

## Task 4: Verification and Commit

**Files:**
- Verify all files from previous tasks.

- [ ] **Step 1: Run focused verification**

Run: `mvn "-Dtest=AgentTraceAccessPolicyTests,JdbcAgentTraceAccessAuditRepositoryTests,AgentTraceAccessAuditServiceTests,AgentRunTraceControllerTests,AgentRunTraceQueryServiceTests,JdbcAgentRunQueryRepositoryTests,ApplicationContextTests" test`

Expected: all selected tests pass.

- [ ] **Step 2: Run whitespace verification**

Run: `git diff --check`

Expected: no whitespace errors. CRLF warnings are acceptable in this repository.

- [ ] **Step 3: Review diff scope**

Run: `git diff --stat` and `git diff --name-only`

Expected: only trace access/audit and documentation files changed.

- [ ] **Step 4: Commit**

```bash
git add docs/superpowers/specs/2026-08-03-enterprise-trace-access-audit-design.md docs/superpowers/plans/2026-08-03-enterprise-trace-access-audit.md src/main/resources/db/migration/V36__create_agent_trace_access_audit.sql src/main/java/com/example/spring/agent/trace src/test/java/com/example/spring/agent/trace
git commit -m "feat(orchestration): audit trace access"
```
