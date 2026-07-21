# 微信端 MySQL 上下文记忆实施计划

> **供执行型智能体使用：** 必须使用 `subagent-driven-development`（推荐）或 `executing-plans` 技能逐项实施。每一步均以复选框标记，方便追踪。

**目标：** 让微信用户的对话历史、会话临时状态和明确偏好在应用重启后仍可恢复，并在 MySQL 临时不可用时自动退回当前进程内存，且不改变 CLI 的上下文逻辑。

**架构：** `WechatConversationService` 不再把 `ConcurrentHashMap` 作为正式记忆来源，而改为依赖 `WechatMemoryService`。该服务通过 MyBatis-Plus 读写 MySQL，并把最近对话、会话摘要、状态 JSON 和明确偏好拼装成现有工具规划器与大模型所需的上下文；数据库访问失败时由内存实现承担本次进程的临时记忆。

**技术栈：** Java 17、Spring Boot 3.4.7、MySQL 8、MyBatis-Plus、Flyway、Jackson、JUnit 5、Mockito、Testcontainers MySQL。

---

## 文件结构与职责

```text
src/main/resources/
  application.properties                              # 数据库、Flyway、记忆策略配置
  db/migration/V1__create_wechat_memory_tables.sql    # 首次建表及索引

src/main/java/com/example/spring/wechat/memory/
  config/WechatMemoryProperties.java                  # 60 分钟、30 天、轮数等配置
  model/ConversationTurn.java                         # 一轮用户/助手对话
  model/WechatConversationMemory.java                 # 当前服务使用的记忆快照
  model/WechatMemorySession.java                      # 用户、会话与记忆快照的访问对象
  entity/*.java                                       # MyBatis-Plus 实体
  mapper/*.java                                       # 表 Mapper
  service/WechatMemoryService.java                    # 微信上下文的统一接口
  service/MySqlWechatMemoryService.java               # 正式 MySQL 实现
  fallback/InMemoryWechatMemoryFallback.java          # 数据库故障时的进程内兜底
  scheduler/WechatMemoryMaintenanceScheduler.java     # 摘要、过期数据清理

src/main/java/com/example/spring/wechat/conversation/
  WechatConversationService.java                      # 改为调用 WechatMemoryService

src/main/java/com/example/spring/wechat/voice/style/service/
  VoicePreferenceService.java                         # 将选定音色持久化为明确用户偏好

src/test/java/com/example/spring/wechat/memory/
  MySqlWechatMemoryServiceTests.java                  # MySQL 会话、历史、状态、偏好、幂等测试
  WechatMemoryMaintenanceSchedulerTests.java          # 30 天清理和摘要触发测试
  InMemoryWechatMemoryFallbackTests.java              # 数据库异常时的内存兜底测试
```

### Task 1：加入数据库依赖与配置项

**文件：**

- 修改：`pom.xml`
- 修改：`src/main/resources/application.properties`

- [ ] **步骤 1：先写出会失败的 Spring 上下文测试**

在 `src/test/java/com/example/spring/ApplicationContextTests.java` 增加数据库配置存在时可启动的断言。测试在未添加依赖或未声明配置类时应失败：

```java
@Test
void loadsWechatMemoryProperties() {
    assertThat(context.getBean(WechatMemoryProperties.class).sessionIdleMinutes())
            .isEqualTo(60);
}
```

- [ ] **步骤 2：运行测试确认失败**

运行：

```powershell
mvn -q -Dtest=ApplicationContextTests test
```

预期：失败，提示找不到 `WechatMemoryProperties`。

- [ ] **步骤 3：补充 Maven 依赖**

在 `pom.xml` 的 `<dependencies>` 中加入：

```xml
<dependency>
    <groupId>com.baomidou</groupId>
    <artifactId>mybatis-plus-spring-boot3-starter</artifactId>
    <version>3.5.12</version>
</dependency>
<dependency>
    <groupId>com.mysql</groupId>
    <artifactId>mysql-connector-j</artifactId>
    <scope>runtime</scope>
</dependency>
<dependency>
    <groupId>org.flywaydb</groupId>
    <artifactId>flyway-mysql</artifactId>
</dependency>
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>mysql</artifactId>
    <scope>test</scope>
</dependency>
```

新增 `WechatMemoryProperties`：

