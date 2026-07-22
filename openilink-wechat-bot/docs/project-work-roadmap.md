# openilink-wechat-bot 项目工作路线图

## 当前架构与消息流

```mermaid
flowchart LR
    WX[微信 iLink SDK] --> ENTRY[WechatBot\n入口与生命周期]
    ENTRY --> MEDIA[feature/media\n图片 / 语音 / 视频输入]
    ENTRY --> TEXT[文本请求拆分与顶层路由]
    MEDIA --> TEXT
    TEXT --> INTENT[intent\n意图分类]
    INTENT --> CONTEXT[context\n上下文记忆与追问补全]
    CONTEXT --> GUIDE[feature/guidance\n引导式任务状态]
    GUIDE --> WEATHER[feature/weather\n和风天气]
    GUIDE --> IMAGE[feature/image\n图片与海报]
    GUIDE --> ROUTE[feature/route\n路线规划与绘图]
    GUIDE --> FILE[feature/file\n文件解析与生成]
    TEXT --> CHAT[LocalLLMService\n普通对话]
    TEXT --> VOICE[feature/voice\n语音模式]
    WEATHER --> REPLY[application/ReplyOrchestrator\n回复策略与语音降级]
    IMAGE --> REPLY
    ROUTE --> REPLY
    FILE --> REPLY
    CHAT --> REPLY
    VOICE --> REPLY
    REPLY --> ADAPTER[adapter/wechat\nWechatMessageSender]
    ADAPTER --> WX
```

## 已完成阶段

```mermaid
flowchart TD
    A[基础接入] --> B[统一微信发送适配器]
    B --> C[统一回复编排器]
    C --> D[语音 MP3 / WAV / 文字降级]
    D --> E[上下文记忆与短句追问]
    E --> F[引导式任务状态]
    F --> G[图片与海报处理器]
    G --> H[和风天气处理器]
    H --> I[路线图处理器]
    I --> J[文件任务处理器]
    J --> K[媒体输入处理器]
    K --> L[语音模式与引导结果处理器]
    L --> M[WechatBot 薄入口]
```

## 模块职责

| 模块 | 主要职责 | 当前状态 |
| --- | --- | --- |
| `WechatBot` | 登录、轮询、消息类型分发、顶层文本路由 | 已完成 |
| `adapter/wechat` | 隔离微信 SDK 的发送接口 | 已完成 |
| `application/ReplyOrchestrator` | 文字、MP3、WAV、文字兜底及敏感标记清理 | 已完成 |
| `feature/media` | 图片识别、SILK 语音转写、视频提示 | 已完成 |
| `feature/voice` | 用户级语音回复模式切换 | 已完成 |
| `feature/weather` | 城市/区域/日期解析、和风天气查询、上下文记录 | 已完成 |
| `feature/image` | 比例选择、图片生成、图片发送、海报满意度 | 已完成 |
| `feature/route` | 天气参考、结构化路线规划、路线图发送 | 已完成 |
| `feature/file` | 文件下载、解析、分析、生成与发送 | 已完成 |
| `feature/guidance` | 多轮追问、任务完成后的功能执行 | 已完成 |
| `context` | 最近对话、天气城市和日期上下文 | 已完成 |
| `intent` | 消息意图分类与图片提示词构建 | 已完成 |
| `speech` / `tts` / `voice` | STT、TTS、SILK 编解码和音色 | 已完成 |
| `vision` | 图片理解 | 已完成 |
| `routegen` / `map` | 结构化路线数据、绘图、地图路线补充 | 已完成 |

## 下一阶段工作路线

```mermaid
flowchart TD
    A[结构迁移完成] --> B[配置与密钥治理]
    B --> B1[环境变量优先]
    B --> B2[config.example.properties]
    B --> B3[日志脱敏与密钥轮换]
    B --> C[可测试性建设]
    C --> C1[天气解析单元测试]
    C --> C2[上下文追问测试]
    C --> C3[意图路由测试]
    C --> C4[音频降级测试]
    C --> D[可靠性建设]
    D --> D1[异步任务队列]
    D --> D2[超时与重试策略]
    D --> D3[请求幂等与消息去重]
    D --> D4[临时文件清理]
    D --> E[产品能力增强]
    E --> E1[联网搜索与知识补充]
    E --> E2[视频理解]
    E --> E3[更精确的行政区定位]
    E --> E4[可编辑路线图与海报版本]
    E --> F[发布运维]
    F --> F1[运行配置检查]
    F --> F2[健康检查与指标]
    F --> F3[部署脚本与备份]
```

## 推荐实施顺序

1. 先处理配置安全：移除代码仓库中的明文 API Key，增加环境变量和示例配置。
2. 为 `WeatherHandler`、`ConversationContextService`、`IntentClassifier` 增加单元测试。
3. 将图片、路线图、TTS 和文件生成改为异步任务，避免阻塞微信消息轮询线程。
4. 增加消息去重、超时、有限重试和失败状态记录。
5. 将 `WeatherService`、`LocalLLMService`、`MediaHelper` 等根包服务继续归入 `feature` 或 `infrastructure` 包。
6. 最后补充联网搜索、视频理解和可编辑图片版本等扩展能力。

## 验收标准

- `WechatBot` 只负责生命周期、输入分发和顶层路由，不直接调用外部 API 或微信发送细节。
- 每个 feature 都可以通过构造器注入依赖，并能单独编写测试。
- 任意外部服务失败时，都有明确的用户可见兜底回复。
- 语音回复遵循 SILK、MP3、WAV、文字的降级顺序。
- 天气短句可以结合最近一次城市、区域和日期上下文。
- 图片、路线图和文件生成不会误触发普通聊天功能。
- API Key 不进入日志、提交记录或用户消息。
