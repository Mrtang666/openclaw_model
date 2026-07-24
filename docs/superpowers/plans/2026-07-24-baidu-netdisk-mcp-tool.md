# 百度网盘 MCP 工具接入实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. 本计划使用 checkbox（`- [ ]`）跟踪任务进度。当前项目由用户自行提交 Git，执行本计划时不要自动 commit。

**Goal:** 在现有微信端 Function Calling 工具体系中新增“百度网盘工具”，支持每个微信用户绑定自己的百度网盘账号，并基于用户授权调用百度网盘 MCP 完成搜索、列目录、分享、上传/下载等能力。

**Architecture:** 保持现有 `WechatTool` + `WechatToolRegistry` + `FunctionCallingAgentLoop` 主流程不变。新增 `wechat/netdisk` 独立模块，负责 OAuth 授权、token 加密保存、MCP 客户端适配、网盘业务服务和操作日志；微信端只通过网盘工具调用该模块。

**Tech Stack:** Java 17、Spring Boot 3.4.7、MySQL、Flyway、RestClient、百度网盘 MCP、百度网盘 OAuth、现有 Function Calling 工具协议。

---

## 一、功能范围

### 第一版必须完成

- 用户绑定百度网盘账号。
- 用户查看绑定状态。
- 用户解绑百度网盘。
- 用户更换/重新绑定百度网盘。
- 加密保存每个微信用户自己的 `access_token` / `refresh_token`。
- token 过期前自动刷新。
- 网盘搜索文件。
- 网盘列目录。
- 网盘生成分享链接。
- 上传用户文件到百度网盘。
- 保存 AI 助手生成的内容到百度网盘。
- 未授权用户触发网盘操作时，先发授权链接；授权完成后自动恢复并执行未完成任务。
- 每次网盘工具调用都只能使用当前微信用户自己的授权。
- 用户未授权时，工具返回授权链接，而不是静默失败。
- 用户更换网盘后，清空旧网盘短期上下文，避免跨账号复用文件。

### 第二版建议完成

- 下载百度网盘文件到本地临时目录。
- 将网盘文档加入知识库。
- 网盘文件总结。
- 批量处理多个网盘文件。

第一版先不做批量同步整个网盘，不默认把用户网盘文件加入知识库。

---

## 二、核心交互流程

### 1. 绑定流程

```text
用户：绑定百度网盘
  ↓
netdisk_auth 工具检查用户是否已绑定
  ↓
如果未绑定，创建 auth_state
  ↓
返回百度授权链接
  ↓
用户打开链接登录百度账号并授权
  ↓
百度回调 /api/netdisk/baidu/callback
  ↓
后端校验 state
  ↓
用 code 换取 access_token / refresh_token
  ↓
token 加密保存到 MySQL
  ↓
回复用户：绑定成功
```

### 2. 已授权用户调用网盘工具

```text
用户：帮我找一下网盘里的项目文档
  ↓
FunctionCallingAgentLoop 判断调用 netdisk_search
  ↓
netdisk_search 根据微信 userId 读取用户授权
  ↓
如果 token 过期，先 refresh
  ↓
构造用户级 MCP 请求
  ↓
调用百度网盘 MCP search 工具
  ↓
返回文件列表
  ↓
结果回传大模型
  ↓
微信回复用户
```

### 3. 更换网盘流程

```text
用户：我要更换百度网盘
  ↓
netdisk_auth 判断为 rebind
  ↓
如果当前已绑定，提示二次确认
  ↓
用户：确认更换网盘
  ↓
旧授权标记为 REPLACED 或删除 token
  ↓
清空该用户网盘短期上下文
  ↓
生成新的授权链接
  ↓
新账号授权成功后保存新 token
```

更换网盘不会删除百度网盘里的真实文件，也不会默认删除之前已经进入知识库的资料。

### 4. 未授权时自动恢复未完成任务

```text
用户：帮我把刚才生成的 MySQL Java 接入流程存到网盘里
  ↓
FunctionCallingAgentLoop 判断调用 netdisk_save
  ↓
netdisk_save 检查当前微信用户是否已授权百度网盘
  ↓
如果没有授权：
      1. 把用户原始需求、待保存资源、目标格式、目标路径保存为 pending action
      2. 生成百度网盘授权链接
      3. 微信端回复用户先授权
  ↓
用户完成百度授权
  ↓
授权 callback 保存 token
  ↓
系统读取 pending action
  ↓
自动继续执行 netdisk_save
  ↓
把内容生成文件并上传到用户自己的百度网盘
  ↓
微信端通知用户保存完成
```

