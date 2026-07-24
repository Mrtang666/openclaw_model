# OpenClaw 协作者本地启动指南

这份文档给新加入项目的协作者使用，目标是：拉到代码后，能够在本机把 OpenClaw 微信端 Agent 跑起来，并能正常使用 MySQL 记忆、Qdrant 知识库、网页搜索/网页阅读、文件、图片和语音等工具。

如果只是看代码，不需要完整启动微信端，可以只完成 JDK、Maven 和 `.env` 的基础配置；如果要完整体验微信端 Agent，需要完成本文所有步骤。

## 1. 当前项目依赖哪些外部服务

本项目不是一个纯 Spring Boot 单体应用，除了 Java 代码，还依赖几类外部服务：

| 模块 | 用途 | 是否必须 |
| --- | --- | --- |
| JDK 17 | 编译和运行 Spring Boot 项目 | 必须 |
| Maven | 依赖管理、测试、启动项目 | 必须 |
| MySQL 8.x | 保存微信用户、上下文记忆、工具日志、文件/图片/知识库元数据 | 必须 |
| Flyway | 项目启动时自动建表和升级表结构 | 自动随项目运行 |
| Qdrant | 保存知识库向量，用于语义检索 | 使用知识库时必须 |
| 阿里百炼 DashScope | 文本大模型、Embedding、图片、语音、网页搜索等模型服务 | 大部分 Agent 能力必须 |
| 高德 Web 服务 Key | 天气和地图工具 | 使用天气/地图时必须 |
| 微信 iLink SDK | 微信登录、接收消息、发送消息 | 使用微信端时必须 |
| ffmpeg | 微信语音格式转换 | 使用语音识别时建议安装 |

## 2. 拉取项目后先检查环境

在项目根目录执行：

```powershell
java -version
mvn -version
```

要求：

- Java 版本为 17。
- Maven 可以正常执行。
- 当前目录是项目根目录，例如：`C:\Users\Lenovo\Desktop\openclaw_model`。

## 3. 准备 MySQL 数据库

项目使用 MySQL 保存微信端长期记忆、会话、工具日志、文件记录、图片记录、知识库元数据等内容。

首次运行前，在项目根目录执行：

```powershell
mysql -u root -p < docs/sql/create_database.sql
```

这个脚本只创建两个空数据库：

- `openclaw`：本地开发运行库。
- `openclaw_test`：测试库，避免测试污染开发数据。

业务表不要手动建。项目启动时 Flyway 会自动执行：

```text
src/main/resources/db/migration/
  V1__create_wechat_memory_tables.sql
  V2__create_wechat_document_tables.sql
  V3__create_wechat_image_tables.sql
  V4__create_wechat_knowledge_tables.sql
  V5__create_wechat_web_tables.sql
```

如果协作者本机没有 `mysql` 命令，也可以登录 MySQL 客户端后手动执行：

```sql
CREATE DATABASE IF NOT EXISTS openclaw
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

CREATE DATABASE IF NOT EXISTS openclaw_test
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;
```

## 4. 启动 Qdrant 向量数据库

知识库功能需要 Qdrant。推荐本地用 Docker 启动：

```powershell
docker run -d `
  --name openclaw-qdrant `
  -p 6333:6333 `
  -p 6334:6334 `
  -v qdrant_storage:/qdrant/storage `
  qdrant/qdrant
```

启动后访问：

```text
http://localhost:6333/dashboard
```

如果容器已经存在但没有启动：

```powershell
docker start openclaw-qdrant
```

如果需要查看运行状态：

```powershell
docker ps
```

说明：

- Qdrant 保存的是知识库向量，不替代 MySQL。
- MySQL 保存文档元数据，Qdrant 保存向量检索数据。
- `QDRANT_VECTOR_SIZE=0` 表示第一次入库时根据 Embedding 返回维度自动创建 collection。

## 5. 配置 `.env`

复制配置模板：

```powershell
copy .env.example .env
```

然后按自己的本机环境修改 `.env`。

最小必填配置如下：

```properties
# MySQL
MYSQL_URL=jdbc:mysql://127.0.0.1:3306/openclaw?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai
MYSQL_USERNAME=root
MYSQL_PASSWORD=你的MySQL密码
FLYWAY_ENABLED=true

