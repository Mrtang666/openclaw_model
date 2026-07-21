# 微信图片识别模块实现计划

> 给后续开发者看的说明：本文档记录微信图片识别能力的设计和实现步骤。以后此类 plan/spec 文档统一使用中文。

## 目标

让微信端可以接收用户发送的图片、图片链接或图片附件，并做到：

- 先识别图片内容；
- 把识别出的内容用自然语言描述给用户；
- 把图片描述写入该用户的会话上下文；
- 后续用户继续追问“这张图适合做头像吗”“帮我改成赛博朋克风格”等问题时，可以结合上一张图片理解。

## 总体架构

微信图片识别流程从 iLink 收到消息开始。系统需要把微信消息中的文本、图片链接、二进制图片等信息统一转换成项目内部的图片模型，然后交给图片理解服务。

核心链路：

```text
微信用户发送图片
  -> IlinkWechatClient 解析消息
  -> WechatIncomingMessage 携带图片列表
  -> WechatConversationService 判断是否有图片
  -> ImageInputResolver 统一图片来源
  -> DashScopeImageUnderstandingClient 调用多模态模型
  -> 返回图片描述
  -> 写入用户会话记忆
  -> 回复用户
```

## 技术栈

- Spring Boot
- Java 17
- RestClient
- Jackson
- JUnit 5
- Mockito
- Spring Test `MockRestServiceServer`
- iLink SDK
- 阿里百炼兼容模式多模态接口

## 任务 1：扩展微信入站消息模型

涉及文件：

- `src/main/java/com/example/spring/wechat/model/WechatIncomingMessage.java`
- `src/main/java/com/example/spring/wechat/model/WechatIncomingImage.java`
- `src/main/java/com/example/spring/wechat/model/ImageSourceType.java`
- `src/main/java/com/example/spring/wechat/adapter/ilink/IlinkWechatClient.java`
- `src/test/java/com/example/spring/wechat/adapter/ilink/IlinkWechatClientTests.java`

实现步骤：

- [ ] 给 `WechatIncomingMessage` 增加图片列表字段。
- [ ] 新增 `WechatIncomingImage`，保存图片 URL、本地路径、二进制数据、来源类型等信息。
- [ ] 在 `IlinkWechatClient` 中解析 iLink 消息里的图片项。
- [ ] 编写测试，确认一条微信消息可以同时携带文本和图片。

推荐测试：

```java
@Test
void mapsIlinkImageMessagesIntoWechatImagePayloads() {
    // 构造一条包含文本和图片的 iLink 消息
    // 期望 getUpdates() 返回的内部消息中同时包含 text 和 image payload
}
```

推荐验证命令：

```powershell
mvn -q -Dtest=IlinkWechatClientTests test
```

## 任务 2：实现图片理解客户端和服务层

涉及文件：

- `src/main/java/com/example/spring/wechat/image/client/ImageUnderstandingClient.java`
- `src/main/java/com/example/spring/wechat/image/client/DashScopeImageUnderstandingClient.java`
- `src/main/java/com/example/spring/wechat/image/model/ImageAnalysisRequest.java`
- `src/main/java/com/example/spring/wechat/image/model/ImageAnalysisResult.java`
- `src/main/java/com/example/spring/wechat/image/model/ResolvedImageInput.java`
- `src/main/java/com/example/spring/wechat/image/service/ImageUnderstandingService.java`
- `src/main/java/com/example/spring/wechat/image/service/DefaultImageUnderstandingService.java`
- `src/main/java/com/example/spring/wechat/image/service/ImageInputResolver.java`
- `src/test/java/com/example/spring/wechat/image/client/DashScopeImageUnderstandingClientTests.java`
- `src/test/java/com/example/spring/wechat/image/service/ImageInputResolverTests.java`

实现步骤：

- [ ] 编写图片理解客户端测试，确认请求体包含文本和 `image_url`。
- [ ] 实现 DashScope 多模态调用，把图片和用户文本一起发给模型。
- [ ] 实现 `ImageInputResolver`，统一处理图片 URL、本地文件和二进制图片。
- [ ] 实现 `DefaultImageUnderstandingService`，对外提供“分析图片”的统一方法。

推荐测试：

```java
@Test
void sendsTextPlusImageUrlRequestToDashScope() {
    // 期望请求中包含 system prompt
    // 期望 user content 中包含 text 和 image_url
}
```

推荐验证命令：

```powershell
mvn -q -Dtest=DashScopeImageUnderstandingClientTests,ImageInputResolverTests test
```

## 任务 3：接入微信会话流程

涉及文件：

- `src/main/java/com/example/spring/wechat/conversation/WechatConversationService.java`
- `src/main/java/com/example/spring/wechat/bot/WechatBotService.java`
- `src/test/java/com/example/spring/wechat/conversation/WechatConversationServiceTests.java`
- `src/test/java/com/example/spring/wechat/bot/WechatBotServiceTests.java`

实现步骤：

- [ ] 在 `WechatConversationService` 中识别图片消息。
- [ ] 图片消息优先进入图片理解流程。
- [ ] 模型识别后，先回复图片内容描述。
- [ ] 把图片描述写入当前用户的会话记忆。
- [ ] 后续文本追问时，把最近图片描述作为上下文传给大模型。

推荐测试：

```java
@Test
void describesImageFirstAndKeepsTheDescriptionInLaterTurns() {
    // 第一轮：用户发图片，系统返回图片描述
    // 第二轮：用户追问，prompt 中应包含上一轮图片描述
}
```

推荐验证命令：

```powershell
mvn -q -Dtest=WechatConversationServiceTests test
```

## 任务 4：完善文档和整体回归

涉及文件：

- `README.md`
- `docs/PROJECT_STRUCTURE.md`

实现步骤：

- [ ] 在 README 中说明微信图片识别的使用方式。
- [ ] 在项目结构文档中补充 `wechat.image` 模块职责。
- [ ] 运行完整测试。
- [ ] 如果需要人工验证，再启动项目并在微信端发送图片测试。

推荐验证命令：

```powershell
mvn -q test
```

## 验收标准

- 微信端能接收纯图片消息；
- 微信端能接收“图片 + 文字”的混合消息；
- 图片识别结果能自然地描述给用户；
- 图片描述能进入上下文；
- 后续追问能引用刚才那张图片；
- 图片识别失败时，用户能收到清晰的错误提示。