授权 callback 不应长时间阻塞 HTTP 请求。推荐 callback 保存 token 后把 pending action 放入后台队列，页面返回“授权成功，正在继续处理你的网盘任务”，后台执行完成后通过微信发送结果。

### 5. 保存 AI 生成内容到网盘

```text
用户：把刚才你总结的 MySQL Java 接入流程存到网盘
  ↓
netdisk_save 解析保存对象
  ↓
对象是上一轮助手文本回答
  ↓
用户没有指定格式
  ↓
默认生成 Markdown 文件
  ↓
上传到百度网盘 /OpenClaw/
```

如果用户明确说：

```text
保存成 PDF
保存成 Word
保存成 txt
```

则优先按用户指定格式生成文件。

### 6. 不同内容类型的默认保存策略

| 用户想保存的对象 | 用户未指定格式时默认策略 | 用户指定格式时 |
| --- | --- | --- |
| 纯文本回答 | 生成 `.md` 文件 | 可生成 `.md`、`.txt`、`.pdf`、`.docx` |
| 网页搜索结果 | 生成 `.md` 文件，保留来源链接 | 可生成 `.pdf`、`.docx` |
| 网页阅读总结 | 生成 `.md` 文件，保留原网页 URL | 可生成 `.pdf`、`.docx` |
| AI 生成图片 | 上传原始图片文件 | 可额外生成说明 `.md` |
| 文本 + 图片混合内容 | 默认生成 `.zip`，内含 `README.md` 和 `assets/` 图片 | 如果用户指定 PDF/Word，则生成带图片的 `.pdf` 或 `.docx` |
| 用户上传的原文件 | 默认上传原文件，不转换 | 用户要求转换时再调用文档生成/转换能力 |
| 多个文件/多张图片 | 默认打包成 `.zip` 上传 | 用户指定格式时按指定格式合并或分别保存 |

设计原则：

- 用户发来的原文件，默认保持原样上传。
- AI 生成的纯文本，默认保存为 Markdown。
- 有图片参与时，默认优先保证内容完整，所以使用 zip 打包，避免 Markdown 外链图片失效。
- 用户明确指定 PDF/Word 时，调用现有文档生成模块，把文本和图片嵌入文档。
- 不默认把保存到网盘的内容加入知识库，除非用户明确说“以后参考”“加入知识库”。

---

## 三、文件结构设计

### 新增配置和启动文档

- Modify: `.env.example`
  - 新增百度网盘 OAuth、MCP、token 加密配置。
- Modify: `src/main/resources/application.properties`
  - 新增 `baidu.netdisk.*` 配置映射。
- Modify: `README.md`
  - 增加百度网盘工具说明和协作者配置入口。
- Modify: `docs/COLLABORATOR_BOOTSTRAP.md`
  - 增加百度网盘授权配置、MCP 配置和本地回调说明。

### 新增数据库迁移

- Create: `src/main/resources/db/migration/V6__create_baidu_netdisk_tables.sql`
  - 创建用户网盘授权表。
  - 创建授权 state 表。
  - 创建授权后待恢复任务表。
  - 创建网盘操作日志表。

### 新增 Java 包

```text
src/main/java/com/example/spring/wechat/netdisk/
  ├─ config/
  │   └─ BaiduNetdiskProperties.java
  ├─ auth/
  │   ├─ BaiduNetdiskAuthController.java
  │   ├─ BaiduNetdiskAuthService.java
  │   ├─ NetdiskAuthorizationService.java
  │   └─ NetdiskTokenCryptoService.java
  ├─ client/
  │   ├─ BaiduNetdiskMcpClient.java
  │   └─ BaiduNetdiskOAuthClient.java
  ├─ context/
  │   ├─ NetdiskFileContextService.java
  │   └─ NetdiskFileSnapshot.java
  ├─ export/
  │   ├─ NetdiskSaveSourceResolver.java
  │   ├─ NetdiskContentExportService.java
  │   ├─ NetdiskPackageService.java
  │   └─ NetdiskExportedFile.java
  ├─ model/
  │   ├─ NetdiskAuthorization.java
  │   ├─ NetdiskAuthState.java
  │   ├─ NetdiskPendingAction.java
  │   ├─ NetdiskSaveSource.java
  │   ├─ NetdiskFileItem.java
  │   ├─ NetdiskOperationResult.java
  │   └─ NetdiskShareResult.java
  ├─ repository/
  │   ├─ NetdiskAuthorizationRepository.java
  │   ├─ MySqlNetdiskAuthorizationRepository.java
  │   ├─ NetdiskAuthStateRepository.java
  │   ├─ MySqlNetdiskAuthStateRepository.java
  │   ├─ NetdiskPendingActionRepository.java
  │   └─ MySqlNetdiskPendingActionRepository.java
  └─ service/
      ├─ BaiduNetdiskService.java
      ├─ NetdiskPendingActionExecutor.java
      └─ NetdiskToolResultFormatter.java
```

