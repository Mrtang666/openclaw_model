# OpenClaw 运行使用文档

这份文档写给第一次拿到 OpenClaw 的使用者。你不需要懂 Maven、Spring Boot 或 Docker，也可以按步骤把项目跑起来。

推荐使用方式：

1. 先跑通基础功能：JDK 17 + MySQL + `.env` + `openclaw-cli.jar`。
2. 再按需开启增强功能：Qdrant、浏览器自动化、小红书采集、邮件、地图、语音、图片等。
3. 不需要的功能保持关闭，不会影响基础启动。

## 1. 你拿到的文件应该长这样

如果你拿到的是发布包，解压后建议目录类似：

```text
openclaw-release/
  openclaw-cli.jar
  .env.example
  start.bat
  start.sh
  README-RUN.md
  optional/
    browser-mcp-sidecar/
    xhs-sidecar/
```

每个文件的作用：

| 文件或目录 | 作用 |
| --- | --- |
| `openclaw-cli.jar` | 主程序，双击脚本或用 `java -jar` 启动 |
| `.env.example` | 配置模板，复制成 `.env` 后填写自己的配置 |
| `start.bat` | Windows 一键启动脚本 |
| `start.sh` | Linux/macOS 一键启动脚本 |
| `README-RUN.md` | 当前文档 |
| `optional/browser-mcp-sidecar/` | 可选，浏览器自动化 Docker 服务 |
| `optional/xhs-sidecar/` | 可选，小红书采集 Docker 服务 |

如果你拿到的是源码目录，而不是发布包，也可以运行。源码目录里对应关系是：

| 发布包路径 | 源码目录路径 |
| --- | --- |
| `openclaw-cli.jar` | `target/openclaw-cli-0.0.1-SNAPSHOT.jar` |
| `optional/browser-mcp-sidecar/` | `browser-mcp-sidecar/` |
| `optional/xhs-sidecar/` | `xhs-sidecar/` |

## 2. 功能分层

OpenClaw 的功能比较多，但不是所有功能都必须一次性配置。

### 2.1 必须准备的基础功能

这些是跑起主程序最少需要的东西：

| 项目 | 是否必须 | 说明 |
| --- | --- | --- |
| JDK 17 | 必须 | 用来运行 `openclaw-cli.jar` |
| MySQL 8.x | 必须 | 保存会话、记忆、工具记录、文件记录等 |
| `.env` | 必须 | 保存数据库、模型 Key、端口等配置 |
| DashScope 或兼容模型服务 | 建议 | 如果不配置，大模型聊天和很多 Agent 能力不可用 |

### 2.2 配置 Key 后可用的功能

这些不需要额外 Docker 服务，只要在 `.env` 里填好 Key 或开关：

| 功能 | 主要配置 |
| --- | --- |
| 大模型聊天 | `DASHSCOPE_API_KEY`、`DASHSCOPE_BASE_URL`、`DASHSCOPE_CHAT_MODEL` |
| 图片理解 | `DASHSCOPE_API_KEY`、`DASHSCOPE_VISION_MODEL` |
| 图片生成 | `DASHSCOPE_IMAGE_BASE_URL`、`DASHSCOPE_IMAGE_MODEL` |
| 语音识别 | `DASHSCOPE_VOICE_BASE_URL`、`DASHSCOPE_VOICE_MODEL` |
| 语音合成 | `DASHSCOPE_TTS_BASE_URL`、`DASHSCOPE_TTS_MODEL` |
| 天气和地图 | `AMAP_WEATHER_KEY`、`AMAP_MAP_KEY` |
| QQ 邮件 | `EMAIL_ENABLED=true` 和 SMTP 配置 |
| 快递查询 | `KUAIDI100_CUSTOMER`、`KUAIDI100_KEY` |
| 百度网盘 | `BAIDU_NETDISK_ENABLED=true` 和百度网盘配置 |
| 美团旅行 CLI | `MEITUAN_TRAVEL_ENABLED=true` 和美团 CLI 配置 |

### 2.3 需要额外安装或 Docker 的功能

