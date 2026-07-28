# QQ Email Agent Tool Design

## Goal

Add an email capability to OpenClaw so the WeChat agent can send email through QQ Mail SMTP. The agent may send automatically only when all recipients are explicitly whitelisted. Messages to any non-whitelisted recipient must be held as a pending draft until the user confirms the exact send action.

## Scope

This design covers outbound email only. The first version will not read inboxes, search mail, download attachments, poll delivery status, schedule future messages, or manage contact books.

## User Experience

When the user asks the agent to send an email, the agent gathers the recipient, subject, and body. Optional fields are cc and bcc. If required information is missing, the agent asks one focused follow-up question.

If every recipient is in the configured whitelist, the tool sends the email immediately and returns a short success message with the masked recipient list and subject.

If any recipient is not whitelisted, the tool creates a pending draft and returns a confirmation prompt. The prompt includes the recipient list, subject, and a safe body preview. The user must explicitly confirm before the message is sent.

## Architecture

The feature follows the existing WeChat tool pattern:

- `EmailWechatTool` exposes `email_send` to the function-calling loop.
- `EmailService` owns validation, whitelist decisions, draft creation, and send orchestration.
- `SmtpEmailClient` sends mail through QQ SMTP.
- `PendingEmailDraftService` stores short-lived drafts for non-whitelist confirmation.
- `EmailProperties` binds configuration under the `email.*` prefix.

The tool is registered automatically through Spring component scanning, matching the existing `WechatToolRegistry` pattern.

## QQ SMTP Configuration

QQ Mail uses SMTP host `smtp.qq.com`. The login password must be a QQ Mail authorization code, not the normal QQ account password.

Recommended defaults:

```properties
email.enabled=${EMAIL_ENABLED:false}
email.provider=${EMAIL_PROVIDER:qq}
email.smtp.host=${EMAIL_SMTP_HOST:smtp.qq.com}
email.smtp.port=${EMAIL_SMTP_PORT:465}
email.smtp.ssl-enabled=${EMAIL_SMTP_SSL_ENABLED:true}
email.smtp.username=${EMAIL_SMTP_USERNAME:}
email.smtp.password=${EMAIL_SMTP_PASSWORD:}
email.smtp.from=${EMAIL_FROM:${EMAIL_SMTP_USERNAME:}}
email.smtp.timeout-ms=${EMAIL_SMTP_TIMEOUT_MS:15000}
email.allowed-recipients=${EMAIL_ALLOWED_RECIPIENTS:}
email.require-confirmation-for-non-whitelist=${EMAIL_REQUIRE_CONFIRMATION_FOR_NON_WHITELIST:true}
email.pending-draft-ttl-minutes=${EMAIL_PENDING_DRAFT_TTL_MINUTES:10}
email.max-body-chars=${EMAIL_MAX_BODY_CHARS:8000}
```

`EMAIL_ALLOWED_RECIPIENTS` is a comma-separated list of email addresses. Matching is case-insensitive after trimming whitespace.

## Tool Contract

Tool name: `email_send`

Parameters:

- `to`: required comma-separated recipient list.
- `subject`: required email subject.
- `body`: required plain text email body.
- `cc`: optional comma-separated cc list.
- `bcc`: optional comma-separated bcc list.
- `confirm_token`: optional token returned by a previous pending draft.

The first version sends plain text email only. HTML and attachments remain out of scope to reduce spoofing, rendering, and file exfiltration risk.

## Send Policy

The send policy is deliberately conservative:

- The tool refuses to run when `email.enabled=false`.
- The tool refuses to send if SMTP credentials or sender address are missing.
- The tool validates email address shape before any SMTP call.
- The tool sends immediately only when all `to`, `cc`, and `bcc` recipients are whitelisted.
- The tool creates a pending draft when any recipient is not whitelisted.
- The tool sends a pending draft only when `confirm_token` matches an unexpired draft for the same WeChat session.
- The tool does not allow the model to bypass confirmation by setting hidden flags.

## Confirmation Flow

For non-whitelisted recipients:

1. `email_send` receives a complete email request.
2. `EmailService` stores a draft with a random confirmation token, session key, recipients, subject, body, and expiry.
3. The tool returns a confirmation prompt to the user.
4. If the user confirms, the model calls `email_send` again with `confirm_token`.
5. `EmailService` loads the matching draft, verifies the session and expiry, sends it, and deletes the draft.

Drafts can initially be stored in memory. A later version may add MySQL persistence if restart-safe confirmations become necessary.

## Prompt And Capability Guidance

`EmailWechatTool.capability()` should tell the model:

- Use the tool only when the user explicitly asks to send or prepare an email.
- Ask for missing recipient, subject, or body.
- Do not invent recipients or email addresses.
- Do not send credentials, verification codes, private keys, or secrets unless the user explicitly provides the exact content and recipient.
- Non-whitelisted recipients require confirmation.
- Plain text email is supported; attachments and inbox reading are not supported.

The agent system prompt can optionally add one global rule: email is an external side-effect tool, so uncertain intent should become a clarification question, not a send attempt.

## Error Handling

User-facing failures should be short and actionable. The final implementation can localize these messages to Chinese following the existing WeChat reply style:

- Disabled: "Email sending is not enabled."
- Missing config: "Email SMTP configuration is incomplete. Check the sender address and QQ authorization code."
- Invalid address: "Invalid email address: ..."
- Confirmation needed: return pending draft summary and ask the user to confirm.
- Expired token: ask the user to recreate the draft.
- SMTP auth failure: mention QQ authorization code rather than account password.
- SMTP transient failure: ask the user to retry later.

Logs must mask recipients where practical and must never print SMTP passwords, authorization codes, body contents, or confirmation tokens in full.

## Testing

Focused tests should cover:

- `EmailProperties` default values and normalization.
- Whitelist matching with trimming and case-insensitive addresses.
- Missing required fields return clarification text.
- Non-whitelist recipients create a pending draft and do not call SMTP.
- Valid confirmation token sends the stored draft and removes it.
- Wrong session, expired token, or unknown token is rejected.
- Whitelisted recipients call the email client directly.
- SMTP exceptions become safe user-facing messages.
- Tool definition exposes the expected function-calling parameters.

SMTP integration can be tested with a fake `EmailClient` in unit tests. A real QQ SMTP smoke test should be manual or opt-in because it sends external email.

## Implementation Notes

Add `spring-boot-starter-mail` to `pom.xml` and use JavaMail through Spring's mail support. Keep the QQ-specific defaults in configuration, but keep the client generic enough that another SMTP provider can be configured later without changing the tool contract.

The current project has some source comments displayed with encoding artifacts in terminal output. New files should remain UTF-8, and tests should assert behavior rather than depend on exact internal comments.