### 新增微信工具

```text
src/main/java/com/example/spring/wechat/conversation/tools/
  ├─ NetdiskAuthWechatTool.java
  ├─ NetdiskSaveWechatTool.java
  ├─ NetdiskUploadWechatTool.java
  ├─ NetdiskSearchWechatTool.java
  ├─ NetdiskListWechatTool.java
  └─ NetdiskShareWechatTool.java
```

---

## 四、数据库设计

### 1. 用户网盘授权表

```sql
CREATE TABLE user_netdisk_authorizations (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id VARCHAR(128) NOT NULL,
  provider VARCHAR(32) NOT NULL,
  access_token_encrypted TEXT NOT NULL,
  refresh_token_encrypted TEXT,
  expires_at DATETIME,
  scope TEXT,
  status VARCHAR(32) NOT NULL,
  created_at DATETIME NOT NULL,
  updated_at DATETIME NOT NULL,
  UNIQUE KEY uk_user_provider (user_id, provider)
);
```

状态：

```text
ACTIVE
EXPIRED
REVOKED
REPLACED
```

### 2. 授权 state 表

```sql
CREATE TABLE netdisk_auth_states (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  state VARCHAR(128) NOT NULL UNIQUE,
  user_id VARCHAR(128) NOT NULL,
  provider VARCHAR(32) NOT NULL,
  operation VARCHAR(32) NOT NULL,
  redirect_after_auth TEXT,
  pending_action_id BIGINT,
  expires_at DATETIME NOT NULL,
  used TINYINT(1) NOT NULL DEFAULT 0,
  created_at DATETIME NOT NULL
);
```

`operation` 可选：

```text
BIND
REBIND
```

### 3. 授权后待恢复任务表

```sql
CREATE TABLE netdisk_pending_actions (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id VARCHAR(128) NOT NULL,
  provider VARCHAR(32) NOT NULL,
  action_type VARCHAR(64) NOT NULL,
  payload_json TEXT NOT NULL,
  status VARCHAR(32) NOT NULL,
  error_message TEXT,
  expires_at DATETIME NOT NULL,
  created_at DATETIME NOT NULL,
  updated_at DATETIME NOT NULL
);
```

`action_type` 可选：

```text
SAVE_CONTENT
UPLOAD_FILE
SEARCH_FILE
LIST_DIRECTORY
SHARE_FILE
```

`payload_json` 示例：

```json
{
  "tool": "netdisk_save",
  "source_type": "last_assistant_text",
  "source_reference": "latest",
  "target_format": "md",
  "target_path": "/OpenClaw/",
  "file_name": "MySQL-Java接入流程.md"
}
```

任务状态：

```text
PENDING
RUNNING
DONE
FAILED
EXPIRED
```

### 4. 网盘操作日志表

```sql
CREATE TABLE netdisk_operation_logs (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id VARCHAR(128) NOT NULL,
  provider VARCHAR(32) NOT NULL,
  operation VARCHAR(64) NOT NULL,
  request_summary TEXT,
  result_summary TEXT,
  status VARCHAR(32) NOT NULL,
  error_message TEXT,
  created_at DATETIME NOT NULL
);
```

注意：日志里不能保存完整 token、完整授权 URL、隐私文件正文。

---

## 五、配置设计

### `.env.example`

新增：

```properties
# 百度网盘 / MCP
BAIDU_NETDISK_ENABLED=true
BAIDU_NETDISK_CLIENT_ID=
BAIDU_NETDISK_CLIENT_SECRET=
BAIDU_NETDISK_REDIRECT_URI=https://你的公网域名/api/netdisk/baidu/callback
BAIDU_NETDISK_AUTH_BASE_URL=
BAIDU_NETDISK_TOKEN_URL=
BAIDU_NETDISK_MCP_SSE_BASE_URL=https://mcp-pan.baidu.com/sse
BAIDU_NETDISK_TOKEN_ENCRYPTION_KEY=
BAIDU_NETDISK_AUTH_STATE_TTL_MINUTES=10
BAIDU_NETDISK_PENDING_ACTION_TTL_MINUTES=30
BAIDU_NETDISK_MCP_TIMEOUT_MS=20000
BAIDU_NETDISK_CONTEXT_LIMIT=5
BAIDU_NETDISK_DEFAULT_UPLOAD_PATH=/OpenClaw/
```