| 功能 | 需要什么 |
| --- | --- |
| 知识库 / RAG | Qdrant，推荐用 Docker 启动 |
| 浏览器自动化 | `browser-mcp-sidecar`，推荐用 Docker Compose 启动 |
| 小红书采集 | `xhs-sidecar`，推荐用 Docker Compose 启动 |
| 微信语音转码 | `ffmpeg`，可选；没有也可以关闭 |

## 3. 第一次运行：最小可用版本

本节目标：先把主程序跑起来，能进入 CLI，并能执行 `/help`、`/status`、`/wechat start`。

### 3.1 安装 JDK 17

先确认电脑有没有 Java。

Windows 打开 PowerShell，输入：

```powershell
java -version
```

如果看到类似下面的信息，说明 Java 可用：

```text
version "17"
```

或者：

```text
openjdk version "17"
```

如果提示 `java 不是内部或外部命令`，说明没有安装 JDK，或者环境变量没有配置好。请安装 JDK 17，并确认安装后重新打开 PowerShell 再执行 `java -version`。

注意：这个项目使用 Spring Boot 3，运行时建议使用 JDK 17。不要用 JDK 8 运行 Jar。

### 3.2 安装并启动 MySQL

OpenClaw 需要 MySQL。建议使用 MySQL 8.x。

安装好 MySQL 后，确认 MySQL 服务已经启动。

如果你会用命令行，可以执行：

```powershell
mysql -u root -p
```

输入密码后进入 MySQL，再执行：

```sql
CREATE DATABASE openclaw DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

如果提示数据库已经存在，可以忽略：

```sql
CREATE DATABASE IF NOT EXISTS openclaw DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

如果你不会用命令行，也可以用 MySQL Workbench、Navicat 或其他图形化工具新建数据库：

```text
数据库名：openclaw
字符集：utf8mb4
排序规则：utf8mb4_unicode_ci
```

项目第一次启动时会自动建业务表，不需要你手动导入所有表结构。这个过程由 Flyway 自动完成。

### 3.3 创建 `.env`

进入 `openclaw-release` 目录。

Windows：

```powershell
copy .env.example .env
```

Linux/macOS：

```bash
cp .env.example .env
```

然后用文本编辑器打开 `.env`。

Windows 可以执行：

```powershell
notepad .env
```

### 3.4 填写最小配置

先只关注下面这些配置。其他配置可以暂时不动。

```properties
# MySQL
MYSQL_URL=jdbc:mysql://127.0.0.1:3306/openclaw?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai
MYSQL_USERNAME=root
MYSQL_PASSWORD=你的MySQL密码
FLYWAY_ENABLED=true

# Web 服务端口
WECHAT_LOGIN_SERVER_ADDRESS=127.0.0.1
WECHAT_LOGIN_SERVER_PORT=8080

# 大模型服务
DASHSCOPE_API_KEY=你的模型Key
DASHSCOPE_BASE_URL=你的模型服务地址
DASHSCOPE_CHAT_MODEL=qwen3.7-max-2026-06-08
DASHSCOPE_ENABLE_THINKING=true
```

如果你暂时没有模型 Key，也可以先启动项目，但普通聊天、图片、语音、工具规划等能力会不可用或报配置缺失。

### 3.5 启动主程序

Windows 推荐：

```powershell
.\start.bat
```

如果没有 `start.bat`，也可以直接运行：

```powershell
java -Dfile.encoding=UTF-8 -jar openclaw-cli.jar
```

Linux/macOS：

```bash
chmod +x start.sh
./start.sh
```

如果没有 `start.sh`：

```bash
java -Dfile.encoding=UTF-8 -jar openclaw-cli.jar
```

启动成功后，控制台会出现类似提示：

```text
OpenClaw CLI 已启动，直接输入内容可与大模型对话；输入 /help 查看命令；输入 exit 退出。
```

### 3.6 第一次启动后做检查

在 CLI 里依次输入：

```text
/help
/version
/status
```

如果这些命令都有响应，说明主程序已经跑起来了。

再测试微信登录入口：

```text
/wechat start
```

之后根据控制台输出或浏览器页面提示扫码登录。

微信登录页一般类似：

```text
http://127.0.0.1:8080/wechat-login/index.html?session=...
```

注意：不要自己随便编 session 参数。以 `/wechat start` 输出的真实地址为准。

## 4. 常用命令

