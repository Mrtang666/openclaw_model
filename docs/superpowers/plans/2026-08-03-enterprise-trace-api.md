# Trace Query API Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Expose the Agent Run Trace query service through small read-only diagnostic REST endpoints.

**Architecture:** Add a thin Spring MVC controller under `/api/agent-runs`. Keep query rules in `AgentRunTraceQueryService`; the controller only maps HTTP paths, status codes, and no-store cache headers.

**Tech Stack:** Java 17, Spring Boot MVC, JUnit 5, MockMvc, Mockito, AssertJ.

---

## File Structure

- Create `src/main/java/com/example/spring/agent/trace/AgentRunTraceController.java`: REST API for trace diagnostics.
- Create `src/test/java/com/example/spring/agent/trace/AgentRunTraceControllerTests.java`: Web MVC tests for endpoint contract.
- Create `docs/superpowers/specs/2026-08-03-enterprise-trace-api-design.md`: Chinese design document.
- Create `docs/superpowers/plans/2026-08-03-enterprise-trace-api.md`: implementation plan.

## Task 1: Controller Contract

**Files:**
- Create: `src/test/java/com/example/spring/agent/trace/AgentRunTraceControllerTests.java`
- Create: `src/main/java/com/example/spring/agent/trace/AgentRunTraceController.java`

- [ ] **Step 1: Write failing controller tests**

Create tests:

```java
@WebMvcTest(AgentRunTraceController.class)
class AgentRunTraceControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AgentRunTraceQueryService queryService;

    @Test
    void findsRunByRunKey() throws Exception {
        when(queryService.findRun("agent-run-1")).thenReturn(Optional.of(traceView()));

        mockMvc.perform(get("/api/agent-runs/agent-run-1"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("no-store")))
                .andExpect(jsonPath("$.runKey").value("agent-run-1"));

        verify(queryService).findRun("agent-run-1");
    }
}
```

- [ ] **Step 2: Run test to verify RED**

Run: `mvn "-Dtest=AgentRunTraceControllerTests" test`

Expected: compilation failure because `AgentRunTraceController` does not exist.

- [ ] **Step 3: Implement minimal controller**

Implement:

```java
@RestController
@RequestMapping("/api/agent-runs")
public class AgentRunTraceController {
    @GetMapping("/{runKey}")
    public ResponseEntity<AgentRunTraceView> run(@PathVariable String runKey) { ... }

    @GetMapping
    public ResponseEntity<List<AgentRunSummaryView>> recentRuns(
            @RequestParam String sessionKey,
            @RequestParam(defaultValue = "20") int limit) { ... }
}
```

Use `CacheControl.noStore()`.

- [ ] **Step 4: Run controller tests to verify GREEN**

Run: `mvn "-Dtest=AgentRunTraceControllerTests" test`

Expected: controller tests pass.

## Task 2: Focused Verification and Commit

**Files:**
- Verify all files from Task 1.

- [ ] **Step 1: Run focused verification**

Run: `mvn "-Dtest=AgentRunTraceControllerTests,AgentRunTraceQueryServiceTests,JdbcAgentRunQueryRepositoryTests,ApplicationContextTests" test`

Expected: all selected tests pass.

- [ ] **Step 2: Run whitespace verification**

Run: `git diff --check`

Expected: no whitespace errors. CRLF warnings are acceptable in this repository.

- [ ] **Step 3: Review diff scope**

Run: `git diff --stat` and `git diff --name-only`

Expected: only trace API and documentation files changed.

- [ ] **Step 4: Commit**

```bash
git add docs/superpowers/specs/2026-08-03-enterprise-trace-api-design.md docs/superpowers/plans/2026-08-03-enterprise-trace-api.md src/main/java/com/example/spring/agent/trace/AgentRunTraceController.java src/test/java/com/example/spring/agent/trace/AgentRunTraceControllerTests.java
git commit -m "feat(orchestration): expose trace query api"
```
