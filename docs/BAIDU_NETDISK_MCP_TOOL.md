# 百度网盘 MCP 工具封存与后期拓展说明

## 1. 当前状态

百度网盘能力已经作为一个独立工具模块保留在项目中，但当前默认关闭，不参与微信端 Function Calling 工具注册，也不暴露 OAuth 回调入口。

这样做的目的：

- 保留已经写好的代码、数据库脚本、配置项和工具接口。
- 不影响当前微信 Agent 的天气、图片、语音、文件、知识库、网页搜索等主流程。
- 后期如果继续做百度网盘，只需要重新打开配置并补齐百度开放平台侧的授权信息。

当前默认关闭开关：

```env
BAIDU_NETDISK_ENABLED=false
```

对应 Spring 配置：

```properties
baidu.netdisk.enabled=${BAIDU_NETDISK_ENABLED:false}
```

## 2. 功能边界

当前封存的是“百度网盘工具模块”，不是删除功能。

已经保留的能力设计包括：

- 绑定当前微信用户自己的百度网盘账号。
- 保存用户授权 token 到数据库。
- 使用 token 调用百度网盘 MCP。
- 搜索网盘文件。
- 列出网盘目录。
- 分享网盘文件。
- 将 AI 生成的文本内容保存为 Markdown 文件并上传到网盘。
- 为后续“授权完成后继续执行未完成任务”预留 pending action 数据表。

当前不会启用的内容：

- 微信端不会把 `netdisk_auth`、`netdisk_search`、`netdisk_list`、`netdisk_save`、`netdisk_share` 注册给大模型。
- `/api/netdisk/baidu/callback` 回调接口默认不会注册。
- 用户在微信端提出网盘需求时，主流程不会自动调用百度网盘工具。

## 3. 文件结构说明

### 3.1 微信工具入口

路径：

```text
src/main/java/com/example/spring/wechat/conversation/tools/
```

文件：

- `NetdiskAuthWechatTool.java`：百度网盘授权工具，负责绑定、查看状态、重新绑定。
- `NetdiskSearchWechatTool.java`：网盘文件搜索工具。
- `NetdiskListWechatTool.java`：网盘目录列表工具。
- `NetdiskSaveWechatTool.java`：把 AI 生成内容保存到网盘的工具。
- `NetdiskShareWechatTool.java`：生成网盘文件分享链接的工具。

这些类都加了条件注册：

```java
@ConditionalOnProperty(prefix = "baidu.netdisk", name = "enabled", havingValue = "true")
```

所以只有 `BAIDU_NETDISK_ENABLED=true` 时才会被注册进工具中心。

### 3.2 百度网盘业务模块

路径：

```text
src/main/java/com/example/spring/wechat/netdisk/
```

主要子包：

- `auth`：OAuth 授权流程、token 加密、授权回调入口。
- `client`：百度 OAuth HTTP 客户端、百度网盘 MCP 客户端适配。
- `config`：`BaiduNetdiskProperties` 配置映射。
- `context`：网盘文件上下文快照，方便后续支持“刚才那个文件”等引用。
- `exception`：网盘工具统一异常。
- `export`：把 AI 内容导出成文件的服务。
- `model`：网盘授权、token、文件、分享结果等数据模型。
- `repository`：MySQL 持久化接口和实现。
- `service`：网盘工具业务编排服务。

### 3.3 数据库脚本

路径：

```text
src/main/resources/db/migration/V10__create_baidu_netdisk_tables.sql
```

保留的数据表：

- `user_netdisk_authorizations`：保存微信用户和百度网盘授权之间的绑定关系。
- `netdisk_auth_states`：保存 OAuth state，用于防止授权回调被伪造或串号。
- `netdisk_pending_actions`：保存授权前未完成的任务，后续可用于授权完成后自动继续执行。
- `netdisk_operation_logs`：预留网盘操作日志表。

