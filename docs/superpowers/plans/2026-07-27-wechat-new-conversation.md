# WeChat `#new` New Conversation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement a strict `#new` WeChat command that closes the current active conversation and makes the next normal message start a fresh conversation.

**Architecture:** Add a `startNewConversation` operation to the memory service boundary, implement it for MySQL and the in-memory fallback, then detect `#new` in `WechatConversationService` before normal message persistence. The command clears only short-term conversation state and process caches; old messages, old conversations, durable resources, and user preferences remain.

**Tech Stack:** Java 17, Spring Boot 3, Spring JDBC, MySQL, Flyway-managed schema, JUnit 5.

---

## File Structure

- Modify `src/main/java/com/example/spring/wechat/memory/service/WechatMemoryService.java`: add the new service method.
- Modify `src/main/java/com/example/spring/wechat/memory/service/MySqlWechatMemoryService.java`: close active MySQL conversations and fall back when MySQL is unavailable.
- Modify `src/main/java/com/example/spring/wechat/memory/fallback/InMemoryWechatMemoryFallback.java`: drop the current fallback session while preserving preferences and duplicate-message ids.
- Modify `src/main/java/com/example/spring/wechat/conversation/WechatConversationService.java`: detect strict `#new` before `acceptWechatMessage`, clear per-user caches, and return a confirmation.
- Modify `src/test/java/com/example/spring/wechat/memory/MySqlWechatMemoryServiceTests.java`: add persistence-level coverage.
- Modify `src/test/java/com/example/spring/wechat/conversation/WechatConversationServiceTests.java`: add command handling coverage.

### Task 1: Memory Service API and MySQL Behavior

**Files:**
- Modify: `src/main/java/com/example/spring/wechat/memory/service/WechatMemoryService.java`
- Modify: `src/main/java/com/example/spring/wechat/memory/service/MySqlWechatMemoryService.java`
- Test: `src/test/java/com/example/spring/wechat/memory/MySqlWechatMemoryServiceTests.java`

- [ ] **Step 1: Write the failing MySQL service test**

Add a test that:

```java
@Test
void startNewConversationClosesActiveConversationAndNextMessageCreatesAnother() {
    Instant first = Instant.parse("2026-07-27T00:00:00Z");
    assertThat(service.acceptIncoming("wx-new", "msg-1", "hello", "TEXT", first)).isTrue();
    service.recordAssistantMessage("wx-new", "hi", "TEXT", first.plusSeconds(1));
    Long firstConversationId = activeConversationId("wx-new");

    service.startNewConversation("wx-new", first.plusSeconds(2));

    assertThat(activeConversationId("wx-new")).isNull();
    assertThat(conversationStatus(firstConversationId)).isEqualTo("CLOSED");
    assertThat(messageContents()).doesNotContain("#new");

    assertThat(service.acceptIncoming("wx-new", "msg-2", "fresh topic", "TEXT", first.plusSeconds(3))).isTrue();
    Long secondConversationId = activeConversationId("wx-new");

    assertThat(secondConversationId).isNotNull();
    assertThat(secondConversationId).isNotEqualTo(firstConversationId);
}
```

Add private helpers in the test class if they do not already exist:

```java
private Long activeConversationId(String wechatUserId) {
    List<Long> ids = jdbcTemplate.query(
            """
                    SELECT c.id
                    FROM conversations c
                    JOIN users u ON u.id = c.user_id
                    WHERE u.wechat_user_id = ? AND c.status = 'ACTIVE'
                    ORDER BY c.id
                    """,
            (rs, rowNum) -> rs.getLong(1),
            wechatUserId);
    return ids.isEmpty() ? null : ids.get(0);
}

private String conversationStatus(Long conversationId) {
    return jdbcTemplate.queryForObject(
            "SELECT status FROM conversations WHERE id = ?",
            String.class,
            conversationId);
}

private List<String> messageContents() {
    return jdbcTemplate.query(
            "SELECT content FROM conversation_messages ORDER BY id",
            (rs, rowNum) -> rs.getString(1));
}
```

- [ ] **Step 2: Run the failing test**

Run:

```bash
mvn -q "-Dtest=MySqlWechatMemoryServiceTests#startNewConversationClosesActiveConversationAndNextMessageCreatesAnother" test
```

Expected: compilation fails because `startNewConversation` is not defined.

- [ ] **Step 3: Add the memory service method**

