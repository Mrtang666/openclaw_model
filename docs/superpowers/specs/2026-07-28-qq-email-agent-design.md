# QQ 邮箱 Agent 工具设计

## 目标

为 OpenClaw 增加邮件发送能力，让微信 Agent 可以通过 QQ 邮箱 SMTP 自动发送邮件。Agent 只有在所有收件人都明确位于白名单中时，才可以直接发送。只要存在任何非白名单收件人，就必须先生成待确认草稿，等用户明确确认后再发送。

## 范围

第一版只支持外发邮件，不支持读取收件箱、搜索邮件、下载附件、查询投递状态、定时发送或管理联系人。

## 用户体验

当用户要求发送邮件时，Agent 需要收集收件人、主题和正文。可选字段包括抄送和密送。如果缺少必要信息，Agent 只追问一个最关键的问题。

如果所有收件人都在配置的白名单中，工具直接发送邮件，并返回简短成功提示，包含脱敏后的收件人列表和主题。

如果存在任何非白名单收件人，工具不直接发送，而是创建待确认草稿并返回确认提示。确认提示包含收件人列表、主题和安全的正文预览。用户必须明确确认后，邮件才会被真正发送。

## 架构

该功能沿用现有微信工具模式：

- `EmailWechatTool`：向 function-calling 循环暴露 `email_send` 工具。
- `EmailService`：负责参数校验、白名单判断、草稿创建和发送编排。
- `SmtpEmailClient`：通过 QQ SMTP 执行真实发信。
- `PendingEmailDraftService`：保存非白名单邮件的短期待确认草稿。
- `EmailProperties`：绑定 `email.*` 配置。

工具通过 Spring 组件扫描自动注册，匹配现有 `WechatToolRegistry` 的用法。

## QQ SMTP 配置

QQ 邮箱 SMTP 主机为 `smtp.qq.com`。登录密码必须使用 QQ 邮箱生成的授权码，不能使用普通 QQ 密码。

推荐默认配置：

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

`EMAIL_ALLOWED_RECIPIENTS` 是逗号分隔的邮箱地址列表。匹配时先去除空白，并忽略大小写。

## 工具契约

工具名：`email_send`

参数：

- `to`：必填，收件人列表，多个地址用逗号分隔。
- `subject`：必填，邮件主题。
- `body`：必填，纯文本邮件正文。
- `cc`：可选，抄送列表，多个地址用逗号分隔。
- `bcc`：可选，密送列表，多个地址用逗号分隔。
- `confirm_token`：可选，之前创建待确认草稿时返回的确认令牌。

第一版只发送纯文本邮件。HTML 邮件和附件暂不支持，以降低伪装、渲染差异和文件外泄风险。

## 发送策略

发送策略保持保守：

- `email.enabled=false` 时，工具拒绝运行。
- SMTP 凭证或发件人地址缺失时，工具拒绝发送。
- 任何 SMTP 调用前都要校验邮箱地址格式。
- 只有当 `to`、`cc`、`bcc` 中的所有收件人都在白名单内，工具才直接发送。
- 只要存在非白名单收件人，工具就创建待确认草稿。
- 只有当 `confirm_token` 匹配同一微信会话下未过期的草稿时，工具才发送该草稿。
- 模型不能通过隐藏参数或额外字段绕过确认流程。

## 确认流程

针对非白名单收件人：

1. `email_send` 收到完整邮件请求。
2. `EmailService` 保存草稿，草稿包含随机确认令牌、会话 key、收件人、主题、正文和过期时间。
3. 工具返回确认提示给用户。
4. 用户确认后，模型再次调用 `email_send`，并带上 `confirm_token`。
5. `EmailService` 读取对应草稿，校验会话和过期时间，发送邮件，然后删除草稿。

草稿第一版可以先放在内存中。如果以后需要支持应用重启后继续确认，再增加 MySQL 持久化。

## Prompt 和能力边界

`EmailWechatTool.capability()` 应该告诉模型：

- 只有在用户明确要求发送或准备邮件时才调用该工具。
- 缺少收件人、主题或正文时必须追问。
- 不要编造收件人或邮箱地址。
- 不要发送凭证、验证码、私钥或其他敏感信息，除非用户明确提供了确切内容和接收方。
- 非白名单收件人必须确认后才能发送。
- 只支持纯文本发信，不支持附件，也不支持读取收件箱。

Agent 系统提示可以额外增加一条全局规则：邮件是具有外部副作用的工具，不确定用户意图时应先澄清，而不是尝试发送。

## 错误处理

用户可见错误需要简短、可操作：

- 未启用：提示“邮箱功能还没有启用”。
- 配置缺失：提示检查发件邮箱和 QQ 邮箱授权码。
- 地址格式错误：指出具体哪个邮箱地址不合法。
- 需要确认：返回待确认草稿摘要，并要求用户确认。
- 令牌过期：要求用户重新创建草稿。
- SMTP 认证失败：明确提示检查 QQ 邮箱授权码，而不是 QQ 密码。
- SMTP 临时失败：提示稍后重试。

日志应尽量对收件人脱敏，并且绝不能完整打印 SMTP 密码、授权码、正文内容或确认令牌。

## 测试

需要覆盖的重点测试：

- `EmailProperties` 默认值和规范化逻辑。
- 白名单匹配支持去空白和忽略大小写。
- 缺少必要字段时返回追问提示。
- 非白名单收件人会创建待确认草稿，并且不会调用 SMTP。
- 有效确认令牌会发送已保存草稿，并删除草稿。
- 错误会话、过期令牌或未知令牌会被拒绝。
- 白名单收件人会直接调用邮件客户端。
- SMTP 异常会转换成安全的用户可见提示。
- 工具定义暴露预期的 function-calling 参数。

单元测试里使用假的 `EmailClient`，避免真的发邮件。真实 QQ SMTP 冒烟测试应当是手动或显式开启的 opt-in 测试，因为它会产生外部邮件。

## 实现备注

在 `pom.xml` 中加入 `spring-boot-starter-mail`，使用 Spring Mail 的 JavaMail 支持。QQ 相关默认值放在配置里，但 `SmtpEmailClient` 保持通用，方便以后切换到其他 SMTP 服务商。

当前项目部分旧源码注释在终端输出里存在编码噪声。新增文件保持 UTF-8，测试应验证行为，不依赖内部注释文本。