# Agent 模式
AGENT_TOOL_CALLING_MODE=function-calling
AGENT_TOOL_CALLING_MAX_LOOP_ROUNDS=5

# 阿里百炼
DASHSCOPE_API_KEY=你的百炼APIKey
DASHSCOPE_BASE_URL=你的百炼文本模型Host，例如 https://xxx.cn-beijing.maas.aliyuncs.com/compatible-mode/v1
DASHSCOPE_CHAT_MODEL=qwen3.7-max-2026-06-08
DASHSCOPE_ENABLE_THINKING=true

# Embedding / Qdrant
QDRANT_HOST=localhost
QDRANT_HTTP_PORT=6333
QDRANT_API_KEY=
QDRANT_COLLECTION=openclaw_knowledge
QDRANT_DISTANCE=Cosine
QDRANT_VECTOR_SIZE=0
KNOWLEDGE_CHUNK_SIZE=800
KNOWLEDGE_CHUNK_OVERLAP=120
KNOWLEDGE_TOP_K=5
KNOWLEDGE_MAX_CONTEXT_CHARS=6000
KNOWLEDGE_MIN_SCORE=0.2
DASHSCOPE_EMBEDDING_MODEL=text-embedding-v4
DASHSCOPE_EMBEDDING_BASE_URL=${DASHSCOPE_BASE_URL}
DASHSCOPE_EMBEDDING_API_KEY=${DASHSCOPE_API_KEY}
```

如果要使用网页搜索，还需要：

```properties
WEB_READ_TIMEOUT_MS=10000
WEB_READ_MAX_BYTES=2097152
WEB_CACHE_TTL_HOURS=24
WEB_SEARCH_PROVIDER=bailian-mcp
WEB_SEARCH_ENDPOINT=https://dashscope.aliyuncs.com/api/v1/mcps/WebSearch/mcp
WEB_SEARCH_API_KEY=${DASHSCOPE_API_KEY}
WEB_SEARCH_LIMIT=5
```

如果要使用天气和地图，还需要：

```properties
AMAP_WEATHER_KEY=你的高德Web服务Key
AMAP_WEATHER_BASE_URL=https://restapi.amap.com
AMAP_MAP_KEY=
AMAP_MAP_BASE_URL=https://restapi.amap.com
```

如果 `AMAP_MAP_KEY` 留空，项目会默认复用 `AMAP_WEATHER_KEY`。

如果要使用图片生成、图片理解，还需要：

```properties
DASHSCOPE_VISION_MODEL=qwen3.7-plus
DASHSCOPE_IMAGE_BASE_URL=你的百炼图片服务Host，例如 https://xxx.cn-beijing.maas.aliyuncs.com/api/v1
DASHSCOPE_IMAGE_MODEL=qwen-image-2.0-pro
DASHSCOPE_IMAGE_SIZE=1024*1024
DASHSCOPE_IMAGE_WATERMARK=false
DASHSCOPE_IMAGE_PROMPT_EXTEND=true
WECHAT_IMAGE_STORAGE_DIR=data/wechat/images
WECHAT_IMAGE_BATCH_SIZE=5
```

如果要使用语音识别和语音合成，还需要：

```properties
DASHSCOPE_VOICE_MODEL=qwen3-asr-flash
DASHSCOPE_VOICE_MAX_POLL_ATTEMPTS=20
DASHSCOPE_VOICE_POLL_INTERVAL_MS=1000
DASHSCOPE_TTS_BASE_URL=https://dashscope.aliyuncs.com/api/v1
DASHSCOPE_TTS_MODEL=qwen3-tts-flash
DASHSCOPE_TTS_VOICE=Cherry
DASHSCOPE_TTS_FORMAT=mp3
VOICE_TTS_MAX_WECHAT_DURATION_MS=58000
AUDIO_FFMPEG_PATH=ffmpeg
AUDIO_FFMPEG_ENABLED=true
```

注意：

- `.env` 里放真实密钥，不要提交到 Git。
- `.env.example` 只放示例和占位值。
- Host、API Key、数据库密码都属于个人配置，不要写死在 Java 代码里。

## 6. 微信 iLink SDK 准备

项目已经在 `pom.xml` 中依赖：

```xml
<dependency>
    <groupId>io.github.lith0924</groupId>
    <artifactId>wechat-ilink-sdk</artifactId>
    <version>2.3.3</version>