Add to `WechatMemoryService`:

```java
void startNewConversation(String wechatUserId, Instant now);
```

Implement in `MySqlWechatMemoryService`:

```java
@Override
@Transactional
public void startNewConversation(String wechatUserId, Instant now) {
    Instant time = safeTime(now);
    String userKey = safeUserId(wechatUserId);
    try {
        Long userId = findUserId(userKey);
        if (userId == null) {
            return;
        }
        List<Long> activeConversationIds = activeConversationIds(userId);
        for (Long conversationId : activeConversationIds) {
            if (conversationId != null) {
                summarizeConversation(conversationId, time);
            }
        }
        closeActiveConversations(userId, time);
    } catch (DataAccessException exception) {
        log.warn("MySQL new conversation switch failed, using in-memory fallback, userId={}, error={}",
                userKey, rootMessage(exception));
        fallback.startNewConversation(userKey, time);
    }
}
```

Add helpers:

```java
private List<Long> activeConversationIds(long userId) {
    return jdbcTemplate.query(
            """
                    SELECT id
                    FROM conversations
                    WHERE user_id = ? AND channel = ? AND status = ?
                    ORDER BY last_active_at, id
                    """,
            (resultSet, rowNumber) -> resultSet.getLong(1),
            userId,
            CHANNEL_WECHAT,
            STATUS_ACTIVE);
}

private void closeActiveConversations(long userId, Instant now) {
    jdbcTemplate.update(
            """
                    UPDATE conversations
                    SET status = ?, closed_at = ?
                    WHERE user_id = ? AND channel = ? AND status = ?
                    """,
            STATUS_CLOSED,
            Timestamp.from(now),
            userId,
            CHANNEL_WECHAT,
            STATUS_ACTIVE);
}
```

- [ ] **Step 4: Run the MySQL service test**

Run:

```bash
mvn -q "-Dtest=MySqlWechatMemoryServiceTests#startNewConversationClosesActiveConversationAndNextMessageCreatesAnother" test
```

Expected: PASS.

### Task 2: In-Memory Fallback Behavior

**Files:**
- Modify: `src/main/java/com/example/spring/wechat/memory/fallback/InMemoryWechatMemoryFallback.java`
- Test: existing unit test if present, otherwise `src/test/java/com/example/spring/wechat/memory/WechatMemoryPropertiesTests.java` is not appropriate; create focused assertions in an existing memory test class.

- [ ] **Step 1: Write the failing fallback test**

Add a test that creates `InMemoryWechatMemoryFallback`, saves a preference, opens a session, calls `startNewConversation`, and asserts the next opened session has a different conversation id while the preference remains.

```java
@Test
void fallbackStartNewConversationDropsSessionButKeepsPreferences() {
    InMemoryWechatMemoryFallback fallback = new InMemoryWechatMemoryFallback(
            new WechatMemoryProperties(60, 30, 6, 20));
    Instant now = Instant.parse("2026-07-27T00:00:00Z");

    fallback.saveExplicitPreference("wx-fallback", "voice", "{\"name\":\"Cherry\"}", "test", now);
    long firstConversationId = fallback.open("wx-fallback", now).conversationId();

    fallback.startNewConversation("wx-fallback", now.plusSeconds(1));

    long secondConversationId = fallback.open("wx-fallback", now.plusSeconds(2)).conversationId();
    assertThat(secondConversationId).isNotEqualTo(firstConversationId);
    assertThat(fallback.explicitPreference("wx-fallback", "voice"))
            .contains("{\"name\":\"Cherry\"}");
}
```

- [ ] **Step 2: Run the failing fallback test**

Run the test class that received the fallback test.

Expected: compilation fails until the fallback implements the new method.

- [ ] **Step 3: Implement fallback method**

Add to `InMemoryWechatMemoryFallback`:

```java
@Override
public void startNewConversation(String wechatUserId, Instant now) {
    sessions.remove(safeKey(wechatUserId));
}
```

- [ ] **Step 4: Run the fallback test**

Expected: PASS.

### Task 3: WeChat `#new` Command Handling

**Files:**
- Modify: `src/main/java/com/example/spring/wechat/conversation/WechatConversationService.java`
- Test: `src/test/java/com/example/spring/wechat/conversation/WechatConversationServiceTests.java`

- [ ] **Step 1: Write the failing WeChat service tests**