### `application.properties`

新增：

```properties
baidu.netdisk.enabled=${BAIDU_NETDISK_ENABLED:false}
baidu.netdisk.client-id=${BAIDU_NETDISK_CLIENT_ID:}
baidu.netdisk.client-secret=${BAIDU_NETDISK_CLIENT_SECRET:}
baidu.netdisk.redirect-uri=${BAIDU_NETDISK_REDIRECT_URI:}
baidu.netdisk.auth-base-url=${BAIDU_NETDISK_AUTH_BASE_URL:}
baidu.netdisk.token-url=${BAIDU_NETDISK_TOKEN_URL:}
baidu.netdisk.mcp-sse-base-url=${BAIDU_NETDISK_MCP_SSE_BASE_URL:https://mcp-pan.baidu.com/sse}
baidu.netdisk.token-encryption-key=${BAIDU_NETDISK_TOKEN_ENCRYPTION_KEY:}
baidu.netdisk.auth-state-ttl-minutes=${BAIDU_NETDISK_AUTH_STATE_TTL_MINUTES:10}
baidu.netdisk.pending-action-ttl-minutes=${BAIDU_NETDISK_PENDING_ACTION_TTL_MINUTES:30}
baidu.netdisk.mcp-timeout-ms=${BAIDU_NETDISK_MCP_TIMEOUT_MS:20000}
baidu.netdisk.context-limit=${BAIDU_NETDISK_CONTEXT_LIMIT:5}
baidu.netdisk.default-upload-path=${BAIDU_NETDISK_DEFAULT_UPLOAD_PATH:/OpenClaw/}
```

---

## 六、工具能力设计

### 1. `netdisk_auth`

用途：绑定、查看状态、解绑、更换网盘。

参数：

```json
{
  "operation": "bind | status | unbind | rebind | confirm_rebind | confirm_unbind"
}
```

能力边界：

- 不能接收用户账号密码。
- 只能生成官方授权链接。
- 解绑和更换必须二次确认。
- 更换账号后必须清空该用户网盘文件上下文。

### 2. `netdisk_search`

用途：搜索用户自己百度网盘中的文件。

参数：

```json
{
  "query": "项目文档",
  "limit": 5
}
```

返回：

```text
百度网盘搜索结果：
[文件1] 项目文档.pdf
路径：/资料/项目文档.pdf
大小：2.3 MB
文件ID：xxx
```

### 3. `netdisk_save`

用途：把 AI 生成内容、网页搜索/阅读结果、图片、用户上传文件等保存到用户自己的百度网盘。

参数：

```json
{
  "source_type": "last_assistant_text | web_search_result | web_read_result | generated_image | user_uploaded_file | mixed_content",
  "source_reference": "latest",
  "target_format": "auto | md | txt | pdf | docx | zip | original",
  "target_path": "/OpenClaw/",
  "file_name": "MySQL-Java接入流程.md"
}
```

默认策略：

- `target_format=auto` 时，纯文本默认 `.md`。
- 用户上传文件默认 `original`，直接上传原文件。
- 图片默认上传原图。
- 文本 + 图片默认 `.zip`，里面包含 `README.md` 和 `assets/`。
- 用户明确指定 `.pdf` 或 `.docx` 时，调用现有文档生成能力生成对应文件。

授权策略：

- 如果用户未授权，保存 `pending action` 并返回授权链接。
- 授权成功后自动恢复 `netdisk_save`，不要求用户重新发送需求。

### 4. `netdisk_upload`

用途：把用户发送到微信端的原文件上传到百度网盘。

参数：

```json
{
  "file_reference": "latest",
  "target_path": "/OpenClaw/",
  "keep_original": true
}
```

默认策略：

- 文件来源是用户上传的 PDF、Word、Excel、PPT、图片、压缩包等原文件时，默认保持原文件上传。
- 如果用户要求“转成 PDF/Word 再存”，才调用文档生成或转换能力。
- 如果未授权，保存上传任务为 `pending action`，授权完成后自动继续上传。

### 5. `netdisk_list`

用途：列出某个目录下的文件。

参数：

