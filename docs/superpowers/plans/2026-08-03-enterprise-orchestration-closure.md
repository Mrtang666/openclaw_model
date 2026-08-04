# Enterprise Orchestration Closure Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 一次完成编排系统收口：Trace 统计摘要和中文运行手册。

**Architecture:** 新增 `AgentRunStatsCalculator` 从 step 列表派生统计；诊断详情和最近列表都输出 `stats`。仓储最近列表查询保持现有 run 查询结构，再为每个 run 查询 steps 计算统计，避免新增表和复杂迁移。新增中文 runbook 固化系统边界、接口和排障路径。

**Tech Stack:** Java 17, Spring Boot MVC, JDBC, JUnit 5, AssertJ, MockMvc, Maven。

---

## 文件结构

- Create: `src/main/java/com/example/spring/agent/trace/AgentRunDiagnosticStatsView.java`
- Create: `src/main/java/com/example/spring/agent/trace/AgentRunStatsCalculator.java`
- Create: `src/test/java/com/example/spring/agent/trace/AgentRunStatsCalculatorTests.java`
- Modify: `src/main/java/com/example/spring/agent/trace/AgentRunSummaryView.java`
- Modify: `src/main/java/com/example/spring/agent/trace/AgentRunDiagnosticSummaryView.java`
- Modify: `src/main/java/com/example/spring/agent/trace/AgentRunDiagnosticTraceView.java`
- Modify: `src/main/java/com/example/spring/agent/trace/AgentRunDiagnosticMapper.java`
- Modify: `src/main/java/com/example/spring/agent/trace/JdbcAgentRunQueryRepository.java`
- Modify tests under `src/test/java/com/example/spring/agent/trace/`
- Create: `docs/orchestration/enterprise-orchestration-runbook.zh-CN.md`

## Task 1: Stats calculator

- [ ] **Step 1: Write failing test**

Create `AgentRunStatsCalculatorTests` and assert counts for mixed steps.

- [ ] **Step 2: Run red test**

Run:

```powershell
mvn "-Dtest=AgentRunStatsCalculatorTests" test
```

Expected: compile failure because stats classes do not exist.

- [ ] **Step 3: Implement calculator**

Create `AgentRunDiagnosticStatsView` and `AgentRunStatsCalculator`.

- [ ] **Step 4: Run green test**

Run:

```powershell
mvn "-Dtest=AgentRunStatsCalculatorTests" test
```

Expected: tests pass.

## Task 2: Diagnostic mapper and API stats

- [ ] **Step 1: Update tests first**

Update mapper and controller tests to expect `stats`.

- [ ] **Step 2: Run red tests**

Run:

```powershell
mvn "-Dtest=AgentRunDiagnosticMapperTests,AgentRunTraceControllerTests" test
```

Expected: failures until diagnostic records and mapper are updated.

- [ ] **Step 3: Implement diagnostic stats fields**

Add `stats` fields and use calculator in mapper.

- [ ] **Step 4: Run green tests**

Run:

```powershell
mvn "-Dtest=AgentRunDiagnosticMapperTests,AgentRunTraceControllerTests" test
```

Expected: tests pass.

## Task 3: Recent run summary stats from repository

- [ ] **Step 1: Update repository test first**

Update `JdbcAgentRunQueryRepositoryTests.findsRecentRunsBySessionKeyNewestFirst` to insert steps for the returned run and assert `summary.stats()`.

- [ ] **Step 2: Run red test**

Run:

```powershell
mvn "-Dtest=JdbcAgentRunQueryRepositoryTests" test
```

Expected: failure until repository summary stats are populated.

- [ ] **Step 3: Implement repository stats loading**

Load ordered steps for each run and calculate stats with `AgentRunStatsCalculator`.

- [ ] **Step 4: Run green test**

Run:

```powershell
mvn "-Dtest=JdbcAgentRunQueryRepositoryTests" test
```

Expected: tests pass.

## Task 4: Runbook

- [ ] **Step 1: Create runbook**

Create `docs/orchestration/enterprise-orchestration-runbook.zh-CN.md` with architecture, API, troubleshooting, and extension rules.

- [ ] **Step 2: Placeholder scan**

Run:

```powershell
rg "未定|待补充|稍后实现" docs/orchestration/enterprise-orchestration-runbook.zh-CN.md
```

Expected: no matches.

## Task 5: Verification and commit

- [ ] **Step 1: Run focused regression**

Run:

```powershell
mvn "-Dtest=AgentRunStatsCalculatorTests,AgentRunPhaseClassifierTests,AgentRunDiagnosticMapperTests,AgentRunTraceControllerTests,AgentTraceAccessAuditControllerTests,JdbcAgentRunQueryRepositoryTests,AgentRunTraceQueryServiceTests,ApplicationContextTests" test
```

Expected: selected tests pass.

- [ ] **Step 2: Run diff checks**

Run:

```powershell
git diff --check
git diff --cached --check
```

Expected: no whitespace errors. CRLF warnings are acceptable if exit code is 0.

- [ ] **Step 3: Commit**

Run:

```powershell
git add docs src/main/java/com/example/spring/agent/trace src/test/java/com/example/spring/agent/trace
git commit -m "feat(orchestration): close enterprise trace diagnostics"
```
