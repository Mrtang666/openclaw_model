# 语音上下文工具路由实现计划

> 给后续开发者看的说明：本文档记录语音合成工具如何结合上下文选择正确文本。以后此类 plan/spec 文档统一使用中文。

## 目标

修复语音合成工具在上下文场景中的错误行为。

典型问题是：用户说“把刚才的故事用语音读一遍”，系统不应该把“把刚才的故事用语音读一遍”这句话合成为语音，而应该找到上一轮助手真正生成的故事内容，再把故事内容转成语音。

## 总体架构

保留现有工具注册和工具调用流程，只增强 `voice_synthesis` 工具的上下文理解能力。

核心思路：

```text
用户请求
  -> 工具规划器判断是否需要 voice_synthesis
  -> 判断语音来源 source
  -> 如果用户明确给了文本，合成这段文本
  -> 如果用户说“刚才/上一条/这些内容”，读取上一轮助手回复
  -> 调用语音合成服务
  -> 返回语音文件
```

其中 `source` 字段用于告诉语音工具目标文本来自哪里，例如：

- `explicit`：用户明确给出的文本；
- `previous`：上一轮助手回复；
- `context`：根据上下文综合提取的内容。

## 技术栈

- Java 17
- Spring Boot
- JUnit 5
- Mockito
- 现有工具路由框架
- 微信会话上下文记忆
- 语音合成服务

## 任务 1：让规划器和语音工具理解“上一轮回复”

涉及文件：

- `src/main/java/com/example/spring/tool/protocol/ToolCallPlanner.java`
- `src/main/java/com/example/spring/wechat/conversation/tools/VoiceSynthesisWechatTool.java`
- `src/main/java/com/example/spring/wechat/conversation/tools/WechatToolRequest.java`
- `src/test/java/com/example/spring/tool/ToolCallPlannerTests.java`
- `src/test/java/com/example/spring/wechat/conversation/tools/VoiceWechatToolTests.java`

实现步骤：

- [ ] 优化 `ToolCallPlanner` 的提示词，要求模型在用户要求朗读上一轮内容时输出 `source=previous`。
- [ ] 在 `WechatToolRequest` 中保留历史上下文文本。
- [ ] 在 `VoiceSynthesisWechatTool` 中增加目标文本解析逻辑。
- [ ] 编写测试，覆盖“朗读上一轮助手回复”的情况。

推荐测试：

```java
@Test
void plannerMarksVoiceRequestsAsPreviousWhenUserRefersToLastAssistantReply() {
    // 用户说“用语音读一遍刚才的内容”
    // 期望工具计划中 voice_synthesis 的参数包含 source=previous
}
```

推荐验证命令：

```powershell
mvn -q -Dtest=ToolCallPlannerTests,VoiceWechatToolTests test
```

## 任务 2：在会话记忆中保存真实助手文本

涉及文件：

- `src/main/java/com/example/spring/wechat/conversation/WechatConversationService.java`
- `src/test/java/com/example/spring/wechat/conversation/WechatVoiceConversationServiceTests.java`

实现步骤：

- [ ] 语音回复发送后，把对应的 `transcriptText` 写入会话记忆。
- [ ] 如果没有 `transcriptText`，再使用“已发送语音”这类占位描述。
- [ ] 后续用户要求“再读一遍”“读刚才那段”时，优先读取真实文本。

推荐测试：

```java
@Test
void voiceRepliesShouldStoreTranscriptTextInHistory() {
    // 构造一条包含 transcriptText 的语音回复
    // 期望后续会话历史中保存真实文本，而不是简单占位符
}
```

推荐验证命令：

```powershell
mvn -q -Dtest=WechatVoiceConversationServiceTests test
```

## 任务 3：处理长文本语音切分

涉及文件：

- `src/main/java/com/example/spring/wechat/voice/synthesis/service/DefaultVoiceSynthesisService.java`
- `src/main/java/com/example/spring/wechat/bot/WechatBotService.java`
- `src/test/java/com/example/spring/wechat/conversation/WechatVoiceConversationServiceTests.java`
- `src/test/java/com/example/spring/wechat/bot/WechatBotServiceTests.java`

实现步骤：

- [ ] 合成前按长度或预估时长切分文本。
- [ ] 每段语音保留对应的文本片段。
- [ ] 微信端按顺序发送多段语音。
- [ ] 发送失败时增加有限重试。
- [ ] 控制发送间隔，避免连续发送太快导致后面几段失败。

## 任务 4：完整回归

验证命令：

```powershell
mvn -q test
```

检查项：

- [ ] 用户直接要求“用语音介绍你自己”，只发送语音，不额外发送重复文本。
- [ ] 用户要求“生成故事并用语音读一遍”，语音内容应该是生成后的故事。
- [ ] 用户要求“把刚才的内容读一遍”，语音内容应该来自上一轮助手回复。
- [ ] 长文本会被拆成多段语音，且尽量保持自然分段。
- [ ] 多段语音发送失败时有重试，不会静默失败。

## 验收标准

- 语音工具不会把用户命令本身误当成朗读内容；
- 语音工具能优先读取上一轮助手回复；
- 会话记忆里保存真实文本，方便后续继续使用；
- 长文本语音能分段发送；
- 所有相关测试通过。