```json
{
  "path": "/",
  "limit": 20
}
```

### 6. `netdisk_share`

用途：为指定文件生成分享链接。

参数：

```json
{
  "file_id": "xxx",
  "expire_type": "day | week | forever"
}
```

如果用户说“分享刚才第一个文件”，工具应从 `NetdiskFileContextService` 解析最近文件引用。

---

## 七、上下文设计

新增 `NetdiskFileContextService`，只保存短期文件引用。

保存内容：

- 最近搜索结果。
- 最近列目录结果。
- 最近分享文件。

限制：

- 每个用户最多保留最近 5 个文件引用集合。
- 用户更换网盘后立即清空。
- 不能把旧账号文件引用用于新账号。
- 默认不把网盘文件加入知识库。
- 授权前产生的待执行保存/上传任务要持久化到 `netdisk_pending_actions`，不能只存在内存里。
- pending action 到期后不再执行，避免用户很久以后授权导致旧任务突然上传。

用户表达：

```text
分享第一个
打开刚才那个
把第二个文件加入知识库
```

系统从短期文件上下文中解析文件。

新增 `NetdiskSaveSourceResolver`，负责识别“用户到底要保存什么”：

- 上一轮助手文本回答。
- 最近一次网页搜索结果。
- 最近一次网页阅读结果。
- 最近一次 AI 生成图片。
- 最近一次用户上传文件。
- 文本 + 图片混合内容。
- 多文件/多图片集合。

如果用户表达模糊，例如只说“保存到网盘”，但当前没有可确定资源，系统应追问：

```text
你想保存哪一部分内容？可以说“保存刚才的回答”“保存刚才的 PDF”“保存刚才生成的图片”。
```

---

## 八、任务拆解

### Task 1: 配置类和环境变量

**Files:**

- Create: `src/main/java/com/example/spring/wechat/netdisk/config/BaiduNetdiskProperties.java`
- Modify: `.env.example`
- Modify: `src/main/resources/application.properties`
- Test: `src/test/java/com/example/spring/wechat/netdisk/config/BaiduNetdiskPropertiesTests.java`

- [ ] 新增 `BaiduNetdiskProperties` record，字段覆盖 enabled、clientId、clientSecret、redirectUri、authBaseUrl、tokenUrl、mcpSseBaseUrl、tokenEncryptionKey、authStateTtlMinutes、mcpTimeoutMs、contextLimit。
- [ ] 增加 pendingActionTtlMinutes 和 defaultUploadPath 配置。
- [ ] 在 `application.properties` 增加 `baidu.netdisk.*` 映射。
- [ ] 在 `.env.example` 增加百度网盘配置示例。
- [ ] 写配置默认值测试，验证 `contextLimit <= 0` 时回退为 5，`authStateTtlMinutes <= 0` 时回退为 10。
- [ ] 运行：`mvn -q "-Dtest=BaiduNetdiskPropertiesTests,ApplicationContextTests" test`。

### Task 2: Flyway 建表

**Files:**

- Create: `src/main/resources/db/migration/V6__create_baidu_netdisk_tables.sql`
- Test: `src/test/java/com/example/spring/wechat/netdisk/repository/NetdiskSchemaTests.java`

- [ ] 创建 `user_netdisk_authorizations`。
- [ ] 创建 `netdisk_auth_states`。
- [ ] 创建 `netdisk_pending_actions`。
- [ ] 创建 `netdisk_operation_logs`。
- [ ] 写 Spring JDBC 测试，验证表存在且能插入基础记录。
- [ ] 运行：`mvn -q "-Dtest=NetdiskSchemaTests" test`。

### Task 3: token 加密服务

**Files:**

- Create: `src/main/java/com/example/spring/wechat/netdisk/auth/NetdiskTokenCryptoService.java`
- Test: `src/test/java/com/example/spring/wechat/netdisk/auth/NetdiskTokenCryptoServiceTests.java`

- [ ] 使用 AES/GCM 加密 token。
- [ ] 密钥从 `BAIDU_NETDISK_TOKEN_ENCRYPTION_KEY` 读取。
- [ ] 加密结果包含随机 IV，保证同一个 token 两次加密结果不同。
- [ ] 解密失败时抛出清晰异常。
- [ ] 测试加密/解密、空密钥报错、错误密钥解密失败。
- [ ] 运行：`mvn -q "-Dtest=NetdiskTokenCryptoServiceTests" test`。

### Task 4: 授权 Repository

**Files:**

