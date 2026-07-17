# Project Structure

本文档用于快速理解 OpenClaw CLI 的文件组织、核心流程和各模块职责。

## 1. 分层思路

项目按功能分层，而不是把所有逻辑写在一个类里：

```text
入口层       CLI / 微信 iLink
分发层       AgentService / CommandDispatcher / WechatBotService
业务层       ChatService / WeatherService / ImageGenerationService / WechatConversationService
外部服务层   DashScopeChatClient / DashScopeImageGenerationClient / AmapWeatherClient / IlinkWechatClient
模型层       ChatReply / WeatherResult / WechatIncomingMessage 等数据对象
```

这样做的好处：

- 每个类职责更单一
- 命令、天气、大模型、微信可以独立测试
- 后续新增工具时，不需要大规模改原有代码

## 2. CLI 流程

```text
ConsoleRunner
  -> AgentService
  -> CommandDispatcher
      |-- / 开头：CommandRegistry 找命令执行
      `-- 普通文本：ChatService 调用大模型
```

相关文件：

- `cli/ConsoleRunner.java`
- `agent/AgentService.java`
- `command/CommandDispatcher.java`
- `command/*Command.java`

## 3. 大模型流程

```text
ChatService
  -> DashScopeChatClient
      -> POST /chat/completions
      -> qwen3.7-plus
      -> 解析 stream 返回
      -> ReplyEmitter 输出
```

相关文件：

- `chat/ChatService.java`
- `chat/ChatClient.java`
- `chat/DashScopeChatClient.java`
- `chat/ChatReply.java`

## 4. 天气工具流程

```text
WeatherCommand / WechatConversationService
  -> WeatherService
  -> AmapWeatherClient
  -> 高德天气 API
  -> WeatherResult
  -> 大模型整理自然回复
```

相关文件：

- `weather/AmapWeatherClient.java`
- `weather/WeatherService.java`
- `weather/WeatherResult.java`
- `tool/WeatherTool.java`
- `command/WeatherCommand.java`

## 5. 微信流程

```text
/wechat start
  -> WechatCommand
  -> WechatBotService.start()
  -> IlinkWechatClient.executeLogin()
  -> 用户扫码
  -> loginFuture 获取 botId
  -> getUpdates() 轮询消息
  -> WechatConversationService 处理消息
  -> sendText() 回复用户
```

相关文件：

- `command/WechatCommand.java`
- `wechat/bot/WechatBotService.java`
- `wechat/client/IlinkWechatClient.java`
- `wechat/conversation/WechatConversationService.java`
- `wechat/client/WechatIncomingMessage.java`

## 6. 微信上下文记忆

微信端按 `from_user_id` 保存最近 6 轮对话：

```text
用户 A -> A 的上下文
用户 B -> B 的上下文
```

每次调用大模型前，会把最近对话和当前消息一起组成 prompt：

```text
最近对话：
用户：我想出去玩
助手：你偏好什么类型？

当前用户：可以，偏好美食
请直接回复用户。
```

这样模型能理解短句和追问，不会把每一句都当成孤立输入。

## 7. iLink 接收日志

`IlinkWechatClient` 会记录：

```text
messageId
fromUserId
contextToken
text
```

排查微信不回复时，按这个顺序看日志：

```text
iLink 收到文本消息
  -> 微信收到消息
  -> 微信会话收到消息
  -> 微信回复发送完成
```

缺在哪一步，就说明问题在哪一层。

## 8. 配置文件

```text
.env.example                         # 配置示例，不放真实 Key
.env                                 # 本地真实配置，不提交 Git
src/main/resources/application.properties
```

主要配置：

```properties
AMAP_WEATHER_KEY=你的高德 Key
DASHSCOPE_API_KEY=你的百炼 Key
dashscope.model=qwen3.7-plus
dashscope.image-model=qwen-image-2.0-pro
```

## 9. 测试目录

测试文件和源码包结构保持一致：

```text
src/test/java/com/example/spring
|-- chat
|-- cli
|-- command
|-- weather
`-- wechat
    |-- bot
    `-- conversation
```

重点测试：

- `DashScopeChatClientTests`：验证大模型请求体和流式解析
- `WeatherIntentParserTests`：验证天气意图和城市提取
- `WechatConversationServiceTests`：验证微信天气问题、普通对话、上下文记忆
- `WechatBotServiceTests`：验证微信登录、消息回复、长消息分段、队列处理
- `ImageGenerationIntentParserTests`：验证图片生成意图识别
- `DashScopeImageGenerationClientTests`：验证文生图请求体和响应解析
- `ImageGenerationServiceTests`：验证图片下载与结果封装
- `ImageCommandTests`：验证 CLI `/image` 命令

## 10. 图片识别流程

图片识别现在走独立的微信图像链路：

```text
iLink 接收图片消息 / 图片链接
  -> IlinkWechatClient 组装 WechatIncomingMessage
  -> ImageInputResolver 识别来源、尺寸、色彩模式
  -> DashScopeImageUnderstandingClient 发送多模态请求
  -> WechatConversationService 先描述图片，再写入上下文
  -> 后续文本提问继续沿用这段图片上下文
```

新增/相关文件：

- `wechat/client/WechatIncomingImage.java`
- `wechat/image/model/ImageAnalysisRequest.java`
- `wechat/image/service/ImageInputResolver.java`
- `wechat/image/service/ImageUnderstandingService.java`
- `wechat/image/client/DashScopeImageUnderstandingClient.java`

## 11. 图片生成流程

图片生成和图片识别是两条不同的链路：

```text
CLI / 微信自然语言图片生成
  -> ImageGenerationIntentParser 识别意图
  -> ImageGenerationService 组织请求
  -> DashScopeImageGenerationClient 调用百炼文生图接口
  -> 下载图片 bytes
  -> CLI 保存到本地 / 微信通过 iLink sendImage() 发图
```

新增/相关文件：

- `image/generation/ImageGenerationRequest.java`
- `image/generation/ImageGenerationResult.java`
- `image/generation/ImageGenerationService.java`
- `image/generation/client/DashScopeImageGenerationClient.java`
- `command/ImageCommand.java`
- `wechat/bot/WechatReply.java`
- `image/generation/ImageGenerationIntentParser.java`
