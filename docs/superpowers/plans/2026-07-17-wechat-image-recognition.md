# WeChat Image Recognition Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let WeChat accept image messages and image links, describe the image first, then reuse that description as context for later chat turns.

**Architecture:** Extend the iLink inbound message model so each message can carry text plus one or more image payloads. Add a DashScope multimodal client that sends text + `image_url` content to the same compatible chat endpoint, and add a WeChat image understanding service that resolves attachments, image links, and binary payloads into a normalized analysis request. The conversation service should answer image messages with a first-pass description, store that exchange in per-user memory, and include that memory in later text follow-ups.

**Tech Stack:** Spring Boot 3.4, Java 17, Spring Web `RestClient`, Jackson, JUnit 5, Mockito, Spring Test `MockRestServiceServer`, iLink SDK.

---

### Task 1: Extend inbound WeChat image model and parser tests

**Files:**
- Modify: `src/main/java/com/example/spring/wechat/client/WechatIncomingMessage.java`
- Create: `src/main/java/com/example/spring/wechat/client/WechatIncomingImage.java`
- Modify: `src/main/java/com/example/spring/wechat/client/IlinkWechatClient.java`
- Create: `src/test/java/com/example/spring/wechat/client/IlinkWechatClientTests.java`

- [ ] **Step 1: Write the failing test**

```java
@Test
void mapsIlinkImageMessagesIntoWechatImagePayloads() {
    // build a WeixinMessage with one text item and one image item
    // expect getUpdates() to return one message with text + one image payload
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -Dtest=IlinkWechatClientTests test -q`

- [ ] **Step 3: Write minimal implementation**

```java
// add image payload fields to WechatIncomingMessage
// map MessageItem#getImage_item() into WechatIncomingImage
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -Dtest=IlinkWechatClientTests test -q`

### Task 2: Add multimodal image understanding client and service tests

**Files:**
- Create: `src/main/java/com/example/spring/wechat/image/client/ImageUnderstandingClient.java`
- Create: `src/main/java/com/example/spring/wechat/image/client/DashScopeImageUnderstandingClient.java`
- Create: `src/main/java/com/example/spring/wechat/image/model/ImageAnalysisResult.java`
- Create: `src/main/java/com/example/spring/wechat/image/model/ImageSourceType.java`
- Create: `src/main/java/com/example/spring/wechat/image/model/ResolvedImageInput.java`
- Create: `src/main/java/com/example/spring/wechat/image/service/ImageUnderstandingService.java`
- Create: `src/main/java/com/example/spring/wechat/image/service/DefaultImageUnderstandingService.java`
- Create: `src/main/java/com/example/spring/wechat/image/service/ImageInputResolver.java`
- Create: `src/test/java/com/example/spring/wechat/image/client/DashScopeImageUnderstandingClientTests.java`
- Create: `src/test/java/com/example/spring/wechat/image/service/DefaultImageUnderstandingServiceTests.java`

- [ ] **Step 1: Write the failing test**

```java
@Test
void sendsTextPlusImageUrlRequestToDashScope() {
    // expect one system prompt and one user content array containing text + image_url
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -Dtest=DashScopeImageUnderstandingClientTests test -q`

- [ ] **Step 3: Write minimal implementation**

```java
// build OpenAI-compatible multimodal payload
// include image metadata and source type in the prompt
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -Dtest=DashScopeImageUnderstandingClientTests test -q`

### Task 3: Wire image replies into WeChat conversation flow

**Files:**
- Modify: `src/main/java/com/example/spring/wechat/conversation/WechatConversationService.java`
- Modify: `src/main/java/com/example/spring/wechat/bot/WechatBotService.java`
- Modify: `src/test/java/com/example/spring/wechat/conversation/WechatConversationServiceTests.java`
- Modify: `src/test/java/com/example/spring/wechat/bot/WechatBotServiceTests.java`

- [ ] **Step 1: Write the failing test**

```java
@Test
void describesImageFirstAndKeepsTheDescriptionInLaterTurns() {
    // first image message returns a description
    // next text-only turn should include that description in the prompt
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -Dtest=WechatConversationServiceTests test -q`

- [ ] **Step 3: Write minimal implementation**

```java
// add image-aware handle/handleStreaming overloads
// store the image description in conversation memory
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -Dtest=WechatConversationServiceTests test -q`

### Task 4: Verify the full project

**Files:**
- Modify: `README.md`
- Modify: `docs/PROJECT_STRUCTURE.md`

- [ ] **Step 1: Run the full test suite**

Run: `mvn test -q`

- [ ] **Step 2: Update docs**

```text
Add the new image flow and the new wechat/image package structure.
```

- [ ] **Step 3: Confirm the project still starts**

Run: `mvn spring-boot:run`

