# QQ 邮箱 Agent 工具 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为 OpenClaw 增加通过 QQ 邮箱 SMTP 发送纯文本邮件的 `email_send` 微信 Agent 工具，并实现白名单自动发送、非白名单确认发送。

**Architecture:** 新增 `wechat.email` 包承载配置、邮件请求模型、发送客户端、待确认草稿和服务编排；新增 `EmailWechatTool` 接入现有 `WechatToolRegistry`。SMTP 发送通过 Spring Mail 封装为 `EmailClient`，单元测试使用假的客户端避免真实发信。

**Tech Stack:** Java 17, Spring Boot 3.4.7, Spring Mail, JUnit 5, AssertJ, Mockito.

---

### Task 1: 邮箱配置和请求模型

**Files:**
- Create: `src/main/java/com/example/spring/wechat/email/config/EmailProperties.java`
- Create: `src/main/java/com/example/spring/wechat/email/model/EmailMessage.java`
- Create: `src/test/java/com/example/spring/wechat/email/config/EmailPropertiesTests.java`

- [ ] **Step 1: Write the failing test**

Create `EmailPropertiesTests` covering QQ defaults, trimming, timeout defaults, max body defaults, and case-insensitive whitelist matching.

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -Dtest=EmailPropertiesTests test`

Expected: compilation fails because `EmailProperties` does not exist.

- [ ] **Step 3: Write minimal implementation**

Create `EmailProperties` as a `@ConfigurationProperties(prefix = "email")` record with nested `Smtp` record. Add helpers `allowedRecipientSet()`, `isAllowedRecipient(String)`, and `fromAddress()`. Create `EmailMessage` record with normalized recipient lists and stripped subject/body.

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -Dtest=EmailPropertiesTests test`

Expected: test passes.

### Task 2: 邮件服务发送策略

**Files:**
- Create: `src/main/java/com/example/spring/wechat/email/client/EmailClient.java`
- Create: `src/main/java/com/example/spring/wechat/email/client/EmailClientException.java`
- Create: `src/main/java/com/example/spring/wechat/email/model/EmailSendResult.java`
- Create: `src/main/java/com/example/spring/wechat/email/service/PendingEmailDraftService.java`
- Create: `src/main/java/com/example/spring/wechat/email/service/EmailService.java`
- Create: `src/test/java/com/example/spring/wechat/email/service/EmailServiceTests.java`

- [ ] **Step 1: Write the failing tests**

Create service tests for disabled config, missing required fields, invalid address, whitelist direct send, non-whitelist pending draft without SMTP call, valid confirmation send, wrong-session token rejection, expired token rejection, and SMTP exception mapping.

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -Dtest=EmailServiceTests test`

Expected: compilation fails because service classes do not exist.

- [ ] **Step 3: Write minimal implementation**

Implement `EmailService` with conservative validation, whitelist decision, in-memory draft storage, confirmation token checks, and safe user-facing Chinese messages. Implement `PendingEmailDraftService` with injectable `Clock` for expiry tests.

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -Dtest=EmailServiceTests test`

Expected: test passes.

### Task 3: SMTP client and Spring configuration

**Files:**
- Modify: `pom.xml`
- Modify: `src/main/java/com/example/spring/AgentClawApplication.java`
- Modify: `src/main/resources/application.properties`
- Modify: `.env.example`
- Create: `src/main/java/com/example/spring/wechat/email/client/SmtpEmailClient.java`
- Create: `src/main/java/com/example/spring/wechat/email/config/EmailMailConfiguration.java`
- Create: `src/test/java/com/example/spring/wechat/email/client/SmtpEmailClientTests.java`

- [ ] **Step 1: Write the failing tests**

Create tests that verify `SmtpEmailClient` maps `EmailMessage` into a `SimpleMailMessage`, uses configured from address, and converts `MailException` into `EmailClientException`.

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -Dtest=SmtpEmailClientTests test`

Expected: compilation fails because SMTP client classes do not exist and Spring Mail dependency is missing.

- [ ] **Step 3: Write minimal implementation**

Add `spring-boot-starter-mail`, register `EmailProperties`, add email defaults to config files, create `JavaMailSender` configuration using QQ SMTP properties, and implement `SmtpEmailClient`.

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -Dtest=SmtpEmailClientTests test`

Expected: test passes.

### Task 4: 微信工具接入

**Files:**
- Create: `src/main/java/com/example/spring/wechat/conversation/tools/EmailWechatTool.java`
- Create: `src/test/java/com/example/spring/wechat/conversation/tools/EmailWechatToolTests.java`
- Modify: `src/main/java/com/example/spring/wechat/conversation/agent/FunctionCallingAgentLoop.java`

- [ ] **Step 1: Write the failing tests**

Create tool tests for exposed name, parameters, capability guidance, argument forwarding, and confirmation token forwarding.

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -Dtest=EmailWechatToolTests test`

Expected: compilation fails because `EmailWechatTool` does not exist.

- [ ] **Step 3: Write minimal implementation**

Implement `EmailWechatTool` as a Spring component with `email_send` tool definition. Update the agent system prompt to mention email as an external side-effect tool requiring clarification when intent is uncertain.

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -Dtest=EmailWechatToolTests test`

Expected: test passes.

### Task 5: 集成验证

**Files:**
- Modify only files touched above if verification exposes compile or behavior gaps.

- [ ] **Step 1: Run focused test suite**

Run: `mvn -Dtest=EmailPropertiesTests,EmailServiceTests,SmtpEmailClientTests,EmailWechatToolTests test`

Expected: all focused tests pass.

- [ ] **Step 2: Run broader verification**

Run: `mvn test`

Expected: all project tests pass, or any unrelated environmental failure is documented with the exact failing test and reason.

- [ ] **Step 3: Review diff**

Run: `git diff --stat` and `git diff --check`

Expected: diff only contains email feature, config, docs plan, and no whitespace errors.