Add tests for exact command handling:

```java
@Test
void hashNewStartsNewConversationBeforePersistingCommand() {
    Instant now = Instant.now();
    WechatIncomingMessage first = new WechatIncomingMessage("msg-a", "wx-command", null, "hello", List.of(), List.of(), List.of());
    service.handleWechat(first);

    WechatIncomingMessage command = new WechatIncomingMessage("msg-new", "wx-command", null, " #new ", List.of(), List.of(), List.of());
    WechatReply reply = service.handleWechat(command);

    assertThat(reply.text()).isEqualTo("已开启新的对话。");
    assertThat(messageContents()).doesNotContain("#new", " #new ");

    WechatIncomingMessage second = new WechatIncomingMessage("msg-b", "wx-command", null, "fresh", List.of(), List.of(), List.of());
    service.handleWechat(second);

    assertThat(conversationCount("wx-command")).isEqualTo(2);
}

@Test
void hashNewWithExtraTextIsNormalInput() {
    WechatIncomingMessage message = new WechatIncomingMessage("msg-c", "wx-normal", null, "#new foo", List.of(), List.of(), List.of());

    service.handleWechat(message);

    assertThat(messageContents()).contains("#new foo");
}
```

Use the constructor shape and test fixtures already present in `WechatConversationServiceTests`; adapt helper wiring to the existing test style rather than introducing a new framework.

- [ ] **Step 2: Run the failing WeChat service tests**

Run:

```bash
mvn -q "-Dtest=WechatConversationServiceTests#hashNewStartsNewConversationBeforePersistingCommand,WechatConversationServiceTests#hashNewWithExtraTextIsNormalInput" test
```

Expected: FAIL because `#new` is not special-cased.

- [ ] **Step 3: Implement command handling**

Add constants:

```java
private static final String NEW_CONVERSATION_COMMAND = "#new";
private static final String NEW_CONVERSATION_CONFIRMATION = "已开启新的对话。";
```

In `handleWechat(...)`, after `sessionKey` is computed and before `acceptWechatMessage(...)`, add:

```java
if (isNewConversationCommand(message)) {
    startNewConversation(sessionKey);
    return WechatReply.text(NEW_CONVERSATION_CONFIRMATION);
}
```

Add helpers:

```java
private boolean isNewConversationCommand(WechatIncomingMessage message) {
    return message != null
            && message.text() != null
            && NEW_CONVERSATION_COMMAND.equals(message.text().strip());
}

private void startNewConversation(String sessionKey) {
    if (DEFAULT_SESSION_KEY.equals(sessionKey)) {
        memories.remove(sessionKey);
    } else {
        wechatMemoryService.startNewConversation(sessionKey, java.time.Instant.now());
        memories.remove(sessionKey);
    }
    pendingVideos.remove(sessionKey);
    lastVideos.remove(sessionKey);
}
```

- [ ] **Step 4: Run the WeChat service tests**

Run the focused tests again.

Expected: PASS.

### Task 4: Verification and Commit

**Files:**
- All files changed above.

- [ ] **Step 1: Run focused memory and conversation tests**

Run:

```bash
mvn -q "-Dtest=MySqlWechatMemoryServiceTests,WechatConversationServiceTests" test
```

Expected: PASS.

- [ ] **Step 2: Run application context test**

Run:

```bash
mvn -q "-Dtest=ApplicationContextTests" test
```

Expected: PASS.

- [ ] **Step 3: Inspect git diff**

Run:

```bash
git diff --check
git diff --stat
```

Expected: no whitespace errors; changed files match this plan.

- [ ] **Step 4: Commit implementation**

Run:

```bash
git add src/main/java/com/example/spring/wechat/memory/service/WechatMemoryService.java \
        src/main/java/com/example/spring/wechat/memory/service/MySqlWechatMemoryService.java \
        src/main/java/com/example/spring/wechat/memory/fallback/InMemoryWechatMemoryFallback.java \
        src/main/java/com/example/spring/wechat/conversation/WechatConversationService.java \
        src/test/java/com/example/spring/wechat/memory/MySqlWechatMemoryServiceTests.java \
        src/test/java/com/example/spring/wechat/conversation/WechatConversationServiceTests.java \
        docs/superpowers/plans/2026-07-27-wechat-new-conversation.md
git commit -m "Add WeChat new conversation command"
```

Expected: one implementation commit.
