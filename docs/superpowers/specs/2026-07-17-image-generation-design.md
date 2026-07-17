# Image Generation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add an image generation module that works in CLI and WeChat: CLI uses an `/image` command, and WeChat can recognize image-generation intent, call Alibaba Bailian, and send the generated picture back.

**Architecture:** A shared `image.generation` service will call Alibaba Bailian’s image-generation API, normalize the result into a small domain model, and download the generated image bytes when needed. CLI will consume the service through a new command, while WeChat will route generation intent through the conversation layer, return a structured reply, and let the bot decide whether to send text, an image, or both.

**Tech Stack:** Java 17, Spring Boot, RestClient, Jackson, Alibaba Bailian compatible API, iLink SDK.

---

## Scope

This feature adds one new capability:

- generate an image from a text prompt

It does not replace the existing text chat, weather, or image-understanding paths.

## User-facing behavior

### CLI

- `/image <prompt>` generates an image.
- If the prompt is blank, the command returns a clear usage message.
- If generation succeeds, CLI prints:
  - the prompt used
  - the returned image URL
  - the local file path if the image is downloaded and saved

### WeChat

- Normal chat still works as before.
- If a message looks like an image-generation request, the bot:
  1. sends a short “I’m generating” status message
  2. calls the image-generation API
  3. sends the generated image back through iLink
  4. falls back to a text link if image sending fails

### Intent examples

- “帮我画一只戴着耳机的橘猫”
- “生成一张国风海报”
- “做一张产品宣传图”

## Non-goals

- Do not change the existing image-understanding module.
- Do not add a web UI.
- Do not add multi-image editing or inpainting.
- Do not add user image uploads as input to image generation.

## Proposed file layout

### New files

- `src/main/java/com/example/spring/image/generation/ImageGenerationClient.java`
- `src/main/java/com/example/spring/image/generation/ImageGenerationException.java`
- `src/main/java/com/example/spring/image/generation/ImageGenerationService.java`
- `src/main/java/com/example/spring/image/generation/ImageGenerationIntentParser.java`
- `src/main/java/com/example/spring/image/generation/ImageGenerationResult.java`
- `src/main/java/com/example/spring/image/generation/ImageGenerationRequest.java`
- `src/main/java/com/example/spring/image/generation/client/DashScopeImageGenerationClient.java`
- `src/main/java/com/example/spring/command/ImageCommand.java`
- `src/main/java/com/example/spring/wechat/bot/WechatReply.java`

### Modify existing files

- `src/main/java/com/example/spring/command/CommandRegistry.java`
- `src/main/java/com/example/spring/command/CommandDispatcher.java`
- `src/main/java/com/example/spring/command/HelpCommand.java`
- `src/main/java/com/example/spring/cli/ConsoleRunner.java`
- `src/main/java/com/example/spring/wechat/client/WechatClient.java`
- `src/main/java/com/example/spring/wechat/client/IlinkWechatClient.java`
- `src/main/java/com/example/spring/wechat/conversation/WechatConversationService.java`
- `src/main/java/com/example/spring/wechat/bot/WechatBotService.java`
- `src/main/resources/application.properties`
- `README.md`

## API design

### ImageGenerationRequest

Carries the raw user prompt plus optional options.

Fields:

- `prompt`
- `styleHint`
- `width`
- `height`
- `watermark`

### ImageGenerationResult

Carries the normalized output of generation.

Fields:

- `prompt`
- `imageUrl`
- `imageBytes`
- `fileName`
- `contentType`
- `width`
- `height`

### WechatReply

Carries what the bot should send back to WeChat.

Fields:

- `text`
- `image` (`ImageGenerationResult`, optional)
- `imageCaption`

### ImageGenerationClient

Single responsibility: talk to Alibaba Bailian and return a normalized result.

### ImageGenerationService

Single responsibility: validate the prompt, call the client, download the image, and return a result suitable for CLI or WeChat.

## Flow

### CLI flow

```text
User types /image prompt
  -> CommandDispatcher
  -> ImageCommand
  -> ImageGenerationService
  -> DashScopeImageGenerationClient
  -> download image bytes
  -> print URL/path to console
```

### WeChat flow

```text
User sends message
  -> IlinkWechatClient
  -> WechatBotService
  -> WechatConversationService
  -> ImageGenerationIntentParser
  -> ImageGenerationService
  -> WechatReply
  -> WechatBotService
  -> WechatClient.sendImage(...)
```

## Intent routing rules

WeChat should detect image generation before falling back to weather detection or normal chat.

Recommended keyword set:

- 画
- 生成图片
- 做图
- 海报
- 插画
- 头像
- 表情包
- 设计图

If the message contains one of those keywords and a usable prompt, route to image generation.

If the prompt is too vague, respond with a short clarification request instead of calling the API.

## Alibaba Bailian integration

Use the current `DASHSCOPE_API_KEY` from `.env`.

Recommended model:

- `qwen-image-2.0-pro`

Recommended endpoint:

- `https://ws-6gncy95g9skiwjfi.cn-beijing.maas.aliyuncs.com/api/v1/services/aigc/multimodal-generation/generation`

Request rules:

- use JSON
- pass the prompt directly
- keep prompt enhancement enabled
- keep watermark configurable

Response rules:

- parse the first returned image URL
- preserve the original API response for debugging only at trace level
- do not log raw image bytes

## WeChat sending rules

The iLink client should support image sending, because text-only responses are not enough for this feature.

Rules:

- if `sendImage(...)` succeeds, send the image to the user
- if `sendImage(...)` fails, send a text fallback containing the image URL
- if generation itself fails, send a short user-facing error message

## Error handling

User-facing errors should be short and specific.

Examples:

- blank prompt -> “请告诉我你想生成什么图片”
- API key missing -> “图片生成服务未配置”
- API returned no image -> “图片生成失败，请稍后重试”
- download failed -> fall back to URL
- WeChat image sending failed -> fall back to text URL

## Testing plan

### Parser tests

Cover:

- normal prompt detection
- blank prompt rejection
- vague prompt handling
- keyword examples that should trigger generation

### Client tests

Cover:

- request body construction
- successful response parsing
- no-image response failure
- HTTP error response handling

### Service tests

Cover:

- prompt validation
- byte download
- fallback behavior
- exception mapping

### CLI and WeChat integration tests

Cover:

- `/image` dispatch
- help text includes the new command
- WeChat intent routes to image generation
- WeChat image send fallback works

## Rollout order

1. Add the image-generation domain model and client.
2. Add the CLI command.
3. Wire WeChat intent parsing and image sending.
4. Update docs and tests.

## Open implementation choices

The only implementation choice that matters later is whether to save generated images to disk for CLI output. The recommended behavior is yes, because the API image URL may expire and local files are easier to inspect.