- Create: `src/main/java/com/example/spring/wechat/netdisk/repository/NetdiskAuthorizationRepository.java`
- Create: `src/main/java/com/example/spring/wechat/netdisk/repository/MySqlNetdiskAuthorizationRepository.java`
- Create: `src/main/java/com/example/spring/wechat/netdisk/repository/NetdiskAuthStateRepository.java`
- Create: `src/main/java/com/example/spring/wechat/netdisk/repository/MySqlNetdiskAuthStateRepository.java`
- Create: `src/main/java/com/example/spring/wechat/netdisk/repository/NetdiskPendingActionRepository.java`
- Create: `src/main/java/com/example/spring/wechat/netdisk/repository/MySqlNetdiskPendingActionRepository.java`
- Test: `src/test/java/com/example/spring/wechat/netdisk/repository/NetdiskAuthorizationRepositoryTests.java`

- [ ] 实现按 `user_id + provider` 查询当前 ACTIVE 授权。
- [ ] 实现保存/更新授权。
- [ ] 实现把旧授权标记为 REVOKED 或 REPLACED。
- [ ] 实现创建、查询、使用 auth_state。
- [ ] auth_state 使用后必须标记 used。
- [ ] 实现创建、查询、更新 pending action。
- [ ] pending action 支持 PENDING/RUNNING/DONE/FAILED/EXPIRED 状态。
- [ ] 运行：`mvn -q "-Dtest=NetdiskAuthorizationRepositoryTests" test`。

### Task 5: OAuth 客户端和授权服务

**Files:**

- Create: `src/main/java/com/example/spring/wechat/netdisk/client/BaiduNetdiskOAuthClient.java`
- Create: `src/main/java/com/example/spring/wechat/netdisk/auth/BaiduNetdiskAuthService.java`
- Create: `src/main/java/com/example/spring/wechat/netdisk/auth/NetdiskAuthorizationService.java`
- Create: `src/main/java/com/example/spring/wechat/netdisk/auth/BaiduNetdiskAuthController.java`
- Test: `src/test/java/com/example/spring/wechat/netdisk/auth/BaiduNetdiskAuthServiceTests.java`

- [ ] 实现生成授权 URL。
- [ ] 授权 URL 必须包含 `client_id`、`redirect_uri`、`response_type=code`、`state`。
- [ ] 实现 callback：校验 state、使用 code 换 token、加密保存 token。
- [ ] callback 保存 token 后，如果 state 绑定了 pending_action_id，则触发后台恢复执行。
- [ ] 实现 refresh token。
- [ ] 实现绑定状态查询。
- [ ] 实现解绑和更换账号的二次确认服务入口。
- [ ] 实现 `ensureAuthorized(userId, operation, pendingAction)`：已授权返回可用授权；未授权保存 pending action 并返回授权链接。
- [ ] 运行：`mvn -q "-Dtest=BaiduNetdiskAuthServiceTests" test`。

### Task 6: MCP 客户端

**Files:**

- Create: `src/main/java/com/example/spring/wechat/netdisk/client/BaiduNetdiskMcpClient.java`
- Create: `src/main/java/com/example/spring/wechat/netdisk/model/NetdiskFileItem.java`
- Create: `src/main/java/com/example/spring/wechat/netdisk/model/NetdiskShareResult.java`
- Test: `src/test/java/com/example/spring/wechat/netdisk/client/BaiduNetdiskMcpClientTests.java`

- [ ] MCP 请求每次都使用当前用户 token。
- [ ] 构造 SSE URL 时不要在日志里打印完整 access_token。
- [ ] 实现 `search(query, limit)`。
- [ ] 实现 `list(path, limit)`。
- [ ] 实现 `share(fileId, expireType)`。
- [ ] 实现 `upload(localFile, targetPath)`，第一版若官方 MCP SSE 不支持上传，则通过本地 sidecar 或后续专用上传客户端实现；工具层接口先稳定下来。
- [ ] MCP 返回异常时转换为用户可理解错误。
- [ ] 运行：`mvn -q "-Dtest=BaiduNetdiskMcpClientTests" test`。

### Task 7: 内容识别、导出和打包服务

**Files:**

- Create: `src/main/java/com/example/spring/wechat/netdisk/export/NetdiskSaveSourceResolver.java`
- Create: `src/main/java/com/example/spring/wechat/netdisk/export/NetdiskContentExportService.java`
- Create: `src/main/java/com/example/spring/wechat/netdisk/export/NetdiskPackageService.java`
- Create: `src/main/java/com/example/spring/wechat/netdisk/export/NetdiskExportedFile.java`
- Create: `src/main/java/com/example/spring/wechat/netdisk/model/NetdiskSaveSource.java`
- Test: `src/test/java/com/example/spring/wechat/netdisk/export/NetdiskContentExportServiceTests.java`

