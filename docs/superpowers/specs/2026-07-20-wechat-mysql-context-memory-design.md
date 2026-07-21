# 微信端 MySQL 上下文记忆设计

## 1. 目标与范围

本方案只为微信端增加数据库上下文记忆，不修改 CLI 的上下文逻辑，也不改变现有 Agent 的工具调用主流程。

目标：

- 微信用户的对话记录、会话状态和明确偏好在项目重启后仍可恢复。
- 同一用户超过 60 分钟没有新消息时自动创建新会话。
- 原始消息和工具调用日志保留 30 天；过期后删除原文，保留会话摘要和用户明确偏好。
- 数据库异常时，微信机器人继续使用进程内存临时回复，不因持久化失败完全停止服务。

## 2. 技术方案

```text
MySQL + MyBatis-Plus + Flyway
```

- MySQL 保存正式数据。
- MyBatis-Plus 负责基础 CRUD；复杂查询使用 Mapper XML。
- Flyway 用版本化 SQL 脚本创建和升级表结构。
- Spring Scheduler 负责超时会话摘要、长会话滚动摘要和 30 天清理。

## 3. 数据模型

### users

保存微信用户身份。

```text
id
wechat_user_id（唯一）
created_at
updated_at
```

### conversations

保存一段连续会话。

```text
id
user_id
channel（固定为 WECHAT）
status（ACTIVE / CLOSED）
started_at
last_active_at
closed_at
```

### conversation_messages

保存用户、助手和工具产生的消息。

```text
id
conversation_id
source_message_id（微信消息 ID，用于幂等）
role（USER / ASSISTANT / TOOL）
content
content_type（TEXT / IMAGE / VOICE / TOOL）
created_at
expires_at
```

### conversation_states

每个会话一条状态记录，使用 JSON 保存变化快的工具状态。

```text
conversation_id（唯一）
state_json
version
updated_at
```

状态包括待追问问题、图片待确认提示词、音色候选与试听信息、最近天气城市等。

### user_preferences

保存跨会话长期生效的明确偏好。

```text
id
user_id
preference_key
preference_value_json
source（EXPLICIT_ACTION / EXPLICIT_STATEMENT）
updated_at
```

不保存模型从普通聊天中推测出的偏好。

### conversation_summaries

保存会话摘要与滚动摘要。

```text
id
conversation_id
summary_text
covered_message_id
generated_at
```

### tool_execution_logs

保存工具调用的参数摘要、结果摘要和执行状态，供排查与审计使用。

```text
id
conversation_id
tool_name
arguments_json
result_summary
status
created_at
expires_at
```

## 4. 上下文读取与写入流程

```text
微信消息
  -> 按 fromUserId 查找或创建用户
  -> 查找最近 60 分钟内的 ACTIVE 会话
  -> 没有则创建新会话
  -> 读取最近 6-10 轮消息、会话摘要、JSON 状态和用户偏好
  -> 交给现有会话编排与工具调用流程
  -> 保存用户消息、助手回复、工具状态和明确偏好
```

## 5. 会话摘要与保留策略

- 会话超过 60 分钟未活跃时关闭并生成一次摘要。
- 单个会话超过 20 轮时生成滚动摘要，避免后续提示词过长。
- 每日定时清理超过 30 天的原始消息和工具调用日志。
- 清理后保留会话摘要和用户明确偏好。

## 6. 故障兜底

- MySQL 正常时，数据库是上下文的唯一正式来源。
- MySQL 临时不可用时，记录错误日志并使用进程内存临时保存当前会话。
- 兜底内存只保证当前进程可用；进程重启后该部分临时记录会丢失。
- 数据库恢复后，新消息继续正常持久化。

## 7. 代码边界

新增包：

```text
wechat/memory/
  model/
  mapper/
  service/
  scheduler/
  fallback/
```

`WechatConversationService` 不再直接维护 `ConcurrentHashMap` 作为正式存储，而是依赖一个上下文记忆接口。MySQL 实现负责正式读写，内存实现只承担故障兜底。

## 8. 测试要求

- 用户与会话创建、60 分钟会话切换。
- 最近消息、摘要、状态、偏好组合读取。
- 用户确认音色与常用城市的长期偏好保存。
- 30 天原始消息与日志清理，摘要和偏好保留。
- MySQL 异常时内存兜底，恢复后新消息正常写入。
- 同一微信消息重复投递时不重复写入。