主程序启动后，你会进入 OpenClaw CLI。

常用命令：

```text
/help
/version
/status
/weather 北京
/wechat start
/wechat status
/wechat stop
/xhs start
/xhs status
/xhs help
exit
```

说明：

| 命令 | 作用 |
| --- | --- |
| `/help` | 查看可用命令 |
| `/version` | 查看当前版本 |
| `/status` | 查看运行状态 |
| `/weather 北京` | 查询天气，要求配置高德 Key |
| `/wechat start` | 启动微信登录流程 |
| `/wechat status` | 查看微信连接状态 |
| `/wechat stop` | 停止微信连接 |
| `/xhs start` | 打开小红书舆情管理台 |
| `/xhs status` | 查看小红书舆情模块状态 |
| `/xhs help` | 查看小红书命令帮助 |
| `exit` | 退出程序 |

## 5. Docker 功能怎么使用

Docker 不是主程序必须项。它主要用来运行可选增强服务。

你可以这样理解：

```text
主程序：openclaw-cli.jar
可选服务：Docker 容器
连接方式：在 .env 里打开开关并填写服务地址
```

建议启动顺序：

```text
1. 先启动 MySQL
2. 按需启动 Docker 服务
3. 修改主程序 .env
4. 再启动 openclaw-cli.jar
```

如果主程序已经启动，修改 `.env` 后通常需要重启主程序才会生效。

### 5.1 安装 Docker Desktop

Windows 和 macOS 推荐安装 Docker Desktop。

安装后先打开 Docker Desktop，等它显示 Docker Engine 已运行。

然后在 PowerShell 验证：

```powershell
docker --version
docker compose version
```

如果这两个命令都有版本输出，说明 Docker 可用。

## 6. 使用 Qdrant：知识库 / RAG

如果你想用这些能力，需要启动 Qdrant：

```text
保存网页到知识库
保存文档到知识库
根据知识库内容问答
RAG 检索增强回答
```

### 6.1 启动 Qdrant

PowerShell：

```powershell
docker run -d `
  --name openclaw-qdrant `
  -p 6333:6333 `
  -v qdrant_storage:/qdrant/storage `
  qdrant/qdrant
```

如果提示容器名已经存在，说明以前创建过。直接启动旧容器：

```powershell
docker start openclaw-qdrant
```

### 6.2 验证 Qdrant

查看容器：

```powershell
docker ps
```

浏览器打开：

```text
http://localhost:6333/dashboard
```

如果能打开 Qdrant 控制台，说明 Qdrant 已经启动。

### 6.3 修改主程序 `.env`

```properties
RAG_ENABLED=true
RAG_AUTO_RETRIEVE=true

QDRANT_HOST=localhost
QDRANT_HTTP_PORT=6333
QDRANT_API_KEY=
QDRANT_COLLECTION=openclaw_knowledge
QDRANT_DISTANCE=Cosine
QDRANT_VECTOR_SIZE=0

DASHSCOPE_EMBEDDING_MODEL=text-embedding-v4
DASHSCOPE_EMBEDDING_BASE_URL=${DASHSCOPE_BASE_URL}
DASHSCOPE_EMBEDDING_API_KEY=${DASHSCOPE_API_KEY}
```

然后重启主程序。

### 6.4 停止 Qdrant

只停止，不删数据：

```powershell
docker stop openclaw-qdrant
```

删除容器但保留数据卷：

```powershell
docker rm openclaw-qdrant
```

不要随便删除 `qdrant_storage` 数据卷，否则知识库向量数据会丢失。

## 7. 使用浏览器自动化

浏览器自动化用于让 Agent 做这些事：

```text
打开网页
读取网页
点击按钮
输入文字
截图
辅助网页测试
```

它由 `browser-mcp-sidecar` 提供，推荐用 Docker Compose 启动。

### 7.1 启动 browser-mcp-sidecar

如果你用的是发布包：

```powershell
docker compose -f optional/browser-mcp-sidecar/compose.yaml up -d --build
```

如果你用的是源码目录：

```powershell
docker compose -f browser-mcp-sidecar/compose.yaml up -d --build
```

第一次启动会下载镜像和依赖，可能比较慢。

### 7.2 验证 browser-mcp-sidecar

