# Chrome DevTools MCP Docker Sidecar 设计

## 目标

为 OpenClaw 增加浏览器自动化能力，让微信 Agent 可以打开网页、点击页面、输入文本、截图和读取页面状态。第一版使用 Docker 容器内的 Chromium 与 `chrome-devtools-mcp`，Java 主应用只通过本地 HTTP MCP sidecar 调用浏览器能力，不直接管理 Chrome 或 Node 进程。

## 范围

第一版只支持可控、可审计的基础浏览器动作：

- 打开指定 URL。
- 点击页面元素。
- 向页面输入文本。
- 截图并返回本地文件路径。
- 读取当前页面可见文本或简短状态。

第一版不支持自动登录真实账号、自动输入密码或验证码、自动支付、自动删除线上数据、绕过网站风控、批量爬取外站数据、使用宿主机 Chrome 登录态。需要宿主机 Chrome 登录态时，后续再单独设计非默认的高风险模式。

## 推荐方案

采用 Docker sidecar：

```text
微信用户
  -> WechatTool
  -> BrowserAutomationService
  -> BrowserMcpClient
  -> browser-mcp-sidecar HTTP endpoint
  -> chrome-devtools-mcp
  -> 容器内 Chromium
```

这个方案和现有项目里的 MCP 思路一致。Java 端继续走 HTTP 调用，sidecar 负责 Node、Chromium、`chrome-devtools-mcp` 和 stdio/HTTP 适配。这样 Java 代码保持简单，Docker 环境也更容易复现。

## 组件设计

### Docker Sidecar

新增 `browser-mcp-sidecar/` 目录。它包含 Dockerfile、Node package、HTTP server 和启动脚本。容器内安装 Chromium、Node 依赖和 `chrome-devtools-mcp`。HTTP server 对 Java 暴露统一接口，默认监听 `0.0.0.0:3333`，容器内部把请求转发给 MCP server。

容器默认使用无头 Chromium，并保存一个容器内浏览器 profile 到 volume。这个 profile 只属于 sidecar，不读取宿主机 Chrome 数据。

### Java 配置

新增 `browser.automation.*` 配置：

```properties
browser.automation.enabled=${BROWSER_AUTOMATION_ENABLED:false}
browser.automation.mcp-endpoint=${BROWSER_AUTOMATION_MCP_ENDPOINT:http://127.0.0.1:3333/mcp}
browser.automation.api-key=${BROWSER_AUTOMATION_API_KEY:}
browser.automation.timeout-ms=${BROWSER_AUTOMATION_TIMEOUT_MS:30000}
browser.automation.allow-external-url=${BROWSER_AUTOMATION_ALLOW_EXTERNAL_URL:false}
browser.automation.allowed-hosts=${BROWSER_AUTOMATION_ALLOWED_HOSTS:localhost,127.0.0.1}
browser.automation.screenshot-dir=${BROWSER_AUTOMATION_SCREENSHOT_DIR:data/browser/screenshots}
browser.automation.require-confirmation-for-risky-actions=${BROWSER_AUTOMATION_REQUIRE_CONFIRMATION_FOR_RISKY_ACTIONS:true}
```

默认关闭。默认只允许访问 `localhost` 和 `127.0.0.1`，适合测试本地 Web 项目。外网访问必须显式开启。

### Java 模块

新增包：

```text
src/main/java/com/example/spring/wechat/browser/
  config/BrowserAutomationProperties.java
  client/BrowserMcpClient.java
  service/BrowserAutomationService.java
  model/BrowserActionResult.java
```

`BrowserAutomationProperties` 负责绑定配置和默认值。`BrowserMcpClient` 负责调用 sidecar endpoint。`BrowserAutomationService` 做动作编排、URL 白名单、危险动作判断、错误转换和截图路径管理。`BrowserActionResult` 统一返回动作结果，包括成功状态、用户可见消息、页面标题、当前 URL、截图路径和原始摘要。

### 微信工具

第一版新增 5 个工具：

- `browser_open`：打开 URL。
- `browser_click`：点击元素。
- `browser_type`：输入文本。
- `browser_screenshot`：截图。
- `browser_read_page`：读取当前页面可见文本或页面摘要。

每个工具都是薄封装，只解析参数并调用 `BrowserAutomationService`。工具通过 `@ConditionalOnProperty(prefix = "browser.automation", name = "enabled", havingValue = "true")` 控制注册。

## 工具契约

### browser_open

参数：

- `url`：必填，目标 URL。

行为：

- 校验 URL 协议，只允许 `http` 和 `https`。
- 默认只允许配置中的 host。
- 打开成功后返回页面标题和当前 URL。

### browser_click

参数：

- `target`：必填，可以是按钮文字、链接文字、元素描述或 CSS selector。
- `confirm_token`：可选，用于确认危险动作。

行为：

- 对包含删除、支付、购买、提交、发送、授权、登录等词汇的目标启用二次确认。
- 未确认时不执行点击，返回确认提示。
- 确认后调用 MCP 点击工具。

