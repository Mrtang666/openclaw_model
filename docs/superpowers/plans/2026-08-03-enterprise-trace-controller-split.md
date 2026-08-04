# Enterprise Trace Controller Split Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将 Trace 诊断查询 Controller 和 Trace 访问审计查询 Controller 拆分，并抽出统一的授权审计门面服务。

**Architecture:** 保持 `/api/agent-runs/**` 外部协议不变。新增 `AgentTraceDiagnosticAccessService` 统一处理 API Key 授权和审计写入；`AgentRunTraceController` 只处理 Run Trace 查询；`AgentTraceAccessAuditController` 只处理 access-audit 查询。

**Tech Stack:** Java 17, Spring Boot MVC, JUnit 5, Mockito, MockMvc, Maven。

---

## 文件结构

- Create: `src/main/java/com/example/spring/agent/trace/AgentTraceDiagnosticAccessService.java`
  - Controller 专用访问门面，统一授权与审计写入。
- Create: `src/test/java/com/example/spring/agent/trace/AgentTraceDiagnosticAccessServiceTests.java`
  - 验证访问门面行为。
- Create: `src/main/java/com/example/spring/agent/trace/AgentTraceAccessAuditController.java`
  - 迁移 `GET /api/agent-runs/access-audit`。
- Create: `src/test/java/com/example/spring/agent/trace/AgentTraceAccessAuditControllerTests.java`
  - 覆盖审计查询 Web API。
- Modify: `src/main/java/com/example/spring/agent/trace/AgentRunTraceController.java`
  - 删除 access-audit 方法和内联授权审计逻辑。
- Modify: `src/test/java/com/example/spring/agent/trace/AgentRunTraceControllerTests.java`
  - 保留 Run Trace API 测试，删除 access-audit 测试。

## Task 1: 新增访问门面服务

- [ ] **Step 1: Write the failing test**

在 `src/test/java/com/example/spring/agent/trace/AgentTraceDiagnosticAccessServiceTests.java` 写测试：

```java
package com.example.spring.agent.trace;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentTraceDiagnosticAccessServiceTests {

    private final AgentTraceAccessPolicy accessPolicy = mock(AgentTraceAccessPolicy.class);
    private final AgentTraceAccessAuditService auditService = mock(AgentTraceAccessAuditService.class);
    private final AgentTraceDiagnosticAccessService service =
            new AgentTraceDiagnosticAccessService(accessPolicy, auditService);

    @Test
    void authorizesWithTrimmedApiKeyAndAuditsAccess() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRemoteAddr()).thenReturn("10.0.0.7");
        when(accessPolicy.authorize("secret"))
                .thenReturn(new AgentTraceAccessDecision(true, "API_KEY_MATCHED"));

        AgentTraceAccessDecision decision = service.authorizeAndAudit(
                " ops ",
                " secret ",
                "FIND_RUN",
                "RUN",
                "agent-run-1",
                request,
                "JUnit");

        assertThat(decision.allowed()).isTrue();
        verify(accessPolicy).authorize("secret");
        verify(auditService).record(argThat(event ->
                "ops".equals(event.actor())
                        && "FIND_RUN".equals(event.action())
                        && "RUN".equals(event.targetType())
                        && "agent-run-1".equals(event.targetKey())
                        && event.allowed()
                        && "API_KEY_MATCHED".equals(event.reason())
                        && "10.0.0.7".equals(event.remoteAddress())
                        && "JUnit".equals(event.userAgent())));
    }

    @Test
    void usesAnonymousActorWhenActorIsBlank() {
        when(accessPolicy.authorize(""))
                .thenReturn(new AgentTraceAccessDecision(false, "API_KEY_MISSING"));

        service.authorizeAndAudit(
                " ",
                null,
                "FIND_RECENT_RUNS",
                "SESSION",
                "session-a",
                null,
                null);

        verify(auditService).record(argThat(event ->
                "anonymous".equals(event.actor())
                        && !event.allowed()
                        && "API_KEY_MISSING".equals(event.reason())
                        && "".equals(event.remoteAddress())));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```powershell
mvn "-Dtest=AgentTraceDiagnosticAccessServiceTests" test
```

Expected: compile failure because `AgentTraceDiagnosticAccessService` does not exist.

- [ ] **Step 3: Write minimal implementation**

创建 `src/main/java/com/example/spring/agent/trace/AgentTraceDiagnosticAccessService.java`：

```java
package com.example.spring.agent.trace;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;

@Service
public class AgentTraceDiagnosticAccessService {

    private final AgentTraceAccessPolicy accessPolicy;
    private final AgentTraceAccessAuditService auditService;

    public AgentTraceDiagnosticAccessService(
            AgentTraceAccessPolicy accessPolicy,
            AgentTraceAccessAuditService auditService) {
        this.accessPolicy = accessPolicy;
        this.auditService = auditService;
    }