```powershell
Invoke-RestMethod http://127.0.0.1:3333/health
```

如果返回健康状态，说明服务可用。

也可以看容器：

```powershell
docker ps
```

### 7.3 修改主程序 `.env`

```properties
BROWSER_AUTOMATION_ENABLED=true
BROWSER_AUTOMATION_MCP_ENDPOINT=http://127.0.0.1:3333/mcp
BROWSER_AUTOMATION_API_KEY=
BROWSER_AUTOMATION_TIMEOUT_MS=30000
BROWSER_AUTOMATION_ALLOW_EXTERNAL_URL=false
BROWSER_AUTOMATION_ALLOWED_HOSTS=localhost,127.0.0.1
BROWSER_AUTOMATION_SCREENSHOT_DIR=data/browser/screenshots
BROWSER_AUTOMATION_REQUIRE_CONFIRMATION_FOR_RISKY_ACTIONS=true
```

默认只允许访问 `localhost` 和 `127.0.0.1`，更安全。

如果你明确想让它访问外网网页，可以改成：

```properties
BROWSER_AUTOMATION_ALLOW_EXTERNAL_URL=true
```

如果仍想限制域名，可以配置：

```properties
BROWSER_AUTOMATION_ALLOWED_HOSTS=localhost,127.0.0.1,example.com
```

修改后重启主程序。

### 7.4 管理 browser-mcp-sidecar

发布包路径：

```powershell
docker compose -f optional/browser-mcp-sidecar/compose.yaml ps
docker compose -f optional/browser-mcp-sidecar/compose.yaml logs -f --tail 100
docker compose -f optional/browser-mcp-sidecar/compose.yaml restart
docker compose -f optional/browser-mcp-sidecar/compose.yaml down
```

源码路径：

```powershell
docker compose -f browser-mcp-sidecar/compose.yaml ps
docker compose -f browser-mcp-sidecar/compose.yaml logs -f --tail 100
docker compose -f browser-mcp-sidecar/compose.yaml restart
docker compose -f browser-mcp-sidecar/compose.yaml down
```

`down` 会停止容器，但默认保留 Docker 卷里的浏览器 profile 和截图数据。

## 8. 使用小红书舆情采集

小红书舆情功能分两部分：

| 部分 | 作用 |
| --- | --- |
| Java 主程序 | 项目管理、数据入库、分析、报告、管理台 |
| xhs-sidecar | 独立采集服务，负责和外部采集依赖隔离 |

小红书采集默认是关闭的。要使用时，需要：

1. 配置 `xhs-sidecar/.env`。
2. 启动 `xhs-sidecar` Docker 服务。
3. 配置主程序 `.env`。
4. 重启主程序。
5. 在 CLI 输入 `/xhs start` 打开管理台。

### 8.1 准备 xhs-sidecar 的 `.env`

如果你用的是发布包，进入：

```powershell
cd optional/xhs-sidecar
```

如果你用的是源码目录，进入：

```powershell
cd xhs-sidecar
```

新建一个 `.env` 文件。不要把这个文件发给别人，不要提交到 Git。

最少需要：

```properties
XHS_COOKIES=你自己的已授权小红书Cookie
XHS_COLLECTOR_API_KEY=一串足够长的共享密钥
XHS_AUTHOR_HASH_KEY=另一串稳定的哈希密钥
XHS_SIDECAR_WORKER_THREADS=1
XHS_DETAIL_MAX_ATTEMPTS=3
XHS_DETAIL_RETRY_DELAY_MS=800
```

说明：

| 配置 | 说明 |
| --- | --- |
| `XHS_COOKIES` | 已登录小红书账号的 Cookie，过期后需要重新填写 |
| `XHS_COLLECTOR_API_KEY` | sidecar 和 Java 主程序之间的共享密钥，两边必须一致 |
| `XHS_AUTHOR_HASH_KEY` | 作者标识脱敏用的稳定密钥，两边必须一致 |
| `XHS_SIDECAR_WORKER_THREADS` | 建议单账号保持 `1`，避免并发请求过高 |

安全提醒：

```text
不要把 XHS_COOKIES 发到聊天、截图、日志或代码仓库。
只使用你有权限使用的账号和数据。
遵守平台规则和适用法律。
```

