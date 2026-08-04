# Trace Diagnostic Redaction Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a redacted diagnostic view layer for Agent Run Trace APIs so HTTP responses do not expose raw trace text by default.

**Architecture:** Keep internal trace query DTOs unchanged. Add a reusable redaction policy, diagnostic DTOs, a mapper from internal views to diagnostic views, and update the controller to return diagnostic views.

**Tech Stack:** Java 17, Spring Boot MVC, JUnit 5, AssertJ, MockMvc, Mockito.

---

## File Structure

- Create `src/main/java/com/example/spring/agent/trace/AgentTraceRedactionPolicy.java`: text redaction and truncation policy.
- Create `src/main/java/com/example/spring/agent/trace/AgentRunDiagnosticStepView.java`: redacted step DTO.
- Create `src/main/java/com/example/spring/agent/trace/AgentRunDiagnosticTraceView.java`: redacted full run DTO.
- Create `src/main/java/com/example/spring/agent/trace/AgentRunDiagnosticSummaryView.java`: redacted summary DTO.
- Create `src/main/java/com/example/spring/agent/trace/AgentRunDiagnosticMapper.java`: mapper from internal DTOs to diagnostic DTOs.
- Modify `src/main/java/com/example/spring/agent/trace/AgentRunTraceController.java`: return diagnostic DTOs.
- Create `src/test/java/com/example/spring/agent/trace/AgentTraceRedactionPolicyTests.java`: redaction unit tests.
- Create `src/test/java/com/example/spring/agent/trace/AgentRunDiagnosticMapperTests.java`: mapping tests.
- Modify `src/test/java/com/example/spring/agent/trace/AgentRunTraceControllerTests.java`: verify API redacts sensitive fields.

## Task 1: Redaction Policy

**Files:**
- Create: `src/test/java/com/example/spring/agent/trace/AgentTraceRedactionPolicyTests.java`
- Create: `src/main/java/com/example/spring/agent/trace/AgentTraceRedactionPolicy.java`

- [ ] **Step 1: Write failing redaction tests**

Create tests for:

```java
@Test
void redactsEmailPhoneAndSensitiveKeyValues() {
    AgentTraceRedactionPolicy policy = new AgentTraceRedactionPolicy();

    String redacted = policy.redact("email=alice@example.com phone=13812345678 token=abc123");

    assertThat(redacted).contains("a***@example.com");
    assertThat(redacted).contains("138****5678");
    assertThat(redacted).contains("token=[REDACTED]");
    assertThat(redacted).doesNotContain("alice@example.com", "abc123");
}
```

- [ ] **Step 2: Run test to verify RED**

Run: `mvn "-Dtest=AgentTraceRedactionPolicyTests" test`

Expected: compilation failure because `AgentTraceRedactionPolicy` does not exist.

- [ ] **Step 3: Implement minimal policy**

Implement regex-based redaction for email, long numbers, sensitive key-values, and 512-character truncation.

- [ ] **Step 4: Run tests to verify GREEN**

Run: `mvn "-Dtest=AgentTraceRedactionPolicyTests" test`

Expected: tests pass.

## Task 2: Diagnostic Mapper

**Files:**
- Create: `src/test/java/com/example/spring/agent/trace/AgentRunDiagnosticMapperTests.java`
- Create: `src/main/java/com/example/spring/agent/trace/AgentRunDiagnosticStepView.java`
- Create: `src/main/java/com/example/spring/agent/trace/AgentRunDiagnosticTraceView.java`
- Create: `src/main/java/com/example/spring/agent/trace/AgentRunDiagnosticSummaryView.java`
- Create: `src/main/java/com/example/spring/agent/trace/AgentRunDiagnosticMapper.java`

- [ ] **Step 1: Write failing mapper tests**

Create tests that map a trace containing phone/email/token/password values and assert diagnostic views contain redacted strings.

- [ ] **Step 2: Run test to verify RED**

Run: `mvn "-Dtest=AgentRunDiagnosticMapperTests" test`

Expected: compilation failure because mapper and DTOs do not exist.

- [ ] **Step 3: Implement DTOs and mapper**

Add immutable records and a Spring `@Component` mapper.

- [ ] **Step 4: Run mapper tests to verify GREEN**

Run: `mvn "-Dtest=AgentRunDiagnosticMapperTests" test`

Expected: tests pass.

## Task 3: Controller Uses Diagnostic Views

**Files:**
- Modify: `src/main/java/com/example/spring/agent/trace/AgentRunTraceController.java`
- Modify: `src/test/java/com/example/spring/agent/trace/AgentRunTraceControllerTests.java`

- [ ] **Step 1: Update controller tests first**

Update tests so API responses do not contain raw sensitive values and do contain redacted values.

- [ ] **Step 2: Run controller tests to verify RED**

Run: `mvn "-Dtest=AgentRunTraceControllerTests" test`

Expected: tests fail because current controller still returns raw internal DTOs.

- [ ] **Step 3: Update controller**

Inject `AgentRunDiagnosticMapper` and return `AgentRunDiagnosticTraceView` / `AgentRunDiagnosticSummaryView`.

- [ ] **Step 4: Run controller tests to verify GREEN**

Run: `mvn "-Dtest=AgentRunTraceControllerTests" test`

Expected: tests pass.

## Task 4: Verification and Commit

**Files:**
- Verify all files from previous tasks.

- [ ] **Step 1: Run focused verification**

Run: `mvn "-Dtest=AgentTraceRedactionPolicyTests,AgentRunDiagnosticMapperTests,AgentRunTraceControllerTests,AgentRunTraceQueryServiceTests,JdbcAgentRunQueryRepositoryTests,ApplicationContextTests" test`

Expected: all selected tests pass.

- [ ] **Step 2: Run whitespace verification**

Run: `git diff --check`

Expected: no whitespace errors. CRLF warnings are acceptable in this repository.

- [ ] **Step 3: Review diff scope**

Run: `git diff --stat` and `git diff --name-only`

Expected: only trace diagnostic redaction and documentation files changed.

- [ ] **Step 4: Commit**

```bash
git add docs/superpowers/specs/2026-08-03-enterprise-trace-diagnostic-redaction-design.md docs/superpowers/plans/2026-08-03-enterprise-trace-diagnostic-redaction.md src/main/java/com/example/spring/agent/trace src/test/java/com/example/spring/agent/trace
git commit -m "feat(orchestration): redact trace diagnostics"
```
