# WeChat `#new` New Conversation Design

## Context

OpenClaw currently keeps WeChat context in `WechatMemoryService`.
For a real WeChat user, `WechatConversationService` derives a session key from the WeChat user id, records the incoming message through `acceptIncoming`, and then loads the user's active conversation from MySQL.

The MySQL memory implementation stores:

- `users`: stable WeChat user identity.
- `conversations`: conversation session records with `ACTIVE` or `CLOSED` status.
- `conversation_messages`: user and assistant message bodies.
- `conversation_states`: mutable tool state such as pending clarification, recent image prompt, file context, and weather city.
- `conversation_summaries`: generated summaries for older or long conversations.
- `user_preferences`: long-lived explicit preferences that should survive conversation changes.
- `tool_execution_logs`: tool execution history.

Today, users get a new conversation only when the previous active conversation is idle long enough for automatic closure. There is no user-facing command to start a fresh conversation immediately.

## Goal

Allow a WeChat user to send exactly `#new` to start a fresh conversation on demand.

The command should close the user's current active conversation and clear short-term in-process context. The next normal message from that user should create and use a new `conversations` row.

## Non-Goals

- Do not delete old conversations or messages.
- Do not clear `user_preferences`.
- Do not support natural-language triggers such as "start over".
- Do not support inline first-message syntax such as `#new plan a trip`.
- Do not change the database schema unless implementation discovers a necessary constraint.

## User-Facing Behavior

Only a trimmed message equal to `#new` triggers the feature.

Examples:

- `#new` triggers a new conversation.
- ` #new ` triggers a new conversation.
- `#NEW` does not trigger.
- `#new plan a report` does not trigger and is treated as normal input.

Recommended confirmation reply:

```text
Started a new conversation.
```

The `#new` command itself should not be recorded as a normal user message in either the old or new conversation.

## Architecture

Add an explicit operation to `WechatMemoryService`, for example:

```java
void startNewConversation(String wechatUserId, Instant now);
```

MySQL implementation:

1. Normalize the user id the same way as existing memory operations.
2. Find the existing user row.
3. If no user exists, do nothing; the next normal message will create the first conversation.
4. Close all current `ACTIVE` conversations for that user and `WECHAT` channel by setting `status = CLOSED` and `closed_at = now`.
5. Prefer generating a summary for the active conversation before closing when possible, but a summary failure should not block the new-conversation command.

In-memory fallback implementation:

1. Remove or replace the current fallback session for that user.
2. Preserve explicit preferences.
3. Preserve accepted message ids used for duplicate-message protection.

`WechatConversationService` should detect `#new` before calling `acceptWechatMessage`, so the command is not persisted as conversation content. After the memory service closes the active conversation, the service should clear volatile per-user caches:

- `memories`
- `pendingVideos`
- `lastVideos`

Image archive records and document archive records should remain in storage. They are durable user resources, not short-term conversation turns. If later behavior needs "resource isolation per conversation", that should be a separate design because it affects image/file lookup semantics across the app.

## Data Flow

For `#new`:

1. `WechatConversationService.handleWechat(...)` receives the message.
2. It computes `sessionKey`.
3. It trims the text and checks equality with `#new`.
4. It calls `wechatMemoryService.startNewConversation(sessionKey, now)`.
5. It removes short-term in-process cache for that `sessionKey`.
6. It returns the confirmation reply.

For the next normal user message:

1. `acceptIncoming(...)` runs normally.
2. `openPersistent(...)` finds no active conversation.
3. `createConversation(...)` creates a new conversation.
4. The message is stored in that new conversation.

## Error Handling

If MySQL is unavailable, `MySqlWechatMemoryService` should fall back to `InMemoryWechatMemoryFallback.startNewConversation(...)`, matching existing fallback behavior for other memory operations.

If summary generation fails while closing the old conversation, log a warning and close the conversation anyway. The user explicitly requested a fresh conversation, so failure to summarize should not trap them in the old active conversation.

If database update fails entirely, keep the app behavior consistent with current memory methods: log a warning, use the fallback implementation, and return the normal confirmation.

## Tests

Add focused tests for:

- `MySqlWechatMemoryService` closes an active conversation and the next `acceptIncoming` creates a new conversation.
- `#new` is detected before persistence, so it does not appear in `conversation_messages`.
- `#new foo` does not trigger the command.
- The in-memory fallback starts a new session while keeping preferences.
- `WechatConversationService` clears its memory cache and returns the confirmation reply.

## Open Decisions

Use exact case-sensitive matching for the first implementation: only `#new` triggers. This keeps behavior predictable and avoids surprising users.