</dependency>
```

如果 Maven 能直接下载这个依赖，一般不需要额外操作。

如果你的本地 Maven 拉不到依赖，或者你使用的是本地修改版 SDK，需要先在 SDK 项目目录执行：

```powershell
cd C:\Users\Lenovo\Desktop\wechat-ilink-sdk-java
mvn clean install -DskipTests "-Dmaven.compiler.source=8" "-Dmaven.compiler.target=8" "-Dmaven.compiler.release=8"
cd C:\Users\Lenovo\Desktop\openclaw_model
```

## 7. 启动项目

确认 MySQL 和 Qdrant 都已启动后，在项目根目录执行：

```powershell
mvn spring-boot:run
```

启动时需要重点观察几类日志：

```text
OpenClaw 配置检查
Flyway migration
Qdrant
WechatBotService
iLink
Function Calling Agent Loop
```

如果看到数据库连接失败、Flyway 报错、Qdrant 连接失败、DashScope 401/403，先不要继续扫码，优先修配置。

## 8. 微信端启动方式

项目启动后，在 CLI 中输入：

```text
/wechat start
```

然后根据控制台或登录页面提示扫码登录。

常用命令：

```text
/help
/status
/wechat start
/wechat status
/wechat stop
```

微信端验证建议按这个顺序：

```text
你好
帮我查一下杭州今天天气怎么样
帮我搜索一下 Qdrant Java 接入方式
看看第二个网页
把刚才网页内容保存到知识库
根据我保存的资料，总结一下 Qdrant 的接入步骤
```

## 9. 本地验证命令

拉代码后，建议先跑测试：

```powershell
mvn test
```

如果只想验证知识库和网页工具：

```powershell
mvn -q "-Dtest=WebResourceContextServiceTests,KnowledgeAndWebWechatToolTests,KnowledgeIngestionAndSearchServiceTests,ApplicationContextTests" test
```

检查补丁格式：

```powershell
git diff --check
```

Windows 下如果只看到 `LF will be replaced by CRLF`，一般是换行提示，不是代码错误。

## 10. 新增工具时的协作规范

后续多人协作新增工具时，建议遵守下面的规则。

### 10.1 不改主流程

除非确实要调整 Agent 框架，否则不要直接改：

```text
WechatConversationService
FunctionCallingAgentLoop
DashScopeFunctionCallingClient
WechatToolRegistry
```

普通业务能力应该做成一个新的 `WechatTool`。

### 10.2 工具统一放在微信工具层

新增工具一般放在：

```text
src/main/java/com/example/spring/wechat/conversation/tools/
```

每个工具要实现：

- `name()`：工具名，给大模型 Function Calling 使用。
- `description()`：工具描述，告诉大模型什么时候调用。
- `parameters()`：结构化参数定义。
- `capability()`：能力边界说明。
- `execute()`：实际执行逻辑。

### 10.3 工具自己的业务逻辑放到独立包

工具类不要写太厚。推荐结构：

```text
wechat/conversation/tools/XxxWechatTool.java      # Function Calling 工具入口
wechat/xxx/config/                                # 配置
wechat/xxx/client/                                # 外部 API 客户端
wechat/xxx/service/                               # 业务服务
wechat/xxx/model/                                 # 请求/响应模型
wechat/xxx/repository/                            # 数据库访问，如果需要
```

例如知识库工具就是：

```text
wechat/conversation/tools/KnowledgeQueryWechatTool.java
wechat/knowledge/config/
wechat/knowledge/client/
wechat/knowledge/service/
wechat/knowledge/repository/
wechat/knowledge/model/
```

### 10.4 新增数据库表必须写 Flyway 脚本

如果工具需要新表，必须新增迁移脚本：

```text
src/main/resources/db/migration/V6__xxx.sql
```

不要修改已经执行过的旧迁移脚本，例如 `V1`、`V2`、`V3`、`V4`、`V5`。

### 10.5 新增配置必须写进 `.env.example`

如果新增 API Key、Host、模型名、超时时间、开关配置，需要同步修改：

```text
.env.example
src/main/resources/application.properties
README.md 或 docs/COLLABORATOR_BOOTSTRAP.md
```

真实密钥只写 `.env`，不要提交。

### 10.6 新增工具必须配测试

至少补充：

- 工具参数测试。
- 工具成功执行测试。
- 工具失败提示测试。
- 如果有 Spring Bean，补 `ApplicationContextTests` 覆盖启动。

## 11. 常见问题排查

### 11.1 Unknown database 'openclaw'

原因：MySQL 里还没有创建 `openclaw`。

解决：

```powershell
mysql -u root -p < docs/sql/create_database.sql
```

### 11.2 表不存在

原因：Flyway 没有执行，或者 `.env` 中关闭了 Flyway。

检查：

```properties
FLYWAY_ENABLED=true
```

然后重启项目。

### 11.3 知识库检索不到内容

优先检查：

- Qdrant 容器是否启动。
- `.env` 中 `QDRANT_HOST`、`QDRANT_HTTP_PORT` 是否正确。
- `DASHSCOPE_EMBEDDING_API_KEY` 是否有效。
- 是否真的执行过“保存/记住/加入知识库”。
- `KNOWLEDGE_MIN_SCORE` 是否设置过高。

### 11.4 网页搜索不可用

优先检查：

- `WEB_SEARCH_PROVIDER=bailian-mcp`
- `WEB_SEARCH_ENDPOINT=https://dashscope.aliyuncs.com/api/v1/mcps/WebSearch/mcp`
- `WEB_SEARCH_API_KEY` 是否有效。
- 百炼控制台是否已经开通 WebSearch。

