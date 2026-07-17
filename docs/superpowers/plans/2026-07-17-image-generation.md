# Image Generation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a shared image-generation module for CLI and WeChat, with `/image` in CLI and natural-language image generation in WeChat.

**Architecture:** Build one shared DashScope client and service under `com.example.spring.image.generation`, then wire it into the command layer and the WeChat conversation/bot flow. WeChat will return a structured reply so the bot can send either text or an actual image through iLink.

**Tech Stack:** Java 17, Spring Boot, RestClient, Jackson, JUnit 5, iLink SDK.

---

### Task 1: Add image-generation core and tests

**Files:**
- Create: `src/main/java/com/example/spring/image/generation/ImageGenerationRequest.java`
- Create: `src/main/java/com/example/spring/image/generation/ImageGenerationResult.java`
- Create: `src/main/java/com/example/spring/image/generation/ImageGenerationException.java`
- Create: `src/main/java/com/example/spring/image/generation/ImageGenerationClient.java`
- Create: `src/main/java/com/example/spring/image/generation/ImageGenerationService.java`
- Create: `src/main/java/com/example/spring/image/generation/client/DashScopeImageGenerationClient.java`
- Create: `src/test/java/com/example/spring/image/generation/client/DashScopeImageGenerationClientTests.java`
- Create: `src/test/java/com/example/spring/image/generation/ImageGenerationServiceTests.java`

- [ ] **Step 1: Write the failing test**

Cover:

- blank prompt rejected
- request body includes model and prompt
- successful response returns the first image URL
- API error becomes `ImageGenerationException`

- [ ] **Step 2: Run the tests and confirm they fail**

Run:

```powershell
mvn test -Dtest=DashScopeImageGenerationClientTests,ImageGenerationServiceTests -q
```

- [ ] **Step 3: Implement the minimal client and service**

Use `RestClient` with `dashscope.image-base-url`, `dashscope.image-model`, `dashscope.api-key`.

- [ ] **Step 4: Re-run the tests**

Expected: pass.

### Task 2: Add `/image` CLI command

**Files:**
- Create: `src/main/java/com/example/spring/command/ImageCommand.java`
- Modify: `src/main/java/com/example/spring/command/CommandRegistry.java`
- Modify: `src/main/java/com/example/spring/command/CommandDispatcher.java`
- Modify: `src/main/java/com/example/spring/command/HelpCommand.java`
- Create: `src/test/java/com/example/spring/command/ImageCommandTests.java`
- Modify: `src/test/java/com/example/spring/command/CommandDispatcherTests.java`

- [ ] **Step 1: Write failing tests**

Cover:

- `/image` dispatches to the new command
- blank args print usage
- help output includes `/image`

- [ ] **Step 2: Run tests and verify red**

Run:

```powershell
mvn test -Dtest=ImageCommandTests,CommandDispatcherTests -q
```

- [ ] **Step 3: Add the command implementation**

Call `ImageGenerationService`, print URL/path, and keep the behavior deterministic.

- [ ] **Step 4: Re-run the tests**

Expected: pass.

### Task 3: Add WeChat image generation and image sending

**Files:**
- Create: `src/main/java/com/example/spring/wechat/bot/WechatReply.java`
- Modify: `src/main/java/com/example/spring/wechat/client/WechatClient.java`
- Modify: `src/main/java/com/example/spring/wechat/client/IlinkWechatClient.java`
- Create: `src/main/java/com/example/spring/image/generation/ImageGenerationIntentParser.java`
- Modify: `src/main/java/com/example/spring/wechat/conversation/WechatConversationService.java`
- Modify: `src/main/java/com/example/spring/wechat/bot/WechatBotService.java`
- Create: `src/test/java/com/example/spring/image/generation/ImageGenerationIntentParserTests.java`
- Create: `src/test/java/com/example/spring/wechat/conversation/WechatImageGenerationConversationTests.java`
- Modify: `src/test/java/com/example/spring/wechat/bot/WechatBotServiceTests.java`

- [ ] **Step 1: Write failing tests**

Cover:

- image-generation keywords route to image generation before weather
- structured WeChat reply includes an image
- `WechatClient.sendImage(...)` is called
- send-image fallback uses text when image transfer fails

- [ ] **Step 2: Run tests and confirm they fail**

Run:

```powershell
mvn test -Dtest=ImageGenerationIntentParserTests,WechatImageGenerationConversationTests,WechatBotServiceTests -q
```

- [ ] **Step 3: Implement structured reply flow**

Keep chat streaming for text, but use a `WechatReply` object for image generation so the bot can send the real image.

- [ ] **Step 4: Re-run the tests**

Expected: pass.

### Task 4: Update config and docs

**Files:**
- Modify: `src/main/resources/application.properties`
- Modify: `README.md`
- Modify: `docs/PROJECT_STRUCTURE.md` if needed

- [ ] **Step 1: Add image-generation properties**

Include image base URL, model, size, and watermark settings.

- [ ] **Step 2: Document CLI and WeChat usage**

Add `/image` examples and explain the WeChat trigger words.

- [ ] **Step 3: Run the full test suite**

Run:

```powershell
mvn test -q
```

Expected: all tests pass.