### 8.2 启动 xhs-sidecar

在 `xhs-sidecar` 目录内执行：

```powershell
docker compose config --quiet
docker compose build
docker compose up -d
docker compose ps
```

### 8.3 验证 xhs-sidecar

```powershell
Invoke-RestMethod http://127.0.0.1:18081/health
```

如果返回健康状态，说明 sidecar 已经启动。

### 8.4 修改主程序 `.env`

回到 `openclaw-release` 主目录，编辑主程序 `.env`：

```properties
XHS_COLLECTOR_ENABLED=true
XHS_COLLECTOR_BASE_URL=http://127.0.0.1:18081
XHS_COLLECTOR_API_KEY=和xhs-sidecar里相同的共享密钥
XHS_AUTHOR_HASH_KEY=和xhs-sidecar里相同的哈希密钥

XHS_ANALYSIS_ENABLED=true
XHS_ALERT_ENABLED=false
XHS_CONSOLE_ENABLED=true
XHS_CONSOLE_AUTO_OPEN=true
XHS_SCHEDULED_REPORT_ENABLED=true
XHS_REPORT_STORAGE_DIR=data/xhs/reports
```

然后重启主程序。

### 8.5 打开小红书管理台

主程序启动后，在 CLI 输入：

```text
/xhs start
```

默认管理台地址：

```text
http://127.0.0.1:8080/xhs-console/index.html
```

管理台里可以做：

```text
创建项目
配置关键词
立即采集
查看任务状态
查看舆情帖子
查看风险事件
生成日报
下载 Word / XLSX 报告
配置告警规则
```

### 8.6 Cookie 过期怎么办

如果采集任务返回 `AUTH_EXPIRED`，说明小红书登录态过期。

处理步骤：

1. 重新登录小红书。
2. 复制新的完整 Cookie。
3. 修改 `xhs-sidecar/.env` 里的 `XHS_COOKIES`。
4. 重启 sidecar。

发布包路径示例：

```powershell
docker compose --project-directory .\optional\xhs-sidecar -f .\optional\xhs-sidecar\compose.yaml up -d --force-recreate sidecar
Invoke-RestMethod http://127.0.0.1:18081/health
```

源码路径示例：

```powershell
docker compose --project-directory .\xhs-sidecar -f .\xhs-sidecar\compose.yaml up -d --force-recreate sidecar
Invoke-RestMethod http://127.0.0.1:18081/health
```

### 8.7 管理 xhs-sidecar

如果当前就在 `xhs-sidecar` 目录：

```powershell
docker compose ps
docker compose logs -f --tail 100 sidecar
docker compose restart sidecar
docker compose down
```

`docker compose down` 会停止容器，但保留任务状态卷。

只有确定要删除任务状态时，才使用：

```powershell
docker compose down -v
```

## 9. 配置其他常用功能

下面这些功能不一定要一次性全部配置。需要哪个，就配置哪个。

### 9.1 天气和地图

```properties
AMAP_WEATHER_KEY=你的高德Web服务Key
AMAP_WEATHER_BASE_URL=https://restapi.amap.com

AMAP_MAP_KEY=你的高德Web服务Key
AMAP_MAP_BASE_URL=https://restapi.amap.com
AMAP_MAP_STATIC_IMAGE_ENABLED=true
```

测试：

```text
/weather 北京
```

### 9.2 QQ 邮件

```properties
EMAIL_ENABLED=true
EMAIL_PROVIDER=qq
EMAIL_SMTP_HOST=smtp.qq.com
EMAIL_SMTP_PORT=465
EMAIL_SMTP_SSL_ENABLED=true
EMAIL_SMTP_USERNAME=你的QQ邮箱
EMAIL_SMTP_PASSWORD=你的QQ邮箱授权码
EMAIL_FROM=${EMAIL_SMTP_USERNAME}
EMAIL_ALLOWED_RECIPIENTS=
EMAIL_REQUIRE_CONFIRMATION_FOR_NON_WHITELIST=true
EMAIL_PENDING_DRAFT_TTL_MINUTES=10
EMAIL_MAX_BODY_CHARS=8000
```

