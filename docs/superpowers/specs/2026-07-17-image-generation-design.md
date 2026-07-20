# 微信端图片生成模块设计

## 设计结论

图片生成能力只保留在微信端，不再作为 CLI 命令暴露。这样做的原因是：当前图片生成的主要交互场景是微信聊天，用户需要直接收到图片，而 CLI 更适合作为调试、基础命令和普通文本对话入口。

## 功能边界

微信端图片生成支持：

- 直接生成图片，例如“帮我生成一张赛博朋克风格的橘猫”。
- 先优化提示词，再生成图片。
- 用户要求确认时，只返回优化后的提示词。
- 根据最近一次图片生成结果或用户刚发送的图片进行修改。
- 结合上下文理解“把刚才那张图改成暖色调”等追问。

CLI 端不支持：

- 不再支持 `/image` 命令。
- 不再保存生成图片到 CLI 本地目录。

## 模块位置

```text
com.example.spring.wechat.image.generation
```

该包内聚在 `wechat` 下，表示图片生成是微信端多模态能力的一部分。

## 关键对象

```text
ImageGenerationClient
ImageGenerationException
DashScopeImageGenerationClient
ImageGenerationIntentParser
ImageGenerationRequest
ImageGenerationResult
ImageGenerationService
ImageGenerationWechatTool
```

## 调用流程

```text
用户发送图片生成需求
  ↓
WechatConversationService
  ↓
优先走结构化工具计划
  ↓
如果模型计划失败，则用 ImageGenerationIntentParser 兜底
  ↓
ImageGenerationWechatTool 处理提示词优化、确认逻辑、上下文图片修改
  ↓
ImageGenerationService 调用图片生成客户端并下载图片二进制
  ↓
WechatReply 返回图片 part
  ↓
WechatBotService 发送到微信
```

## 验收标准

- CLI 命令注册中心不包含 `image` 命令。
- 微信端可以继续生成图片。
- 微信端可以先优化提示词再生成图片。
- 微信端可以结合上下文修改图片。
- 完整测试通过。