```java
@ConfigurationProperties(prefix = "wechat.memory")
public record WechatMemoryProperties(
        int sessionIdleMinutes,
        int rawRetentionDays,
        int recentTurnLimit,
        int rollingSummaryTurnThreshold) {

    public WechatMemoryProperties {
        sessionIdleMinutes = sessionIdleMinutes <= 0 ? 60 : sessionIdleMinutes;
        rawRetentionDays = rawRetentionDays <= 0 ? 30 : rawRetentionDays;
        recentTurnLimit = recentTurnLimit <= 0 ? 10 : recentTurnLimit;
        rollingSummaryTurnThreshold =
                rollingSummaryTurnThreshold <= 0 ? 20 : rollingSummaryTurnThreshold;
    }
}
```

在 `AgentClawApplication` 上启用：

```java
@EnableConfigurationProperties(WechatMemoryProperties.class)
@SpringBootApplication
public class AgentClawApplication {
    // 保持现有 main 方法不变。
}
```

在 `application.properties` 追加：

```properties
spring.datasource.url=${MYSQL_URL:jdbc:mysql://127.0.0.1:3306/openclaw?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai}
spring.datasource.username=${MYSQL_USERNAME:root}
spring.datasource.password=${MYSQL_PASSWORD:}
spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver
spring.flyway.enabled=${FLYWAY_ENABLED:true}
spring.flyway.locations=classpath:db/migration
mybatis-plus.configuration.map-underscore-to-camel-case=true
wechat.memory.session-idle-minutes=60
wechat.memory.raw-retention-days=30
wechat.memory.recent-turn-limit=10
wechat.memory.rolling-summary-turn-threshold=20
```

- [ ] **步骤 4：运行测试确认通过**

运行：

```powershell
mvn -q -Dtest=ApplicationContextTests test
```

预期：通过；本步骤不需要真实连接 MySQL。

- [ ] **步骤 5：提交本任务**

```powershell
git add pom.xml src/main/java/com/example/spring/AgentClawApplication.java src/main/resources/application.properties src/test/java/com/example/spring/ApplicationContextTests.java src/main/java/com/example/spring/wechat/memory/config/WechatMemoryProperties.java
git commit -m "feat: add MySQL context memory dependencies"
```

### Task 2：创建 Flyway 数据表与索引

**文件：**

- 新建：`src/main/resources/db/migration/V1__create_wechat_memory_tables.sql`
- 新建：`src/test/java/com/example/spring/wechat/memory/MySqlWechatMemoryServiceTests.java`

- [ ] **步骤 1：写出验证表结构的失败测试**

使用 `@Testcontainers` 启动 MySQL，并检查迁移完成后包含 `users`、`conversations`、`conversation_messages`、`conversation_states`、`user_preferences`、`conversation_summaries`、`tool_execution_logs`：

```java
@Test
void flywayCreatesWechatMemoryTables() {
    List<String> tables = jdbcTemplate.queryForList(
            "SHOW TABLES", String.class);

    assertThat(tables).contains(
            "users",
            "conversations",
            "conversation_messages",
            "conversation_states",
            "user_preferences",
            "conversation_summaries",
            "tool_execution_logs");
}
```

- [ ] **步骤 2：运行测试确认失败**

运行：

```powershell
mvn -q -Dtest=MySqlWechatMemoryServiceTests#flywayCreatesWechatMemoryTables test
```

预期：失败，原因是迁移脚本尚不存在。

- [ ] **步骤 3：创建初始迁移脚本**

在迁移脚本中使用以下 SQL；所有表使用 `utf8mb4`、`InnoDB`，时间使用 `DATETIME(3)`：