注意：`EMAIL_SMTP_PASSWORD` 应该填 QQ 邮箱授权码，不是 QQ 登录密码。

### 9.3 ffmpeg 语音转码

如果电脑安装了 ffmpeg：

```properties
AUDIO_FFMPEG_PATH=ffmpeg
AUDIO_FFMPEG_ENABLED=true
```

如果没有安装 ffmpeg，也可以先关闭：

```properties
AUDIO_FFMPEG_ENABLED=false
```

关闭后，部分微信语音格式可能无法识别。

### 9.4 百度网盘

不用百度网盘时保持关闭：

```properties
BAIDU_NETDISK_ENABLED=false
```

需要使用时再改：

```properties
BAIDU_NETDISK_ENABLED=true
BAIDU_NETDISK_APP_ID=
BAIDU_NETDISK_APP_KEY=
BAIDU_NETDISK_OAUTH_CLIENT_ID=
BAIDU_NETDISK_SECRET_KEY=
BAIDU_NETDISK_SIGN_KEY=
BAIDU_NETDISK_REDIRECT_URI=
BAIDU_NETDISK_TOKEN_ENCRYPTION_KEY=
```

### 9.5 浏览器外部网页访问

如果只测试本地网页，保持：

```properties
BROWSER_AUTOMATION_ALLOW_EXTERNAL_URL=false
BROWSER_AUTOMATION_ALLOWED_HOSTS=localhost,127.0.0.1
```

如果要让 Agent 访问外部网站：

```properties
BROWSER_AUTOMATION_ALLOW_EXTERNAL_URL=true
```

更谨慎的做法是限制域名：

```properties
BROWSER_AUTOMATION_ALLOWED_HOSTS=localhost,127.0.0.1,example.com
```

## 10. 启动顺序示例

### 10.1 只用基础聊天和微信

```text
1. 启动 MySQL
2. 创建 openclaw 数据库
3. 复制 .env.example 为 .env
4. 填 MySQL 和模型配置
5. 运行 start.bat
6. 在 CLI 输入 /wechat start
```

### 10.2 使用知识库

```text
1. 启动 MySQL
2. 启动 Qdrant Docker 容器
3. 在 .env 开启 RAG，并配置 Qdrant
4. 运行 start.bat
5. 在微信或 CLI 中使用知识库相关能力
```

### 10.3 使用浏览器自动化

```text
1. 启动 MySQL
2. 启动 browser-mcp-sidecar
3. 在 .env 设置 BROWSER_AUTOMATION_ENABLED=true
4. 运行 start.bat
5. 让 Agent 执行打开网页、读取页面、截图等操作
```

### 10.4 使用小红书舆情

```text
1. 启动 MySQL
2. 配置 optional/xhs-sidecar/.env
3. 启动 xhs-sidecar
4. 在主程序 .env 开启 XHS_COLLECTOR_ENABLED 和 XHS_ANALYSIS_ENABLED
5. 运行 start.bat
6. 在 CLI 输入 /xhs start
7. 在网页管理台创建项目并采集
```

### 10.5 全功能本地启动顺序

```text
1. 启动 Docker Desktop
2. 启动 MySQL
3. 启动 Qdrant
4. 启动 browser-mcp-sidecar
5. 启动 xhs-sidecar
6. 检查所有 health 接口
7. 确认主程序 .env 开关和地址正确
8. 运行 start.bat
9. 在 CLI 输入 /status、/wechat start、/xhs start
```

## 11. 常用端口

| 端口 | 服务 | 说明 |
| --- | --- | --- |
| `8080` | OpenClaw 主程序 | 由 `WECHAT_LOGIN_SERVER_PORT` 控制 |
| `3306` | MySQL | 数据库 |
| `6333` | Qdrant | 知识库向量数据库 |
| `3333` | browser-mcp-sidecar | 浏览器自动化 |
| `18081` | xhs-sidecar | 小红书采集 |

如果端口被占用：

1. 先确认是什么程序占用了端口。
2. 停掉冲突程序，或者修改 `.env` / compose 里的端口。
3. 修改端口后，主程序 `.env` 里的 endpoint 也要同步修改。

## 12. 数据保存在哪里