    public AgentTraceAccessDecision authorizeAndAudit(
            String actor,
            String apiKey,
            String action,
            String targetType,
            String targetKey,
            HttpServletRequest request,
            String userAgent) {
        AgentTraceAccessDecision decision = accessPolicy.authorize(clean(apiKey));
        auditService.record(new AgentTraceAccessAuditEvent(
                actorName(actor),
                action,
                targetType,
                targetKey,
                decision.allowed(),
                decision.reason(),
                request == null ? "" : request.getRemoteAddr(),
                userAgent));
        return decision;
    }

    private String actorName(String actor) {
        String cleanActor = clean(actor);
        return cleanActor.isBlank() ? "anonymous" : cleanActor;
    }

    private String clean(String value) {
        return value == null ? "" : value.strip();
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run:

```powershell
mvn "-Dtest=AgentTraceDiagnosticAccessServiceTests" test
```

Expected: tests pass.

## Task 2: 拆出 AgentTraceAccessAuditController

- [ ] **Step 1: Write the failing WebMvc test**

从 `AgentRunTraceControllerTests` 迁移 access-audit 相关测试到新文件 `src/test/java/com/example/spring/agent/trace/AgentTraceAccessAuditControllerTests.java`，`@WebMvcTest(AgentTraceAccessAuditController.class)`，Mock `AgentTraceDiagnosticAccessService` 和 `AgentTraceAccessAuditQueryService`。

- [ ] **Step 2: Run test to verify it fails**

Run:

```powershell
mvn "-Dtest=AgentTraceAccessAuditControllerTests" test
```

Expected: compile failure because `AgentTraceAccessAuditController` does not exist.

- [ ] **Step 3: Write minimal implementation**

创建 `AgentTraceAccessAuditController`，使用 `@RequestMapping("/api/agent-runs/access-audit")`，把目标解析逻辑从原 Controller 移入该类。

- [ ] **Step 4: Run test to verify it passes**

Run:

```powershell
mvn "-Dtest=AgentTraceAccessAuditControllerTests" test
```

Expected: tests pass.

## Task 3: 收窄 AgentRunTraceController

- [ ] **Step 1: Modify tests first**

调整 `AgentRunTraceControllerTests`：

- 删除 `AgentTraceAccessPolicy`、`AgentTraceAccessAuditService`、`AgentTraceAccessAuditQueryService` mock。
- 新增 `AgentTraceDiagnosticAccessService` mock。
- 删除 access-audit 相关测试。
- 在 Run Trace / Recent Runs 测试中 verify `authorizeAndAudit(...)` 被调用。

- [ ] **Step 2: Run test to verify it fails**

Run:

```powershell
mvn "-Dtest=AgentRunTraceControllerTests" test
```

Expected: failure because production Controller 仍依赖旧的授权审计结构。

- [ ] **Step 3: Update production controller**

修改 `AgentRunTraceController`：

- 删除 `accessAudit(...)` 方法。
- 删除 `AgentTraceAccessPolicy`、`AgentTraceAccessAuditService`、`AgentTraceAccessAuditQueryService` 字段。
- 注入 `AgentTraceDiagnosticAccessService`。
- `run(...)` 和 `recentRuns(...)` 使用 `authorizeAndAudit(...)`。
- 删除私有 `recordAudit(...)`、`auditQueryTarget(...)` 和内部 record。

- [ ] **Step 4: Run test to verify it passes**

Run:

```powershell
mvn "-Dtest=AgentRunTraceControllerTests" test
```

Expected: tests pass.

## Task 4: 集成验证与提交

- [ ] **Step 1: Run focused regression suite**

Run:

```powershell
mvn "-Dtest=AgentTraceDiagnosticAccessServiceTests,AgentRunTraceControllerTests,AgentTraceAccessAuditControllerTests,AgentTraceAccessAuditQueryServiceTests,AgentTraceAccessAuditServiceTests,ApplicationContextTests" test
```

Expected: all selected tests pass.

- [ ] **Step 2: Run diff check**

Run:

```powershell
git diff --check
```

Expected: no whitespace errors. CRLF warnings are acceptable if reported by Git on Windows.

- [ ] **Step 3: Commit**

Run:

```powershell
git status --short
git add docs/superpowers/specs/2026-08-03-enterprise-trace-controller-split-design.md docs/superpowers/plans/2026-08-03-enterprise-trace-controller-split.md src/main/java/com/example/spring/agent/trace/AgentTraceDiagnosticAccessService.java src/test/java/com/example/spring/agent/trace/AgentTraceDiagnosticAccessServiceTests.java src/main/java/com/example/spring/agent/trace/AgentTraceAccessAuditController.java src/test/java/com/example/spring/agent/trace/AgentTraceAccessAuditControllerTests.java src/main/java/com/example/spring/agent/trace/AgentRunTraceController.java src/test/java/com/example/spring/agent/trace/AgentRunTraceControllerTests.java
git commit -m "feat(orchestration): split trace diagnostic controllers"
```

Expected: one focused commit on `tang`.
