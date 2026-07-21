# 意图识别升级与源码注释实现计划

> 给后续开发者看的说明：本文档记录“更精确的意图识别”和“生产代码注释整理”的实现思路。以后此类计划文档统一使用中文。

## 目标

优化微信端的意图识别能力，让系统不再只靠简单关键词判断，而是结合上下文、大模型结构化输出和保守规则共同判断用户要做什么。

同时，为 `src/main/java` 下的生产代码补充简短注释，让每个类和关键方法的职责更容易看懂。测试文件不需要加注释。

## 总体架构

当前系统已经有工具中心和微信会话流程，本次优化不推翻原结构，而是在工具执行之前加入一层更清晰的“意图决策层”。

推荐顺序：

```text
用户消息
  -> 简单预处理
  -> 检查是否存在等待确认的状态
  -> 调用大模型输出结构化 JSON 任务计划
  -> 解析工具调用计划
  -> 如果置信度不足，进行追问
  -> 如果结构化结果不可用，进入保守规则兜底
  -> 调用对应工具
  -> 汇总回复
```

这样做的重点是：先理解用户需求，再决定调用哪个工具，而不是在代码里按关键词顺序一路判断。

## 技术栈

- Java 17
- Spring Boot
- Maven
- JUnit 5
- Mockito
- 阿里百炼兼容模式大模型接口
- 现有微信工具注册中心

## 任务 1：增加上下文感知的意图决策层

涉及文件：

- `src/main/java/com/example/spring/wechat/conversation/intent/ConversationIntentDecision.java`
- `src/main/java/com/example/spring/wechat/conversation/intent/ConversationIntentType.java`
- `src/main/java/com/example/spring/wechat/conversation/intent/ConversationIntentPlanner.java`
- `src/main/java/com/example/spring/wechat/conversation/intent/DefaultConversationIntentPlanner.java`
- `src/main/java/com/example/spring/tool/protocol/ToolCallPlanner.java`
- `src/main/java/com/example/spring/tool/protocol/ToolCallPlanParser.java`
- `src/test/java/com/example/spring/tool/ToolCallPlannerTests.java`
- `src/test/java/com/example/spring/wechat/conversation/WechatConversationServiceTests.java`

实现步骤：

- [ ] 增加意图决策模型，用于描述意图类型、置信度、是否需要追问、追问问题、任务列表。
- [ ] 优化 `ToolCallPlanner` 的提示词，要求大模型输出稳定 JSON。
- [ ] 在解析失败或置信度过低时，不直接乱调工具，而是给用户追问。
- [ ] 编写测试覆盖模糊需求和上下文依赖消息。

重点测试场景：

```java
// 用户说“帮我查天气”，但没有城市名，应追问城市。
// 用户说“可以，偏好美食”，应结合上下文继续对话。
// 大模型输出低置信度任务时，应进入追问，而不是直接执行。
```

推荐验证命令：

```powershell
mvn -q -Dtest=WechatConversationServiceTests,ToolCallPlannerTests test
```

## 任务 2：把意图决策接入微信会话主流程

涉及文件：

- `src/main/java/com/example/spring/wechat/conversation/WechatConversationService.java`
- `src/main/java/com/example/spring/wechat/conversation/intent/WeatherIntentParser.java`
- `src/main/java/com/example/spring/wechat/image/generation/intent/ImageGenerationIntentParser.java`

实现步骤：

- [ ] 先处理明确的等待状态，例如图片生成确认、图片修改确认。
- [ ] 把最近几轮会话历史传给结构化意图规划器。
- [ ] 天气缺城市、图片生成缺提示词、语音合成缺目标文本时，优先追问。
- [ ] 对明确需求调用对应工具，对普通聊天进入大模型对话。

关键规则：

```text
优先级一：等待用户确认的状态。
优先级二：大模型结构化任务计划。
优先级三：保守规则补充。
优先级四：普通对话。
```

推荐验证命令：

```powershell
mvn -q -Dtest=WechatConversationServiceTests,ToolCallPlannerTests test
```

## 任务 3：给生产代码补充简短注释

范围：

- 包含：`src/main/java/com/example/spring/**`
- 排除：`src/test/java/**`

实现要求：

- [ ] 每个生产类增加简短类注释，说明它负责什么。
- [ ] 对关键方法和复杂分支增加注释。
- [ ] 不给简单 getter/setter 写废话注释。
- [ ] 注释只解释功能和作用，不写太长。
- [ ] 注释内容使用中文。

重点位置：

- 工具路由；
- 意图解析；
- 会话记忆更新；
- 外部 API 调用；
- 微信消息收发；
- 图片、语音、天气工具执行；
- 异常处理。

## 任务 4：最终检查和清理

涉及文件：

- `docs/superpowers/plans/2026-07-20-intent-recognition-and-comments.md`
- 本次改动涉及的所有生产代码文件

检查项：

- [ ] 新增行为有测试覆盖。
- [ ] 没有遗留无意义的 `TODO`。
- [ ] 没有含糊的占位文字。
- [ ] 注释没有解释显而易见的代码。
- [ ] 完整测试通过。

推荐验证命令：

```powershell
mvn -q test
```

## 验收标准

- 用户模糊提问时，系统会追问，而不是沉默或乱调用工具；
- 用户上下文追问时，系统能继续理解上一轮内容；
- 多工具需求能通过结构化任务计划拆解；
- 生产代码具备基础中文注释；
- 测试通过。