| 数据 | 保存位置 |
| --- | --- |
| 会话、记忆、工具记录 | MySQL |
| 知识库向量 | Qdrant Docker volume：`qdrant_storage` |
| 生成图片 | `data/` 或 `.env` 中的 `IMAGE_OUTPUT_DIR` |
| 微信图片归档 | `data/wechat/images` |
| 微信文档归档 | `data/wechat/documents` |
| 浏览器截图 | `data/browser/screenshots` 或 sidecar Docker volume |
| 小红书日报 | `data/xhs/reports` |
| 小红书 sidecar 任务状态 | Docker volume：`openclaw-xhs_xhs-jobs` |

不要随便删除 `data/`、MySQL 数据库、Qdrant volume 或 xhs sidecar volume，除非你确定不再需要这些数据。

## 13. 安全注意事项

请务必遵守：

1. 不要把 `.env` 发给别人。
2. 不要把 `.env` 提交到 Git。
3. 不要把 API Key、邮箱授权码、小红书 Cookie、百度网盘密钥截图发出去。
4. 小红书采集只用于你有权限处理的数据。
5. 浏览器自动化默认限制访问本地地址，需要访问外网时再显式开启。
6. 生产环境不要使用示例密钥，例如 `openclaw-local-only`。
7. 对外暴露服务时，不要直接把 `127.0.0.1` 改成 `0.0.0.0`，除非你知道网络和鉴权风险。

## 14. 常见问题排查

### 14.1 `java` 命令找不到

现象：

```text
java 不是内部或外部命令
```

原因：

```text
没有安装 JDK，或者 Java 环境变量没有配置好。
```

处理：

```text
安装 JDK 17。
安装后关闭当前 PowerShell，重新打开，再执行 java -version。
```

### 14.2 主程序启动后立刻退出

先看控制台最后几行日志。

常见原因：

| 日志关键词 | 可能原因 |
| --- | --- |
| `Access denied for user` | MySQL 用户名或密码错误 |
| `Unknown database 'openclaw'` | 没有创建 `openclaw` 数据库 |
| `Communications link failure` | MySQL 没启动，或端口不对 |
| `Flyway` | 数据库迁移失败 |
| `Address already in use` | 端口被占用 |

### 14.3 MySQL 连接失败

检查 `.env`：

```properties
MYSQL_URL=jdbc:mysql://127.0.0.1:3306/openclaw?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai
MYSQL_USERNAME=root
MYSQL_PASSWORD=你的MySQL密码
```

确认：

```text
MySQL 服务已经启动
数据库 openclaw 已经创建
用户名密码正确
3306 端口没有改过
```

### 14.4 `/wechat start` 没有二维码或登录页打不开

检查：

```text
主程序是否还在运行
WECHAT_LOGIN_SERVER_PORT 是否是 8080
控制台是否输出登录页地址
浏览器访问的地址是否包含正确 session 参数
```

手动访问静态页面不要省略 session 参数。应该使用 `/wechat start` 输出的完整地址。

### 14.5 模型调用失败，出现 401 或 403

通常是 Key 或服务地址不对。

检查：

```properties
DASHSCOPE_API_KEY=你的模型Key
DASHSCOPE_BASE_URL=你的模型服务地址
DASHSCOPE_CHAT_MODEL=模型名
```

如果图片、语音、Embedding 使用不同地址，还要检查：

```properties
DASHSCOPE_IMAGE_BASE_URL=
DASHSCOPE_VOICE_BASE_URL=
DASHSCOPE_TTS_BASE_URL=
DASHSCOPE_EMBEDDING_BASE_URL=
```

### 14.6 Qdrant 连不上

检查容器：

```powershell
docker ps
```

检查页面：

```text
http://localhost:6333/dashboard
```

检查 `.env`：

```properties
QDRANT_HOST=localhost
QDRANT_HTTP_PORT=6333
```

如果 Docker 容器没启动：

```powershell
docker start openclaw-qdrant
```

### 14.7 browser-mcp-sidecar 连不上

检查服务：

```powershell
Invoke-RestMethod http://127.0.0.1:3333/health
```

检查主程序 `.env`：

```properties
BROWSER_AUTOMATION_ENABLED=true
BROWSER_AUTOMATION_MCP_ENDPOINT=http://127.0.0.1:3333/mcp
```