如果 MCP 临时失败，项目会尝试 fallback 到 Chat Completions 联网搜索。

### 11.5 DashScope 401 / 403

常见原因：

- API Key 错误。
- Host 写错。
- 模型没有权限。
- 图片、语音、Embedding 使用了不同服务地址，但 `.env` 没分开配置。

处理方式：

- 确认 `DASHSCOPE_API_KEY`。
- 确认 `DASHSCOPE_BASE_URL`。
- 确认 `DASHSCOPE_IMAGE_BASE_URL`。
- 确认 `DASHSCOPE_TTS_BASE_URL`。
- 确认对应模型已经在百炼开通。

### 11.6 微信端没有回复

按日志顺序排查：

```text
iLink 收到消息
微信收到消息
微信消息进入处理队列
Function Calling Agent Loop 开始
工具调用日志
微信回复发送完成
```

如果第一条日志都没有，说明 iLink 接收链路有问题。  
如果 Function Calling 开始后没有回复，重点看模型调用和工具调用异常。  
如果显示回复发送失败，重点看 iLink 发送文件、图片或语音的日志。

## 12. 协作者启动检查清单

新成员可以按这个清单自查：

- [ ] JDK 17 可用。
- [ ] Maven 可用。
- [ ] MySQL 已启动。
- [ ] 已创建 `openclaw` 和 `openclaw_test`。
- [ ] Qdrant 已启动，`http://localhost:6333/dashboard` 可访问。
- [ ] 已复制 `.env.example` 为 `.env`。
- [ ] `.env` 中 MySQL 配置正确。
- [ ] `.env` 中 DashScope API Key 和 Host 正确。
- [ ] 如果使用知识库，Embedding 配置正确。
- [ ] 如果使用网页搜索，WebSearch MCP 已开通。
- [ ] 如果使用天气/地图，高德 Key 已配置。
- [ ] `mvn test` 可以通过。
- [ ] `mvn spring-boot:run` 可以启动。
- [ ] `/wechat start` 可以扫码登录。
- [ ] 微信发送“你好”可以收到回复。
- [ ] 微信发送搜索、知识库、天气等请求可以正常调用工具。