注意：Flyway 会继续创建这些表。它们只是数据结构，不会影响当前主流程。

## 4. 配置项说明

配置模板位于：

```text
.env.example
src/main/resources/application.properties
```

后续恢复时需要关注这些变量：

```env
BAIDU_NETDISK_ENABLED=true
BAIDU_NETDISK_APP_ID=
BAIDU_NETDISK_APP_KEY=
BAIDU_NETDISK_OAUTH_CLIENT_ID=
BAIDU_NETDISK_SECRET_KEY=
BAIDU_NETDISK_SIGN_KEY=
BAIDU_NETDISK_REDIRECT_URI=http://127.0.0.1:8080/api/netdisk/baidu/callback
BAIDU_NETDISK_AUTH_BASE_URL=https://openapi.baidu.com/oauth/2.0/authorize
BAIDU_NETDISK_TOKEN_URL=https://openapi.baidu.com/oauth/2.0/token
BAIDU_NETDISK_MCP_SSE_BASE_URL=https://mcp-pan.baidu.com/sse
BAIDU_NETDISK_TOKEN_ENCRYPTION_KEY=
BAIDU_NETDISK_AUTH_STATE_TTL_MINUTES=10
BAIDU_NETDISK_PENDING_ACTION_TTL_MINUTES=30
BAIDU_NETDISK_MCP_TIMEOUT_MS=20000
BAIDU_NETDISK_CONTEXT_LIMIT=5
BAIDU_NETDISK_DEFAULT_UPLOAD_PATH=/OpenClaw/
```

重点说明：

- `BAIDU_NETDISK_ENABLED`：总开关。当前应保持 `false`。
- `BAIDU_NETDISK_APP_KEY`：百度开放平台应用的 AppKey。
- `BAIDU_NETDISK_OAUTH_CLIENT_ID`：OAuth 请求中的 `client_id`。按照百度网盘开放平台授权码模式，通常应使用 AppKey，而不是数字 AppID。
- `BAIDU_NETDISK_SECRET_KEY`：OAuth 换 token 时使用的密钥。
- `BAIDU_NETDISK_REDIRECT_URI`：百度授权完成后回调到本项目的地址，必须和百度开放平台控制台中配置的一致。
- `BAIDU_NETDISK_TOKEN_ENCRYPTION_KEY`：用于加密保存用户 token，建议使用足够长的随机字符串。

## 5. 后期恢复步骤

如果后面要继续开发百度网盘工具，可以按下面顺序恢复。

### 第一步：确认百度开放平台应用

进入百度网盘开放平台，确认：

- 应用已经创建完成。
- 应用类型和回调地址符合当前使用方式。
- 已开通需要的网盘能力或 MCP 能力。
- AppKey、SecretKey 可用。
- 回调地址填写为项目实际可访问地址。

官方文档入口：