查看日志：

```powershell
docker compose -f optional/browser-mcp-sidecar/compose.yaml logs -f --tail 100
```

源码目录改用：

```powershell
docker compose -f browser-mcp-sidecar/compose.yaml logs -f --tail 100
```

### 14.8 xhs-sidecar 连不上

检查服务：

```powershell
Invoke-RestMethod http://127.0.0.1:18081/health
```

检查主程序 `.env`：

```properties
XHS_COLLECTOR_ENABLED=true
XHS_COLLECTOR_BASE_URL=http://127.0.0.1:18081
XHS_COLLECTOR_API_KEY=和sidecar一致
XHS_AUTHOR_HASH_KEY=和sidecar一致
```

检查 `xhs-sidecar/.env`：

```properties
XHS_COOKIES=不能空
XHS_COLLECTOR_API_KEY=和主程序一致
XHS_AUTHOR_HASH_KEY=和主程序一致
```

查看日志：

```powershell
docker compose logs -f --tail 100 sidecar
```

这个命令需要在 `xhs-sidecar` 目录下执行。

### 14.9 小红书返回 `AUTH_EXPIRED`

说明 Cookie 过期。

处理：

```text
重新登录小红书
复制新的 Cookie
更新 xhs-sidecar/.env
重启 xhs-sidecar
重新提交采集任务
```

### 14.10 端口被占用

Windows 查看端口占用：

```powershell
netstat -ano | findstr :8080
```

如果 8080 被占用，可以改主程序 `.env`：

```properties
WECHAT_LOGIN_SERVER_PORT=8081
```

然后访问地址也要改成：

```text
http://127.0.0.1:8081
```

## 15. 新手启动检查清单

第一次运行前逐项确认：

```text
[ ] 已安装 JDK 17，java -version 正常
[ ] 已安装并启动 MySQL
[ ] 已创建 openclaw 数据库
[ ] 已复制 .env.example 为 .env
[ ] .env 中 MYSQL_PASSWORD 正确
[ ] .env 中 WECHAT_LOGIN_SERVER_PORT=8080
[ ] .env 中模型 Key 和 Base URL 已填写
[ ] 已运行 start.bat 或 java -jar openclaw-cli.jar
[ ] CLI 中 /help 有响应
[ ] CLI 中 /status 有响应
[ ] /wechat start 能输出登录地址或二维码
```

如果要使用 Docker 增强功能，再确认：

```text
[ ] Docker Desktop 已启动
[ ] docker --version 正常
[ ] docker compose version 正常
[ ] 需要知识库时，Qdrant 已启动，6333 可访问
[ ] 需要浏览器自动化时，3333 health 正常
[ ] 需要小红书采集时，18081 health 正常
[ ] 修改 .env 后已经重启主程序
```

## 16. 给源码用户：如何自己打 Jar

如果你拿到的不是发布包，而是完整源码，需要先打包。

确认 Maven 可用：

```powershell
mvn -version
```

打包：

```powershell
mvn clean package -DskipTests
```

生成 Jar：

```text
target/openclaw-cli-0.0.1-SNAPSHOT.jar
```

运行：

```powershell
java -Dfile.encoding=UTF-8 -jar target/openclaw-cli-0.0.1-SNAPSHOT.jar
```

如果要制作发布包，可以把 Jar 复制出来并重命名：

```text
openclaw-cli.jar
```

同时带上：

```text
.env.example
README-RUN.md
start.bat
start.sh
optional/browser-mcp-sidecar/
optional/xhs-sidecar/
```

## 17. 推荐给最终使用者的一句话流程

最短流程：

```text
解压 openclaw-release.zip
安装 JDK 17 和 MySQL
创建 openclaw 数据库
复制 .env.example 为 .env
填写 MySQL 密码和模型 Key
双击 start.bat
在 CLI 输入 /wechat start
扫码登录后开始使用
```

需要增强功能：

```text
知识库：启动 Qdrant，并配置 QDRANT_*
浏览器自动化：启动 browser-mcp-sidecar，并配置 BROWSER_AUTOMATION_*
小红书采集：配置并启动 xhs-sidecar，再配置 XHS_*
邮件、地图、图片、语音：按需填写对应 Key
```