- [ ] 支持解析上一轮助手文本回答。
- [ ] 支持解析最近网页搜索/网页阅读结果。
- [ ] 支持解析最近 AI 生成图片。
- [ ] 支持解析用户上传原文件。
- [ ] 纯文本未指定格式时导出 `.md`。
- [ ] 用户上传原文件未指定格式时保持原文件。
- [ ] 图片未指定格式时保持原图。
- [ ] 文本 + 图片未指定格式时导出 `.zip`，包含 `README.md` 和 `assets/`。
- [ ] 用户指定 PDF/Word 时调用现有文档生成能力。
- [ ] 没有可确定资源时返回追问文本，不执行上传。
- [ ] 运行：`mvn -q "-Dtest=NetdiskContentExportServiceTests" test`。

### Task 8: 网盘业务服务和 pending action 恢复

**Files:**

- Create: `src/main/java/com/example/spring/wechat/netdisk/service/BaiduNetdiskService.java`
- Create: `src/main/java/com/example/spring/wechat/netdisk/service/NetdiskToolResultFormatter.java`
- Create: `src/main/java/com/example/spring/wechat/netdisk/service/NetdiskPendingActionExecutor.java`
- Create: `src/main/java/com/example/spring/wechat/netdisk/context/NetdiskFileContextService.java`
- Create: `src/main/java/com/example/spring/wechat/netdisk/context/NetdiskFileSnapshot.java`
- Test: `src/test/java/com/example/spring/wechat/netdisk/service/BaiduNetdiskServiceTests.java`

- [ ] 所有业务方法先检查授权。
- [ ] 未授权时返回授权链接。
- [ ] token 过期时自动刷新。
- [ ] 搜索和列目录结果写入短期文件上下文。
- [ ] 更换网盘后清空短期文件上下文。
- [ ] 支持“第一个文件”“刚才那个文件”的引用解析。
- [ ] 实现 `saveToNetdisk`：识别资源、导出文件、上传网盘。
- [ ] 实现 `uploadOriginalFile`：用户上传文件默认原样保存。
- [ ] 实现 pending action 恢复执行。
- [ ] pending action 执行完成后通过微信发送结果通知。
- [ ] pending action 失败时通过微信发送失败原因。
- [ ] 运行：`mvn -q "-Dtest=BaiduNetdiskServiceTests" test`。

### Task 9: 微信 Function Calling 工具

**Files:**

- Create: `src/main/java/com/example/spring/wechat/conversation/tools/NetdiskAuthWechatTool.java`
- Create: `src/main/java/com/example/spring/wechat/conversation/tools/NetdiskSaveWechatTool.java`
- Create: `src/main/java/com/example/spring/wechat/conversation/tools/NetdiskUploadWechatTool.java`
- Create: `src/main/java/com/example/spring/wechat/conversation/tools/NetdiskSearchWechatTool.java`
- Create: `src/main/java/com/example/spring/wechat/conversation/tools/NetdiskListWechatTool.java`
- Create: `src/main/java/com/example/spring/wechat/conversation/tools/NetdiskShareWechatTool.java`
- Test: `src/test/java/com/example/spring/wechat/conversation/tools/NetdiskWechatToolTests.java`

- [ ] `netdisk_auth` 支持 `bind/status/unbind/rebind/confirm_rebind/confirm_unbind`。
- [ ] `netdisk_save` 支持 `source_type/source_reference/target_format/target_path/file_name`。
- [ ] `netdisk_upload` 支持 `file_reference/target_path/keep_original`。
- [ ] `netdisk_search` 支持 `query/limit`。
- [ ] `netdisk_list` 支持 `path/limit`。
- [ ] `netdisk_share` 支持 `file_id/expire_type/file_reference`。
- [ ] 每个工具提供清晰的 `WechatToolCapability`。
- [ ] 未授权时工具返回授权链接。
- [ ] 风险操作必须二次确认。
- [ ] 未授权触发保存/上传时，必须保存 pending action，授权后自动继续完成。
- [ ] 运行：`mvn -q "-Dtest=NetdiskWechatToolTests,ApplicationContextTests" test`。

### Task 10: 与知识库的边界处理

**Files:**

