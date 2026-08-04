# Enterprise Trace Phase View Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在 Trace 诊断响应中增加运行阶段视图，让 Agent Run 从底层 step 日志升级为可理解的执行轨迹。

**Architecture:** 新增 `AgentRunStepPhase` 和 `AgentRunPhaseClassifier`，用只读派生方式把 `AgentRunStepType` 分类为阶段，并把连续相同阶段聚合为 phase 片段。`AgentRunDiagnosticMapper` 在输出诊断视图时填充 step 级 `stepPhase` 和 trace 级 `phases`，不修改数据库和写入链路。

**Tech Stack:** Java 17, Spring Boot MVC, JUnit 5, AssertJ, MockMvc, Maven。

---

## 文件结构

- Create: `src/main/java/com/example/spring/agent/trace/AgentRunStepPhase.java`
  - 运行阶段枚举。
- Create: `src/main/java/com/example/spring/agent/trace/AgentRunDiagnosticPhaseView.java`
  - Trace 诊断响应中的 phase 聚合片段。
- Create: `src/main/java/com/example/spring/agent/trace/AgentRunPhaseClassifier.java`
  - step type 分类与 phase 聚合。
- Create: `src/test/java/com/example/spring/agent/trace/AgentRunPhaseClassifierTests.java`
  - 纯单元测试分类与聚合规则。
- Modify: `src/main/java/com/example/spring/agent/trace/AgentRunDiagnosticStepView.java`
  - 增加 `stepPhase` 字段。
- Modify: `src/main/java/com/example/spring/agent/trace/AgentRunDiagnosticTraceView.java`
  - 增加 `phases` 字段。
- Modify: `src/main/java/com/example/spring/agent/trace/AgentRunDiagnosticMapper.java`
  - 注入/使用 classifier。
- Modify: `src/test/java/com/example/spring/agent/trace/AgentRunDiagnosticMapperTests.java`
  - 验证诊断视图新增阶段字段。
- Modify: `src/test/java/com/example/spring/agent/trace/AgentRunTraceControllerTests.java`
  - 验证 HTTP JSON 暴露阶段字段。

## Task 1: 阶段分类器

- [ ] **Step 1: Write the failing test**

创建 `src/test/java/com/example/spring/agent/trace/AgentRunPhaseClassifierTests.java`：

```java
package com.example.spring.agent.trace;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AgentRunPhaseClassifierTests {

    private final AgentRunPhaseClassifier classifier = new AgentRunPhaseClassifier();

    @Test
    void classifiesStepTypesIntoOperationalPhases() {
        assertThat(classifier.classify(AgentRunStepType.MODEL_ROUND)).isEqualTo(AgentRunStepPhase.MODEL);
        assertThat(classifier.classify(AgentRunStepType.TOOL_CALL)).isEqualTo(AgentRunStepPhase.TOOL);
        assertThat(classifier.classify(AgentRunStepType.TOOL_RESULT)).isEqualTo(AgentRunStepPhase.TOOL);
        assertThat(classifier.classify(AgentRunStepType.POLICY_DECISION)).isEqualTo(AgentRunStepPhase.POLICY);
        assertThat(classifier.classify(AgentRunStepType.STOP)).isEqualTo(AgentRunStepPhase.TERMINAL);
    }

    @Test
    void groupsOnlyContiguousStepsWithSamePhase() {
        List<AgentRunDiagnosticPhaseView> phases = classifier.phases(List.of(
                step(1, AgentRunStepType.MODEL_ROUND, AgentRunStepStatus.SUCCESS),
                step(2, AgentRunStepType.TOOL_CALL, AgentRunStepStatus.SUCCESS),
                step(3, AgentRunStepType.TOOL_RESULT, AgentRunStepStatus.SUCCESS),
                step(4, AgentRunStepType.POLICY_DECISION, AgentRunStepStatus.SKIPPED),
                step(5, AgentRunStepType.MODEL_ROUND, AgentRunStepStatus.SUCCESS)));

        assertThat(phases).extracting(AgentRunDiagnosticPhaseView::phase)
                .containsExactly(
                        AgentRunStepPhase.MODEL,
                        AgentRunStepPhase.TOOL,
                        AgentRunStepPhase.POLICY,
                        AgentRunStepPhase.MODEL);
        assertThat(phases.get(1).startStepIndex()).isEqualTo(2);
        assertThat(phases.get(1).endStepIndex()).isEqualTo(3);
        assertThat(phases.get(1).stepCount()).isEqualTo(2);
        assertThat(phases.get(2).status()).isEqualTo(AgentRunStepStatus.SKIPPED);
    }

    @Test
    void aggregatesPhaseStatusWithFailurePriority() {
        List<AgentRunDiagnosticPhaseView> phases = classifier.phases(List.of(
                step(1, AgentRunStepType.TOOL_CALL, AgentRunStepStatus.SUCCESS),
                step(2, AgentRunStepType.TOOL_RESULT, AgentRunStepStatus.FAILED)));

        assertThat(phases).hasSize(1);
        assertThat(phases.get(0).phase()).isEqualTo(AgentRunStepPhase.TOOL);
        assertThat(phases.get(0).status()).isEqualTo(AgentRunStepStatus.FAILED);
    }

    private AgentRunStepView step(int index, AgentRunStepType type, AgentRunStepStatus status) {
        return new AgentRunStepView(
                index,
                index,
                type,
                1,
                "",
                status,
                "",
                "",
                "",
                Instant.parse("2026-08-03T06:00:00Z"));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```powershell
