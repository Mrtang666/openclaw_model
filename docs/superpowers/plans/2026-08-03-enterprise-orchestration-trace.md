# Enterprise Orchestration Trace Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add persistent Agent Run and Agent Step tracing to the Function Calling orchestration path.

**Architecture:** Introduce a small `com.example.spring.agent.trace` module with DTOs, repository, JDBC implementation, and a safe facade service. Integrate it into `FunctionCallingAgentLoop` without changing tool behavior; trace failures degrade to no-op.

**Tech Stack:** Java 17, Spring Boot, JdbcTemplate, Flyway, JUnit 5, Mockito, Maven.

---

### Task 1: Trace schema and repository

**Files:**
- Create: `src/main/resources/db/migration/V35__create_agent_trace_tables.sql`
- Create: `src/main/java/com/example/spring/agent/trace/AgentRunStatus.java`
- Create: `src/main/java/com/example/spring/agent/trace/AgentRunStepType.java`
- Create: `src/main/java/com/example/spring/agent/trace/AgentRunStepStatus.java`
- Create: `src/main/java/com/example/spring/agent/trace/AgentRunHandle.java`
- Create: `src/main/java/com/example/spring/agent/trace/AgentRunRepository.java`
- Create: `src/main/java/com/example/spring/agent/trace/JdbcAgentRunRepository.java`
- Test: `src/test/java/com/example/spring/agent/trace/JdbcAgentRunRepositoryTests.java`

- [ ] Write failing repository tests for creating a run, appending steps, and completing the run.
- [ ] Run `mvn "-Dtest=JdbcAgentRunRepositoryTests" test` and confirm missing classes/schema fail.
- [ ] Add migration and repository implementation.
- [ ] Re-run `mvn "-Dtest=JdbcAgentRunRepositoryTests" test` and confirm pass.

### Task 2: Safe trace service facade

**Files:**
- Create: `src/main/java/com/example/spring/agent/trace/AgentRunTraceService.java`
- Test: `src/test/java/com/example/spring/agent/trace/AgentRunTraceServiceTests.java`

- [ ] Write failing tests proving normal trace calls delegate to repository.
- [ ] Write failing tests proving repository failures are swallowed and return a no-op handle.
- [ ] Implement the trace service.
- [ ] Run `mvn "-Dtest=AgentRunTraceServiceTests" test` and confirm pass.

### Task 3: Function Calling Loop integration

**Files:**
- Modify: `src/main/java/com/example/spring/wechat/conversation/agent/FunctionCallingAgentLoop.java`
- Modify: `src/test/java/com/example/spring/wechat/conversation/agent/FunctionCallingAgentLoopTests.java`

- [ ] Write failing test proving a run is started and completed for a final-answer path.
- [ ] Write failing test proving tool calls and stop reason are traced.
- [ ] Integrate `AgentRunTraceService` into the loop through optional constructor injection.
- [ ] Run `mvn "-Dtest=FunctionCallingAgentLoopTests" test` and confirm pass.

### Task 4: Verification and commit

**Files:**
- Verify all changed code.

- [ ] Run `mvn "-Dtest=JdbcAgentRunRepositoryTests,AgentRunTraceServiceTests,FunctionCallingAgentLoopTests,ApplicationContextTests" test`.
- [ ] Run `git diff --check`.
- [ ] Commit with `feat(orchestration): add agent run trace foundation`.