### browser_type

参数：

- `target`：必填，输入框描述或 selector。
- `text`：必填，输入内容。

行为：

- 拒绝输入明显敏感内容，例如密码、验证码、银行卡号、私钥。
- 输入成功后返回简短结果，不回显完整输入内容。

### browser_screenshot

参数：

- `name`：可选，截图文件名提示。

行为：

- 调用 sidecar 截图。
- 保存到 `browser.automation.screenshot-dir`。
- 返回本地路径，日志里不打印图片二进制。

### browser_read_page

参数：

- `max_chars`：可选，默认 2000。

行为：

- 读取当前页面可见文本。
- 超长内容截断。
- 不返回 cookie、localStorage、token 或隐藏表单字段。

## Sidecar HTTP API

Java 第一版不直接依赖 MCP stdio 细节，只依赖 sidecar 的 HTTP 接口：

```text
GET  /health
POST /mcp
```

`/mcp` 接收标准 JSON-RPC 风格 MCP 请求，sidecar 内部负责调用 `chrome-devtools-mcp`。如果 `chrome-devtools-mcp` 工具名与预期不同，适配逻辑只改 sidecar，不改 Java 工具契约。

## Docker 设计

新增 `browser-mcp-sidecar/compose.yaml`，与现有 `xhs-sidecar/compose.yaml` 保持相似风格。推荐服务：

```yaml
services:
  browser-mcp-sidecar:
    image: openclaw-browser-mcp-sidecar:local
    build:
      context: .
      dockerfile: Dockerfile
      args:
        BASE_REGISTRY: ${BASE_REGISTRY:-dockerproxy.net/library}
    restart: unless-stopped
    environment:
      BROWSER_MCP_HOST: 0.0.0.0
      BROWSER_MCP_PORT: 3333
      CHROME_HEADLESS: "true"
      CHROME_USER_DATA_DIR: /data/chrome-profile
    ports:
      - "127.0.0.1:3333:3333"
    volumes:
      - browser-chrome-profile:/data/chrome-profile
      - browser-screenshots:/data/screenshots
    shm_size: "1gb"
    security_opt:
      - no-new-privileges:true

volumes:
  browser-chrome-profile:
  browser-screenshots:
```

容器需要较大的 `/dev/shm`，避免 Chromium 页面渲染或截图时崩溃。默认只绑定到 `127.0.0.1`，避免暴露浏览器控制接口到局域网。

## 安全边界

浏览器自动化是高风险能力，默认采用保守策略：

- 默认关闭工具。
- 默认只允许访问本地地址。
- 外网访问必须显式配置。
- 不使用宿主机 Chrome 登录态。
- 不允许输入密码、验证码、私钥、银行卡号等敏感内容。
- 点击高风险动作需要二次确认。
- 不在日志中保存 cookie、token、完整表单内容、完整页面 HTML。
- 截图目录使用项目数据目录或 Docker volume，避免写到任意路径。
- MCP endpoint 只监听本机。

## 错误处理

用户可见错误保持简短：

- 未启用：提示浏览器自动化未启用。
- sidecar 未启动：提示检查 `browser-mcp-sidecar` 容器。
- URL 不允许：提示当前只允许访问配置白名单内的地址。
- 页面元素找不到：提示换一种描述或先读取页面。
- 危险动作未确认：返回确认提示。
- 截图失败：提示页面可能未加载完成或浏览器异常。

日志保留动作类型、脱敏 URL、耗时和错误类型，不记录敏感页面内容。

## 测试策略

单元测试覆盖：

- 配置默认值和 allowed hosts 解析。
- URL 白名单校验。
- 危险点击识别和确认 token 流程。
- 敏感输入拦截。
- `BrowserMcpClient` 构造 MCP 请求参数。
- 微信工具暴露的参数和能力说明。

集成或手动验收覆盖：

- 启动 `browser-mcp-sidecar` 容器。
- 打开本地测试页面。
- 点击按钮。
- 输入普通文本。
- 截图并确认文件生成。
- 读取页面文本。

真实外站测试需要显式打开外网配置，并且只用于公开页面。

## 实施顺序

1. 新增配置类和测试。
2. 新增 Java MCP 客户端和服务测试。
3. 新增微信工具和工具定义测试。
4. 新增 `browser-mcp-sidecar` Docker 目录。
5. 新增 README 或协作者启动文档。
6. 运行相关单元测试和 `mvn test`。
7. 启动 sidecar 做本地页面手动验收。

## 验收标准

- `browser.automation.enabled=false` 时浏览器工具不会注册。
- 启用后 function-calling 能看到 `browser_open`、`browser_click`、`browser_type`、`browser_screenshot`、`browser_read_page`。
- 默认配置只能打开本地地址。
- 危险点击需要确认。
- 敏感输入会被拒绝。
- sidecar 未启动时返回可理解错误。
- sidecar 启动后可以对本地页面完成打开、点击、输入、截图和读取页面。