mvn "-Dtest=AgentRunPhaseClassifierTests" test
```

Expected: compile failure because `AgentRunPhaseClassifier`, `AgentRunStepPhase`, and `AgentRunDiagnosticPhaseView` do not exist.

- [ ] **Step 3: Write minimal implementation**

Create the enum, phase view record, and classifier implementation.

- [ ] **Step 4: Run test to verify it passes**

Run:

```powershell
mvn "-Dtest=AgentRunPhaseClassifierTests" test
```

Expected: tests pass.

## Task 2: 诊断 Mapper 输出阶段字段

- [ ] **Step 1: Update mapper tests first**

修改 `AgentRunDiagnosticMapperTests`：

- `mapsTraceToRedactedDiagnosticView` 断言 `step.stepPhase()` 是 `TOOL`。
- 断言 `diagnostic.phases()` 包含一个 `TOOL` phase。

- [ ] **Step 2: Run test to verify it fails**

Run:

```powershell
mvn "-Dtest=AgentRunDiagnosticMapperTests" test
```

Expected: compile failure because diagnostic records still没有新字段。

- [ ] **Step 3: Update diagnostic records and mapper**

- `AgentRunDiagnosticStepView` 增加 `AgentRunStepPhase stepPhase`。
- `AgentRunDiagnosticTraceView` 增加 `List<AgentRunDiagnosticPhaseView> phases`。
- `AgentRunDiagnosticMapper` 新增 `AgentRunPhaseClassifier` 字段，默认构造函数创建 classifier。
- 映射 step 时调用 `classifier.classify(step.stepType())`。
- 映射 trace 时调用 `classifier.phases(trace.steps())`。

- [ ] **Step 4: Run test to verify it passes**

Run:

```powershell
mvn "-Dtest=AgentRunDiagnosticMapperTests" test
```

Expected: tests pass.

## Task 3: Controller JSON 暴露阶段字段

- [ ] **Step 1: Update WebMvc test first**

修改 `AgentRunTraceControllerTests.findsRunByRunKey`：

- 断言 `$.steps[0].stepPhase == "POLICY"`。
- 断言 `$.phases[0].phase == "POLICY"`。
- 断言 `$.phases[0].startStepIndex == 1`。
- 断言 `$.phases[0].status == "SKIPPED"`。

- [ ] **Step 2: Run test to verify it fails**

Run:

```powershell
mvn "-Dtest=AgentRunTraceControllerTests" test
```

Expected: failure until mapper and records are updated.

- [ ] **Step 3: Ensure production JSON includes fields**

如果 Task 2 已完成，Spring record serialization 会自动暴露新增 accessor，无需 Controller 额外代码。

- [ ] **Step 4: Run test to verify it passes**

Run:

```powershell
mvn "-Dtest=AgentRunTraceControllerTests" test
```

Expected: tests pass.

## Task 4: 回归验证与提交

- [ ] **Step 1: Run focused regression suite**

Run:

```powershell
mvn "-Dtest=AgentRunPhaseClassifierTests,AgentRunDiagnosticMapperTests,AgentRunTraceControllerTests,AgentTraceAccessAuditControllerTests,AgentTraceDiagnosticAccessServiceTests,ApplicationContextTests" test
```

Expected: selected tests pass.

- [ ] **Step 2: Run diff check**

Run:

```powershell
git diff --check
```

Expected: no whitespace errors. CRLF warnings on Windows are acceptable when exit code is 0.

- [ ] **Step 3: Commit**

Run:

```powershell
git status --short
git add docs/superpowers/specs/2026-08-03-enterprise-trace-phase-view-design.md docs/superpowers/plans/2026-08-03-enterprise-trace-phase-view.md src/main/java/com/example/spring/agent/trace/AgentRunStepPhase.java src/main/java/com/example/spring/agent/trace/AgentRunDiagnosticPhaseView.java src/main/java/com/example/spring/agent/trace/AgentRunPhaseClassifier.java src/test/java/com/example/spring/agent/trace/AgentRunPhaseClassifierTests.java src/main/java/com/example/spring/agent/trace/AgentRunDiagnosticStepView.java src/main/java/com/example/spring/agent/trace/AgentRunDiagnosticTraceView.java src/main/java/com/example/spring/agent/trace/AgentRunDiagnosticMapper.java src/test/java/com/example/spring/agent/trace/AgentRunDiagnosticMapperTests.java src/test/java/com/example/spring/agent/trace/AgentRunTraceControllerTests.java
git commit -m "feat(orchestration): expose trace phase view"
```

Expected: one focused commit on `tang` branch.