```sql
CREATE TABLE users (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    wechat_user_id VARCHAR(191) NOT NULL,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    UNIQUE KEY uk_users_wechat_user_id (wechat_user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE conversations (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    channel VARCHAR(32) NOT NULL,
    status VARCHAR(16) NOT NULL,
    started_at DATETIME(3) NOT NULL,
    last_active_at DATETIME(3) NOT NULL,
    closed_at DATETIME(3) NULL,
    KEY idx_conversations_user_active (user_id, channel, status, last_active_at),
    CONSTRAINT fk_conversations_user FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE conversation_messages (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    conversation_id BIGINT NOT NULL,
    source_message_id VARCHAR(191) NULL,
    role VARCHAR(16) NOT NULL,
    content MEDIUMTEXT NOT NULL,
    content_type VARCHAR(16) NOT NULL,
    created_at DATETIME(3) NOT NULL,
    expires_at DATETIME(3) NOT NULL,
    UNIQUE KEY uk_messages_source_message_id (source_message_id),
    KEY idx_messages_conversation_created (conversation_id, created_at, id),
    KEY idx_messages_expires_at (expires_at),
    CONSTRAINT fk_messages_conversation FOREIGN KEY (conversation_id) REFERENCES conversations(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE conversation_states (
    conversation_id BIGINT PRIMARY KEY,
    state_json JSON NOT NULL,
    version BIGINT NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    CONSTRAINT fk_states_conversation FOREIGN KEY (conversation_id) REFERENCES conversations(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE user_preferences (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    preference_key VARCHAR(64) NOT NULL,
    preference_value_json JSON NOT NULL,
    source VARCHAR(32) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    UNIQUE KEY uk_preferences_user_key (user_id, preference_key),
    CONSTRAINT fk_preferences_user FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE conversation_summaries (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    conversation_id BIGINT NOT NULL,
    summary_text MEDIUMTEXT NOT NULL,
    covered_message_id BIGINT NULL,
    generated_at DATETIME(3) NOT NULL,
    KEY idx_summaries_conversation_generated (conversation_id, generated_at, id),
    CONSTRAINT fk_summaries_conversation FOREIGN KEY (conversation_id) REFERENCES conversations(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE tool_execution_logs (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    conversation_id BIGINT NOT NULL,
    tool_name VARCHAR(64) NOT NULL,
    arguments_json JSON NOT NULL,
    result_summary TEXT NOT NULL,
    status VARCHAR(16) NOT NULL,
    created_at DATETIME(3) NOT NULL,
    expires_at DATETIME(3) NOT NULL,
    KEY idx_tool_logs_expires_at (expires_at),
    KEY idx_tool_logs_conversation_created (conversation_id, created_at, id),
    CONSTRAINT fk_tool_logs_conversation FOREIGN KEY (conversation_id) REFERENCES conversations(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

- [ ] **步骤 4：运行迁移测试确认通过**

运行：

```powershell
mvn -q -Dtest=MySqlWechatMemoryServiceTests#flywayCreatesWechatMemoryTables test
```

预期：通过。

- [ ] **步骤 5：提交本任务**

```powershell
git add src/main/resources/db/migration/V1__create_wechat_memory_tables.sql src/test/java/com/example/spring/wechat/memory/MySqlWechatMemoryServiceTests.java
git commit -m "feat: add WeChat memory schema migration"
```

### Task 3：定义记忆模型、Mapper 与统一服务接口

**文件：**

- 新建：`src/main/java/com/example/spring/wechat/memory/model/ConversationTurn.java`
- 新建：`src/main/java/com/example/spring/wechat/memory/model/WechatConversationMemory.java`
- 新建：`src/main/java/com/example/spring/wechat/memory/model/WechatMemorySession.java`
- 新建：`src/main/java/com/example/spring/wechat/memory/entity/*.java`
- 新建：`src/main/java/com/example/spring/wechat/memory/mapper/*.java`
- 新建：`src/main/java/com/example/spring/wechat/memory/service/WechatMemoryService.java`

- [ ] **步骤 1：写出内存语义的失败测试**

新增测试，约束记忆对象保持现有行为：

```java
@Test
void memoryKeepsRecentTurnsAndStateFields() {
    WechatConversationMemory memory = WechatConversationMemory.empty(6);
    memory.record("用户问题", "助手回复");
    memory.recordWeatherCity("杭州");
    memory.recordPendingImagePrompt("画一只猫", "橘猫，赛博朋克风格");

    assertThat(memory.snapshot()).containsExactly(
            new ConversationTurn("用户问题", "助手回复"));
    assertThat(memory.lastWeatherCity()).contains("杭州");
    assertThat(memory.lastPendingImagePrompt()).contains("橘猫，赛博朋克风格");
}
```

- [ ] **步骤 2：运行测试确认失败**

运行：

```powershell
mvn -q -Dtest=MySqlWechatMemoryServiceTests#memoryKeepsRecentTurnsAndStateFields test
```

预期：失败，提示不存在 `WechatConversationMemory`。

- [ ] **步骤 3：实现模型及接口**

将当前 `WechatConversationService` 内部的 `ConversationMemory` 和 `ConversationTurn` 移入 `memory/model`，保持以下公共方法，以便后续平滑替换：

```java
public interface WechatMemoryService {
    WechatMemorySession open(String wechatUserId, Instant now);
    boolean acceptIncoming(String wechatUserId, String sourceMessageId, String content, String contentType, Instant now);
    WechatConversationMemory memoryFor(String wechatUserId);
    void saveMemory(String wechatUserId, WechatConversationMemory memory, Instant now);
    void recordAssistantMessage(String wechatUserId, String content, String contentType, Instant now);
    void recordToolExecution(String wechatUserId, String toolName, Map<String, String> arguments,
                             String resultSummary, String status, Instant now);
    Optional<String> explicitPreference(String wechatUserId, String preferenceKey);
    void saveExplicitPreference(String wechatUserId, String preferenceKey, String valueJson,
                                String source, Instant now);
}
```

`WechatConversationMemory` 的状态 JSON 字段固定如下，避免工具状态散落在多个表中：

```java
private String lastImagePrompt;
private String pendingImagePrompt;
private String lastWeatherCity;
private String pendingClarificationUserText;
private String pendingClarificationQuestion;
private int lastImagePromptTurnCount;
private List<VoiceProfile> lastDisplayedVoiceCandidates;
private String lastVoiceQuery;
private int nextVoiceCandidatePage;
private VoiceProfile recentVoicePreview;
private Instant recentVoicePreviewAt;
```

所有 MyBatis-Plus 实体使用 `@TableName`、`@TableId(type = IdType.AUTO)`；实体的时间字段使用 `LocalDateTime`，`JSON` 字段先按 `String` 保存并由 Jackson 序列化。

- [ ] **步骤 4：运行模型测试确认通过**

运行：

```powershell
mvn -q -Dtest=MySqlWechatMemoryServiceTests#memoryKeepsRecentTurnsAndStateFields test
```

预期：通过。

- [ ] **步骤 5：提交本任务**

```powershell
git add src/main/java/com/example/spring/wechat/memory src/test/java/com/example/spring/wechat/memory/MySqlWechatMemoryServiceTests.java
git commit -m "feat: define WeChat memory domain model"
```

### Task 4：实现 MySQL 读写、60 分钟会话和消息幂等

**文件：**

- 新建：`src/main/java/com/example/spring/wechat/memory/service/MySqlWechatMemoryService.java`
- 修改：`src/main/java/com/example/spring/wechat/memory/mapper/ConversationMapper.java`
- 修改：`src/main/java/com/example/spring/wechat/memory/mapper/ConversationMessageMapper.java`
- 修改：`src/test/java/com/example/spring/wechat/memory/MySqlWechatMemoryServiceTests.java`

- [ ] **步骤 1：写出会失败的会话与幂等测试**

```java
@Test
void opensNewConversationAfterSixtyMinutesOfInactivity() {
    Instant first = Instant.parse("2026-07-20T09:00:00Z");
    long firstConversation = memoryService.open("wx-user", first).conversationId();

    long sameConversation = memoryService.open(
            "wx-user", first.plus(Duration.ofMinutes(59))).conversationId();
    long newConversation = memoryService.open(
            "wx-user", first.plus(Duration.ofMinutes(61))).conversationId();

    assertThat(sameConversation).isEqualTo(firstConversation);
    assertThat(newConversation).isNotEqualTo(firstConversation);
}

@Test
void acceptsOneWechatMessageIdOnlyOnce() {
    Instant now = Instant.parse("2026-07-20T09:00:00Z");

    assertThat(memoryService.acceptIncoming("wx-user", "msg-1", "你好", "TEXT", now)).isTrue();
    assertThat(memoryService.acceptIncoming("wx-user", "msg-1", "你好", "TEXT", now)).isFalse();
}
```

- [ ] **步骤 2：运行测试确认失败**

运行：

```powershell
mvn -q -Dtest=MySqlWechatMemoryServiceTests#opensNewConversationAfterSixtyMinutesOfInactivity,MySqlWechatMemoryServiceTests#acceptsOneWechatMessageIdOnlyOnce test
```

预期：失败，提示服务尚未实现。

- [ ] **步骤 3：实现正式读写流程**

`open` 必须按以下顺序执行：

```java
public WechatMemorySession open(String wechatUserId, Instant now) {
    UserEntity user = findOrCreateUser(wechatUserId, now);
    ConversationEntity conversation = findActiveConversation(user.getId())
            .filter(value -> value.getLastActiveAt().plusMinutes(properties.sessionIdleMinutes())
                    .isAfter(now))
            .orElseGet(() -> closeExpiredAndCreateConversation(user.getId(), now));

    touchConversation(conversation.getId(), now);
    WechatConversationMemory memory = loadMemory(
            conversation.getId(), user.getId(), properties.recentTurnLimit());
    return new WechatMemorySession(user.getId(), conversation.getId(), memory);
}
```

`acceptIncoming` 必须先调用 `open`，再插入 `conversation_messages` 的 `USER/TEXT` 记录；当 `source_message_id` 已存在时捕获唯一索引冲突并返回 `false`，不得重复调用工具或模型。

`loadMemory` 必须合并：

1. 当前会话最近 6 至 10 轮用户/助手文本；
2. 当前会话最近一条摘要；
3. `conversation_states.state_json`；
4. `user_preferences` 的明确偏好。

数据库写操作必须在 `@Transactional` 方法中执行，且只把音色、常用城市等明确表达写入 `user_preferences`；模型猜测的偏好绝不调用 `saveExplicitPreference`。

- [ ] **步骤 4：运行 MySQL 服务测试确认通过**

运行：

```powershell
mvn -q -Dtest=MySqlWechatMemoryServiceTests test
```

预期：通过；包括用户创建、59 分钟复用、61 分钟新会话、消息幂等、状态与偏好恢复。

- [ ] **步骤 5：提交本任务**

```powershell
git add src/main/java/com/example/spring/wechat/memory src/test/java/com/example/spring/wechat/memory/MySqlWechatMemoryServiceTests.java
git commit -m "feat: persist WeChat conversation memory in MySQL"
```

### Task 5：实现数据库不可用时的进程内兜底

**文件：**

- 新建：`src/main/java/com/example/spring/wechat/memory/fallback/InMemoryWechatMemoryFallback.java`
- 修改：`src/main/java/com/example/spring/wechat/memory/service/MySqlWechatMemoryService.java`
- 新建：`src/test/java/com/example/spring/wechat/memory/InMemoryWechatMemoryFallbackTests.java`

- [ ] **步骤 1：写出故障兜底失败测试**

```java
@Test
void usesFallbackMemoryWhenDatabaseOperationFails() {
    WechatMemoryService failingService = serviceWhoseMapperThrows(
            new DataAccessResourceFailureException("mysql unavailable"));

    WechatMemorySession session = failingService.open("wx-user", Instant.now());
    session.memory().record("你好", "你好，我是你的 AI 助手");

    assertThat(failingService.memoryFor("wx-user").snapshot())
            .containsExactly(new ConversationTurn("你好", "你好，我是你的 AI 助手"));
}
```

- [ ] **步骤 2：运行测试确认失败**

运行：

```powershell
mvn -q -Dtest=InMemoryWechatMemoryFallbackTests test
```

预期：失败，提示没有回退实现。

- [ ] **步骤 3：实现只用于故障期的内存回退**

`InMemoryWechatMemoryFallback` 使用 `ConcurrentHashMap<String, FallbackSession>`，仅保存当前进程内数据；每个入口的数据库异常都执行：

```java
catch (DataAccessException exception) {
    log.warn("MySQL 上下文记忆不可用，改用进程内存，userId={}, error={}",
            wechatUserId, rootMessage(exception));
    return fallback.open(wechatUserId, now);
}
```

不要把回退数据在数据库恢复后自动回写，以避免恢复期把重复消息或已过期状态写入正式库；恢复后新消息直接回到正常数据库路径。

- [ ] **步骤 4：运行测试确认通过**

运行：

```powershell
mvn -q -Dtest=InMemoryWechatMemoryFallbackTests test
```

预期：通过；日志包含“改用进程内存”。

- [ ] **步骤 5：提交本任务**

```powershell
git add src/main/java/com/example/spring/wechat/memory/fallback src/main/java/com/example/spring/wechat/memory/service src/test/java/com/example/spring/wechat/memory/InMemoryWechatMemoryFallbackTests.java
git commit -m "feat: add memory fallback for MySQL outages"
```

### Task 6：让微信对话编排器使用持久化记忆

**文件：**

- 修改：`src/main/java/com/example/spring/wechat/conversation/WechatConversationService.java`
- 修改：`src/test/java/com/example/spring/wechat/conversation/WechatConversationServiceTests.java`

- [ ] **步骤 1：写出失败的重启恢复测试**

在测试中使用同一个 `WechatMemoryService` 的新 `WechatConversationService` 实例模拟应用重启：

```java
@Test
void restoresWechatHistoryAfterConversationServiceIsRecreated() {
    memoryService.acceptIncoming("user-1", "msg-1", "我在杭州", "TEXT", Instant.now());
    memoryService.recordAssistantMessage("user-1", "记住了，你在杭州。", "TEXT", Instant.now());

    WechatConversationService recreated = serviceUsing(memoryService, chatService);
    recreated.handleWechat(new WechatIncomingMessage("msg-2", "user-1", null, "那今天呢？", List.of()));

    verify(chatService).streamReply(argThat(prompt ->
            prompt.contains("我在杭州") && prompt.contains("记住了，你在杭州")), any());
}
```

- [ ] **步骤 2：运行测试确认失败**

运行：

```powershell
mvn -q -Dtest=WechatConversationServiceTests#restoresWechatHistoryAfterConversationServiceIsRecreated test
```

预期：失败，因为当前实现只读取 `WechatConversationService.memories`。

- [ ] **步骤 3：替换内存 Map**

构造器注入 `WechatMemoryService` 和 `Clock`：

```java
private final WechatMemoryService wechatMemoryService;
private final Clock clock;
```

删除：

```java
private final Map<String, ConversationMemory> memories = new ConcurrentHashMap<>();
```

将 `memoryFor(sessionKey)` 改为：

```java
private WechatConversationMemory memoryFor(String sessionKey) {
    return wechatMemoryService.memoryFor(sessionKey);
}

private void saveMemory(String sessionKey) {
    wechatMemoryService.saveMemory(sessionKey, memoryFor(sessionKey), Instant.now(clock));
}
```

每次调用 `record`、`recordImage`、`recordPendingImagePrompt`、`recordUserImage`、`recordWeatherCity`、`clearPendingImagePrompt`、`clearPendingClarification` 后立即调用 `saveMemory(sessionKey)`；每次得到可发送的助手文本后调用：

```java
wechatMemoryService.recordAssistantMessage(
        sessionKey, assistantReply, "TEXT", Instant.now(clock));
```

在 `handleWechat(WechatIncomingMessage)` 的最前面增加幂等入口：

```java
if (message.messageId() != null && !message.messageId().isBlank()
        && !wechatMemoryService.acceptIncoming(
                sessionKey,
                message.messageId(),
                message.text() == null ? "" : message.text(),
                message.hasVoices() ? "VOICE" : message.hasImages() ? "IMAGE" : "TEXT",
                Instant.now(clock))) {
    log.info("忽略微信重复消息，userId={}, messageId={}", sessionKey, message.messageId());
    return WechatReply.text("");
}
```

工具计划执行完成后，调用 `recordToolExecution`；`arguments` 保存为 JSON，`resultSummary` 仅保存 `replyMemoryText(parts)` 的前 2,000 个字符，状态为 `SUCCESS` 或 `FAILED`。

- [ ] **步骤 4：运行对话编排与 MySQL 记忆测试确认通过**

运行：

```powershell
mvn -q -Dtest=WechatConversationServiceTests,MySqlWechatMemoryServiceTests test
```

预期：通过；普通文本、天气、图片、语音、追问和多工具任务继续保持原有回复行为。

- [ ] **步骤 5：提交本任务**

```powershell
git add src/main/java/com/example/spring/wechat/conversation/WechatConversationService.java src/test/java/com/example/spring/wechat/conversation/WechatConversationServiceTests.java
git commit -m "feat: connect WeChat conversation flow to persistent memory"
```

### Task 7：持久化音色偏好与临时音色选择状态

**文件：**

- 修改：`src/main/java/com/example/spring/wechat/voice/style/service/VoicePreferenceService.java`
- 修改：`src/test/java/com/example/spring/wechat/voice/style/service/VoicePreferenceServiceTests.java`
- 修改：`src/main/java/com/example/spring/wechat/conversation/tools/VoiceStyleWechatTool.java`

- [ ] **步骤 1：写出失败的音色恢复测试**

```java
@Test
void restoresConfirmedVoiceAfterServiceIsRecreated() {
    VoiceProfile serena = catalog.findByVoice("Serena").orElseThrow();
    firstService.savePreference("user-1", serena);

    VoicePreferenceService recreated = new VoicePreferenceService(
            catalog, memoryService, fixedClock("2026-07-20T09:00:00Z"));

    assertThat(recreated.effectiveVoice("user-1")).isEqualTo("Serena");
}
```

- [ ] **步骤 2：运行测试确认失败**

运行：

```powershell
mvn -q -Dtest=VoicePreferenceServiceTests#restoresConfirmedVoiceAfterServiceIsRecreated test
```

预期：失败，因为当前 `states` 只保存在旧实例中。

- [ ] **步骤 3：实现偏好与临时状态分层**

将选中音色保存为：

```java
wechatMemoryService.saveExplicitPreference(
        sessionKey,
        "voice",
        objectMapper.writeValueAsString(profile),
        "EXPLICIT_ACTION",
        Instant.now(clock));
```

`lastDisplayedCandidates`、`lastQuery`、`nextPage`、`recentPreview`、`recentPreviewAt` 改写到当前会话的 `WechatConversationMemory` 后调用 `saveMemory`。这样：

- “选择第五个”“试听第一个”等短时上下文在同一会话内重启后仍有效；
- “以后都用温柔女声”确认后的音色跨会话有效；
- 用户未确认时不产生长期偏好记录。

- [ ] **步骤 4：运行音色偏好与音色工具测试确认通过**

运行：

```powershell
mvn -q -Dtest=VoicePreferenceServiceTests,VoiceStyleWechatToolTests,VoiceWechatToolTests test
```

预期：通过；默认仍为 `Cherry`，已确认音色重启后可恢复。

- [ ] **步骤 5：提交本任务**

```powershell
git add src/main/java/com/example/spring/wechat/voice/style/service/VoicePreferenceService.java src/main/java/com/example/spring/wechat/conversation/tools/VoiceStyleWechatTool.java src/test/java/com/example/spring/wechat/voice/style/service/VoicePreferenceServiceTests.java
git commit -m "feat: persist confirmed WeChat voice preference"
```

### Task 8：实现摘要生成和 30 天数据清理

**文件：**

- 新建：`src/main/java/com/example/spring/wechat/memory/scheduler/WechatMemoryMaintenanceScheduler.java`
- 修改：`src/main/java/com/example/spring/wechat/memory/service/WechatMemoryService.java`
- 修改：`src/main/java/com/example/spring/wechat/memory/service/MySqlWechatMemoryService.java`
- 新建：`src/test/java/com/example/spring/wechat/memory/WechatMemoryMaintenanceSchedulerTests.java`

- [ ] **步骤 1：写出失败的摘要与清理测试**

```java
@Test
void removesExpiredRawMessagesButKeepsSummaryAndPreference() {
    insertExpiredMessageAndToolLog();
    insertSummaryAndPreference();

    scheduler.cleanExpiredRawData();

    assertThat(count("conversation_messages")).isZero();
    assertThat(count("tool_execution_logs")).isZero();
    assertThat(count("conversation_summaries")).isEqualTo(1);
    assertThat(count("user_preferences")).isEqualTo(1);
}
```

- [ ] **步骤 2：运行测试确认失败**

运行：

```powershell
mvn -q -Dtest=WechatMemoryMaintenanceSchedulerTests test
```

预期：失败，因为计划任务尚未实现。

- [ ] **步骤 3：实现维护任务**

启用调度：

```java
@EnableScheduling
@SpringBootApplication
public class AgentClawApplication {
}
```

调度器包含三个明确方法：

```java
@Scheduled(cron = "0 10 3 * * *")
public void summarizeInactiveConversations() {
    memoryService.summarizeInactiveConversations(Instant.now(clock));
}

@Scheduled(cron = "0 20 3 * * *")
public void summarizeLongConversations() {
    memoryService.summarizeLongConversations(Instant.now(clock));
}

@Scheduled(cron = "0 30 3 * * *")
public void cleanExpiredRawData() {
    memoryService.deleteExpiredRawData(Instant.now(clock));
}
```

摘要生成规则：

1. `ACTIVE` 会话超过 60 分钟未活跃：先生成摘要，再将会话置为 `CLOSED`；
2. 当前会话用户/助手消息对超过 20 轮：基于尚未被摘要覆盖的消息生成滚动摘要；
3. 摘要调用现有 `ChatService.reply`，提示词只能总结事实、待办、工具状态和明确偏好，不得杜撰；
4. 摘要生成失败时保留原会话与消息，记录 WARN，下一次任务重试；
5. 删除条件只使用 `expires_at < now`，绝不删除 `conversation_summaries` 和 `user_preferences`。

- [ ] **步骤 4：运行维护测试确认通过**

运行：

```powershell
mvn -q -Dtest=WechatMemoryMaintenanceSchedulerTests test
```

预期：通过；过期原文与工具日志被删除，摘要和偏好仍存在。

- [ ] **步骤 5：提交本任务**

```powershell
git add src/main/java/com/example/spring/AgentClawApplication.java src/main/java/com/example/spring/wechat/memory/scheduler src/main/java/com/example/spring/wechat/memory/service src/test/java/com/example/spring/wechat/memory/WechatMemoryMaintenanceSchedulerTests.java
git commit -m "feat: summarize and retain WeChat memory safely"
```

### Task 9：端到端验证、配置说明与回归

**文件：**

- 修改：`README.md`
- 修改：`docs/PROJECT_STRUCTURE.md`
- 修改：`src/main/resources/application.properties`

- [ ] **步骤 1：补充集成验收测试**

覆盖以下场景：

```java
@Test
void conversationContextSurvivesRestartButCliMemoryIsUnaffected() {
    // 1. 微信 user-1 完成“我在杭州”的对话并写入 MySQL。
    // 2. 重新构造微信服务，验证“今天天气呢”可从持久化状态取到杭州。
    // 3. 使用 CLI handle("你好")，验证不会产生 users/conversations 记录。
}
```

- [ ] **步骤 2：运行测试确认当前缺口**

运行：

```powershell
mvn -q test
```

预期：若仍有旧构造器或模拟对象未调整，测试失败；逐项修正后再继续。

- [ ] **步骤 3：完成配置与文档**

在 `README.md` 增加：

```text
MYSQL_URL=jdbc:mysql://127.0.0.1:3306/openclaw?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai
MYSQL_USERNAME=root
MYSQL_PASSWORD=你的本机密码
FLYWAY_ENABLED=true
```

并记录：

- 首次运行前创建空库：`CREATE DATABASE openclaw CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;`
- 应用启动时 Flyway 自动建表；
- 微信上下文 60 分钟切会话，原始消息与工具日志保存 30 天；
- 所有数据库密码只放 `.env`，不得提交到 Git；
- MySQL 临时不可用时仅回退当前进程内存，应用重启会失去故障期临时记录。

- [ ] **步骤 4：完成全量验证**

运行：

```powershell
mvn -q test
mvn -q -DskipTests compile
```

预期：两个命令均成功；无编译错误、无测试失败。

随后用真实本机 MySQL 进行手工验收：

```powershell
Copy-Item .env.example .env
# 编辑 .env 后执行
mvn spring-boot:run
```

在微信依次发送“我在杭州”“今天怎么样”，重启程序后再发送“明天呢”，预期助手仍能关联杭州；等待 60 分钟或手动把 `last_active_at` 调整到 60 分钟前后，再发送消息，预期产生新的 `conversations` 记录。

- [ ] **步骤 5：提交本任务**

```powershell
git add README.md docs/PROJECT_STRUCTURE.md src/main/resources/application.properties src/test/java
git commit -m "docs: document WeChat MySQL memory setup"
```

## 实施前需要的本机信息

开始 Task 1 前，需要在 `.env`（不提交 Git）中填写：

```properties
MYSQL_URL=jdbc:mysql://127.0.0.1:3306/openclaw?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai
MYSQL_USERNAME=root
MYSQL_PASSWORD=
```

若本机 MySQL 尚未创建 `openclaw` 数据库，先执行：

```sql
CREATE DATABASE openclaw CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

## 计划自检

- 已覆盖：微信端持久化历史、60 分钟切会话、临时状态 JSON、明确偏好、30 天清理、会话摘要、工具日志、消息幂等、数据库故障兜底。
- 未改变：CLI 内存逻辑、现有天气/图像/语音/工具编排框架、微信发送流程。
- 未采用：数据库恢复后自动回写故障期内存数据，避免重复投递与冲突。
