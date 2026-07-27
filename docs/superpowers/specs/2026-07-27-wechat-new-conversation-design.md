# 微信 `#new` 开启新对话设计

## 背景

OpenClaw 当前通过 `WechatMemoryService` 管理微信端上下文记忆。
对于真实微信用户，`WechatConversationService` 会从微信用户 ID 得到 session key，先通过 `acceptIncoming` 记录用户消息，再从 MySQL 加载该用户的活跃会话。

MySQL 记忆模块主要使用这些表：

- `users`：稳定的微信用户身份。
- `conversations`：会话记录，状态包括 `ACTIVE` 和 `CLOSED`。
- `conversation_messages`：用户和助手的消息正文。
- `conversation_states`：可变工具状态，例如待追问问题、最近图片提示词、文件上下文、天气城市等。
- `conversation_summaries`：较早会话或长会话的压缩摘要。
- `user_preferences`：用户长期显式偏好，切换对话时不应清除。
- `tool_execution_logs`：工具调用历史。

现在用户只有在旧会话空闲超过配置时间后，系统才会自动关闭旧会话并创建新会话。用户还不能主动要求“从这里开始一段新的对话”。

## 目标

允许微信用户单独发送 `#new`，随时开启一段新的对话。

该命令需要关闭当前用户的活跃会话，并清理进程内的短期上下文缓存。用户下一条普通消息应该自动创建并进入新的 `conversations` 记录。

## 非目标

- 不删除旧会话和旧消息。
- 不清除 `user_preferences`。
- 不支持“重新开始”“新对话”等自然语言触发。
- 不支持 `#new 帮我规划旅行` 这种命令后直接跟首条消息的写法。
- 除非实现时发现必要约束，否则不改数据库表结构。

## 用户侧行为

只有去掉首尾空白后，文本严格等于 `#new` 时才触发该功能。

示例：

- `#new`：触发新对话。
- ` #new `：触发新对话。
- `#NEW`：不触发。
- `#new 帮我写日报`：不触发，按普通消息处理。

推荐确认回复：

```text
已开启新的对话。
```

`#new` 命令本身不应作为普通用户消息写入旧会话或新会话。

## 架构设计

在 `WechatMemoryService` 增加一个显式操作，例如：

```java
void startNewConversation(String wechatUserId, Instant now);
```

MySQL 实现：

1. 按现有记忆方法一致的规则规范化用户 ID。
2. 查找已有 `users` 记录。
3. 如果用户不存在，不做额外操作；下一条普通消息会自然创建用户和第一段会话。
4. 将该用户在 `WECHAT` channel 下当前所有 `ACTIVE` 会话更新为 `CLOSED`，并写入 `closed_at = now`。
5. 优先尝试在关闭旧会话前生成摘要；但摘要失败不能阻塞 `#new` 命令。

进程内 fallback 实现：

1. 移除或替换当前用户的 fallback session。
2. 保留显式用户偏好。
3. 保留用于重复消息保护的 message id 集合。

`WechatConversationService` 应该在调用 `acceptWechatMessage` 之前识别 `#new`。这样该命令不会被持久化为普通对话内容。

关闭活跃会话后，`WechatConversationService` 还应该清理该用户的短期进程缓存：

- `memories`
- `pendingVideos`
- `lastVideos`

图片归档和文档归档记录不在本次功能里清除。它们属于用户已经上传或生成的持久资源，不是短期对话轮次。未来如果需要“资源也按会话隔离”，应该另起一个设计，因为这会影响图片和文件在整个应用中的查找语义。

## 数据流

当用户发送 `#new`：

1. `WechatConversationService.handleWechat(...)` 收到消息。
2. 计算当前用户的 `sessionKey`。
3. 读取消息文本，trim 后判断是否严格等于 `#new`。
4. 调用 `wechatMemoryService.startNewConversation(sessionKey, now)`。
5. 移除该 `sessionKey` 对应的短期进程缓存。
6. 返回确认回复。

当用户随后发送下一条普通消息：

1. `acceptIncoming(...)` 正常执行。
2. `openPersistent(...)` 找不到活跃会话。
3. `createConversation(...)` 创建新的会话。
4. 该消息写入新会话。

## 错误处理

如果 MySQL 不可用，`MySqlWechatMemoryService` 应该和现有其他记忆操作保持一致，回退到 `InMemoryWechatMemoryFallback.startNewConversation(...)`。

如果关闭旧会话前生成摘要失败，只记录 warning，然后继续关闭旧会话。用户已经明确要求开启新对话，摘要失败不应该把用户困在旧活跃会话里。

如果数据库更新整体失败，行为也应和现有记忆方法一致：记录 warning，使用 fallback 实现，并返回正常确认回复。

## 测试范围

需要补充聚焦测试：

- `MySqlWechatMemoryService` 能关闭活跃会话，并让下一次 `acceptIncoming` 创建新会话。
- `#new` 在持久化前被识别，因此不会出现在 `conversation_messages`。
- `#new foo` 不触发新对话命令。
- 进程内 fallback 能开启新 session，同时保留用户偏好。
- `WechatConversationService` 会清理内存缓存，并返回确认回复。

## 已确定决策

第一版使用严格、大小写敏感的精确匹配：只有 `#new` 会触发。这样行为最可预测，也能避免误触发。
