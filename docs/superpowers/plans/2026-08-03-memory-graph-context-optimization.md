# Memory Graph Context Optimization Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Close the maintenance and compression loops for the Memory Graph context system.

**Architecture:** Extend the existing context package instead of creating a second context pipeline. Maintenance writes summary/topic/active-extract nodes through `MemoryGraphMaintenanceService`; compression delegates semantic shrinking to a small service and keeps truncation as the final fallback.

**Tech Stack:** Java 21, Spring Boot, JUnit 5, Mockito, Maven, Flyway/MySQL schema already present.

---

### Task 1: Maintain summaries, topics, and active extracts

**Files:**
- Modify: `src/main/java/com/example/spring/wechat/context/MemoryGraphMaintenanceService.java`
- Test: `src/test/java/com/example/spring/wechat/context/MemoryGraphMaintenanceServiceTests.java`

- [ ] Add a failing test that calls `maintainConversationWindow` with memory older than the strong recent window and verifies `CONVERSATION_SUMMARY`, `CONVERSATION_TOPIC`, and `ACTIVE_EXTRACT` nodes are created.
- [ ] Run `mvn "-Dtest=MemoryGraphMaintenanceServiceTests" test` and confirm the new method is missing.
- [ ] Implement `maintainConversationWindow` using `SlidingWindowSummaryService`, `ActiveExtractService`, and `MemoryGraphRepository`.
- [ ] Re-run `mvn "-Dtest=MemoryGraphMaintenanceServiceTests" test` and confirm it passes.

### Task 2: Add semantic section compression

**Files:**
- Create: `src/main/java/com/example/spring/wechat/context/SectionCompressionService.java`
- Modify: `src/main/java/com/example/spring/wechat/context/ContextCompressor.java`
- Test: `src/test/java/com/example/spring/wechat/context/ContextCompressorTests.java`

- [ ] Add a failing test proving the compressor uses model-generated shorter text before truncating.
- [ ] Add a failing test proving model failures fall back to truncation.
- [ ] Run `mvn "-Dtest=ContextCompressorTests" test` and confirm failures.
- [ ] Implement `SectionCompressionService` and inject it optionally into `ContextCompressor`.
- [ ] Re-run `mvn "-Dtest=ContextCompressorTests" test` and confirm it passes.

### Task 3: Regression verification

**Files:**
- Verify existing context and conversation tests.

- [ ] Run the context test suite.
- [ ] Run Function Calling and WeChat conversation regression tests.
- [ ] Run `git diff --check`.
- [ ] Commit the optimization changes.