- Modify: `src/main/java/com/example/spring/wechat/conversation/tools/KnowledgeAddWechatTool.java`
- Create: `src/main/java/com/example/spring/wechat/conversation/tools/NetdiskToKnowledgeWechatTool.java`
- Test: `src/test/java/com/example/spring/wechat/conversation/tools/NetdiskToKnowledgeWechatToolTests.java`

- [ ] 第一版不默认同步整个网盘。
- [ ] 只有用户明确说“加入知识库”“以后参考”时，才把指定网盘文件下载/解析/入库。
- [ ] 如果文件过大，提示用户分批或选择具体文件。
- [ ] 用户更换网盘后，不自动删除已入库知识。
- [ ] 运行：`mvn -q "-Dtest=NetdiskToKnowledgeWechatToolTests" test`。

### Task 11: 文档和协作者启动说明

**Files:**

- Modify: `README.md`
- Modify: `docs/COLLABORATOR_BOOTSTRAP.md`
- Create: `docs/BAIDU_NETDISK_MCP_TOOL.md`

- [ ] 写百度网盘 MCP 工具说明。
- [ ] 写 `.env` 配置说明。
- [ ] 写用户授权流程。
- [ ] 写授权后自动恢复未完成保存/上传任务流程。
- [ ] 写更换网盘流程。
- [ ] 写 AI 内容保存格式规则：纯文本默认 md，原文件默认 original，图文混合默认 zip。
- [ ] 写本地开发 callback_url 说明。
- [ ] 写常见问题：未授权、token 过期、授权失败、MCP 调用失败、跨账号上下文失效。
- [ ] 运行：`mvn -q "-Dtest=EncodingHealthTests" test`。

### Task 12: 全量验证

**Files:**

- No production file changes.

- [ ] 运行：`mvn -q test`。
- [ ] 运行：`git diff --check`。
- [ ] 人工验证微信端：
  - 发送“绑定百度网盘”。
  - 点击授权链接完成绑定。
  - 发送“我绑定网盘了吗”。
  - 未授权时发送“把刚才的回答存到网盘”，完成授权后确认系统自动继续上传。
  - 发送“把刚才的 MySQL Java 接入流程保存成 PDF 到网盘”。
  - 发送一个 PDF 后，再发送“把这个文件存到网盘”，确认上传原文件。
  - 发送图文混合内容后，确认默认打包 zip 上传。
  - 发送“帮我找一下网盘里的项目文档”。
  - 发送“分享第一个文件”。
  - 发送“我要更换百度网盘”。
  - 发送“确认更换网盘”。
  - 确认旧文件上下文失效。

---

## 九、安全和隐私要求

- 不能让用户把百度账号密码发给机器人。
- 只能走官方 OAuth 授权。
- token 必须加密保存。
- 日志不能打印完整 access_token、refresh_token、授权 URL。
- pending action 不能保存明文 token。
- pending action 必须设置过期时间。
- 授权 callback 不应长时间阻塞，应异步恢复未完成任务。
- 更换网盘后必须清空短期文件上下文。
- 网盘文件不默认加入知识库。
- 解绑和更换网盘必须二次确认。
- MCP 调用失败要给用户明确错误，不要无回复。
- 不同微信用户之间不能共享授权、文件上下文和搜索结果。

---

## 十、验收标准

- 未绑定用户请求网盘操作时，会收到授权链接。
- 未绑定用户请求“把文件/内容存到网盘”时，系统保存 pending action，授权完成后自动继续保存，不要求用户重新发送需求。
- 用户授权成功后，系统能保存加密 token。
- 用户再次请求网盘操作时，不需要重复授权。
- token 过期时系统能尝试刷新。
- 用户可以查看绑定状态。
- 用户可以解绑百度网盘。
- 用户可以更换百度网盘，且旧账号上下文失效。
- 用户可以搜索自己网盘文件。
- 用户可以列目录。
- 用户可以分享最近搜索到的文件。
- 用户可以把上一轮 AI 文本回答保存到网盘；未指定格式时默认生成 Markdown。
- 用户可以指定把 AI 文本回答保存为 PDF 或 Word。
- 用户上传原文件后说“存到网盘”，系统默认上传原文件，不转换。
- 内容中包含文本和图片时，未指定格式默认打包 zip，包含 Markdown 和图片 assets。
- 用户授权后自动恢复的 pending action 过期时，系统提示任务已过期，不再上传旧内容。
- 用户明确要求后，网盘文件才能进入知识库。
- `mvn test` 通过。
- `git diff --check` 无实际格式错误。
