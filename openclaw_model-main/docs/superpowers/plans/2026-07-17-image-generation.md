# 微信端图片生成模块实现计划

> 现状更新：图片生成能力已经从 CLI 端移除，只保留在微信端。图片生成相关代码统一放在 `com.example.spring.wechat.image.generation` 下，由微信工具 `ImageGenerationWechatTool` 调用。

## 目标

- 微信端通过自然语言识别图片生成、图片修改、提示词优化等需求。
- 图片生成前可以先调用文本大模型优化提示词。
- 如果用户说“先别生成、等我确认”，只返回优化后的提示词。
- 用户确认后再调用图片生成 API。
- 生成完成后优先发送真实图片，发送失败时返回清晰错误信息。

## 当前调用链

```text
微信用户文本
  ↓
IlinkWechatClient
  ↓
WechatBotService
  ↓
WechatConversationService
  ↓
ToolCallPlanner / ImageGenerationIntentParser
  ↓
ImageGenerationWechatTool
  ↓
ImageGenerationService
  ↓
DashScopeImageGenerationClient
  ↓
WechatReply 图片回复
```

## 核心文件

```text
src/main/java/com/example/spring/wechat/conversation/tools/ImageGenerationWechatTool.java
src/main/java/com/example/spring/wechat/image/generation/ImageGenerationClient.java
src/main/java/com/example/spring/wechat/image/generation/ImageGenerationException.java
src/main/java/com/example/spring/wechat/image/generation/client/DashScopeImageGenerationClient.java
src/main/java/com/example/spring/wechat/image/generation/intent/ImageGenerationIntentParser.java
src/main/java/com/example/spring/wechat/image/generation/model/ImageGenerationRequest.java
src/main/java/com/example/spring/wechat/image/generation/model/ImageGenerationResult.java
src/main/java/com/example/spring/wechat/image/generation/service/ImageGenerationService.java
```

## 测试文件

```text
src/test/java/com/example/spring/wechat/image/generation/client/DashScopeImageGenerationClientTests.java
src/test/java/com/example/spring/wechat/image/generation/intent/ImageGenerationIntentParserTests.java
src/test/java/com/example/spring/wechat/image/generation/service/ImageGenerationServiceTests.java
src/test/java/com/example/spring/wechat/conversation/WechatImageConversationServiceTests.java
src/test/java/com/example/spring/wechat/bot/WechatBotServiceTests.java
src/test/java/com/example/spring/ApplicationContextTests.java
```

其中 `ApplicationContextTests` 额外验证 CLI 不再注册 `image` 命令。

## 错误处理要求

- 提示词为空：提示用户说明想生成什么图片。
- API Key 未配置：提示图片生成服务未配置。
- API 没有返回图片：提示生成失败，稍后重试。
- 图片下载失败：返回明确失败信息。
- 微信图片发送失败：由发送层记录日志，避免整条消息静默失败。

## 验证命令

```powershell
mvn clean test
```