- [百度网盘开放平台使用概述](https://pan.baidu.com/union/doc/%E4%BD%BF%E7%94%A8%E5%85%A5%E9%97%A8/%E4%BD%BF%E7%94%A8%E6%A6%82%E8%BF%B0/)

### 第二步：打开本地配置

在本机 `.env` 中改成：

```env
BAIDU_NETDISK_ENABLED=true
```

并填好：

```env
BAIDU_NETDISK_APP_KEY=你的AppKey
BAIDU_NETDISK_OAUTH_CLIENT_ID=你的AppKey
BAIDU_NETDISK_SECRET_KEY=你的SecretKey
BAIDU_NETDISK_REDIRECT_URI=http://127.0.0.1:8080/api/netdisk/baidu/callback
BAIDU_NETDISK_TOKEN_ENCRYPTION_KEY=一段足够长的随机密钥
```

如果只支持电脑端本地回调，回调地址可以先使用 `127.0.0.1:8080`。如果要支持手机端点击授权链接，需要公网可访问域名或稳定内网穿透地址。

### 第三步：重启项目

启动项目后确认日志中没有网盘配置错误。

如果项目启动成功，说明网盘 Bean 已经正常注册。

### 第四步：微信端触发授权

在微信端发送类似：

```text
绑定百度网盘
```

大模型应该通过 Function Calling 调用：

```text
netdisk_auth
```

然后返回百度授权链接。

### 第五步：完成授权回调

用户点击授权链接并完成登录后，百度会回调：

```text
/api/netdisk/baidu/callback
```

项目拿到 `code` 和 `state` 后，会换取 token 并写入数据库。

### 第六步：测试网盘工具

可以在微信端测试：

```text
帮我搜索网盘里的项目文档
列一下网盘根目录
把刚才总结的内容保存到网盘
分享刚才找到的文件
```

## 6. 当前遗留问题

本次暂停前主要卡点是百度 OAuth 授权可用性。

已经根据百度文档确认过的要点：

- 授权码模式中 `client_id` 应使用应用 AppKey。
- `client_secret` 应使用 SecretKey。
- `scope` 需要包含 `basic,netdisk`。
- 普通软件应用一般不需要传 `device_id`，硬件应用才需要关注 AppID/device_id。

但之前直接请求百度 OAuth 时，百度返回过：

```text
invalid_client
```

这个错误更像是百度开放平台侧应用状态、AppKey/SecretKey、应用权限、回调域名或应用类型配置不匹配导致的。后续继续开发前，建议先用最小授权链接在浏览器中验证 OAuth 能否正常打开授权页。

## 7. 后续推荐优化

后期继续做时，建议按这个优先级推进：

1. 先做 OAuth 自检工具。
   - 检查 AppKey、SecretKey、redirect_uri、scope 是否完整。
   - 生成一条可复制的授权测试链接。
   - 对 `invalid_client`、`redirect_uri_mismatch` 等错误给出明确解释。

2. 再做授权完成后自动继续任务。
   - 用户未授权时保存 pending action。
   - 授权成功后后台继续执行用户刚才的网盘任务。
   - 执行完成后主动发微信消息。

3. 完善文件保存格式。
   - 纯文本默认保存为 Markdown。
   - 用户要求 Word 时导出 docx。
   - 用户要求 PDF 时导出 pdf。
   - 如果内容含图片，优先打包成 zip 或生成带图片的文档。

4. 增加网盘上下文引用。
   - 支持“刚才那个文件”“第一个搜索结果”“上次保存的文档”。
   - 更换网盘账号后清空当前用户的网盘短期上下文。

5. 增加更完整的操作日志。
   - 记录工具名、用户、参数摘要、结果摘要、耗时、错误原因。
   - 避免记录 access token、refresh token 等敏感内容。

## 8. 验证命令

后续恢复或修改网盘模块后，建议至少运行：

```powershell
mvn -q "-Dtest=BaiduNetdiskPropertiesTests,NetdiskTokenCryptoServiceTests,BaiduNetdiskAuthServiceTests,BaiduNetdiskOAuthClientTests,BaiduNetdiskAuthControllerTests,BaiduNetdiskMcpClientTests,NetdiskWechatToolTests,ApplicationContextTests" test
```

如果时间允许，再运行完整测试：

```powershell
mvn -q test
```

## 9. 给协作者的简短说明

如果你只是运行当前项目，不需要百度网盘能力：

- 保持 `.env` 里的 `BAIDU_NETDISK_ENABLED=false`。
- 不需要填写百度网盘 AppKey、SecretKey。
- 不需要配置百度 OAuth 回调地址。

如果你要继续开发百度网盘能力：

- 先阅读本文档。
- 再阅读 `docs/superpowers/plans/2026-07-24-baidu-netdisk-mcp-tool.md`。
- 确认百度开放平台 OAuth 可以跑通后，再打开 `BAIDU_NETDISK_ENABLED=true`。
- 不要把真实 `.env`、AppKey、SecretKey、token 加密密钥提交到仓库。
