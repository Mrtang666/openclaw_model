# OpenClaw 当前项目结构

> 本文按当前工作区的代码组织说明模块边界。完整功能、流程图、工具清单和数据模型见 [PROJECT_FEATURES_AND_FLOWS.md](PROJECT_FEATURES_AND_FLOWS.md)。

## 根目录

```text
openclaw_model/
├─ src/                         Spring Boot 主程序、资源和测试
├─ frontend/medical-console/    患者、家属与医护照护控制台静态页面
├─ skills/                      运行时 Agent 业务指令与工具映射
├─ browser-mcp-sidecar/         Docker 浏览器自动化 Sidecar
├─ xhs-sidecar/                 Python 小红书只读采集 Sidecar
├─ docs/                        运行、接口、业务与结构文档
├─ data/                        运行期生成文件与本地归档（不提交）
├─ .env.example                 环境变量模板
└─ pom.xml                      Maven 依赖、Java 17、Spring Boot 3.4.7
```

## 主程序包

```text
src/main/java/com/example/spring/
├─ AgentClawApplication.java    Spring Boot 启动入口与调度启用
├─ agent/                       CLI 对话服务、Agent 目标与人工复盘
├─ chat/                        DashScope/兼容 Chat Completions 客户端
├─ cli/                         ConsoleRunner、命令注册、分发与格式化
├─ config/                      .env 加载、配置检查、密钥脱敏
├─ skill/                       Skill 扫描、解析、工具映射与 Prompt 注入
├─ tool/                        通用工具与 Function Calling / legacy 协议
├─ weather/                     高德天气 Client、模型与服务
├─ wechat/                      微信入口及全部业务域
└─ xhs/                         小红书采集、分析、告警、报告、控制台
```

## 微信模块

```text
wechat/
├─ adapter/                     WechatClient 抽象和 iLink SDK 适配
├─ bot/                         多连接生命周期、用户邮箱队列、接收与发送
├─ login/                       扫码页、连接管理、登录 session
├─ model/                       统一入站消息和媒体模型
├─ conversation/                消息编排、医疗对话模式、Agent 循环、RAG、53 个工具
├─ memory/                      MySQL 会话记忆、摘要、偏好与 fallback
├─ knowledge/                   知识入库、切块、Embedding、Qdrant 检索
├─ document/                    文件检测、解析、分块、归档、文档生成
├─ image/                       图片理解、归档、引用解析和图片生成
├─ voice/                       ASR、TTS、音色偏好与 ffmpeg 转码
├─ web/                         网页阅读、缓存、MCP 搜索与资源上下文
├─ browser/                     Chrome DevTools MCP Client、URL/动作安全策略
├─ email/                       SMTP 文本/附件发送、IMAP 查询和附件下载
├─ reminder/                    提醒任务、收件人绑定、调度、重试和微信推送
├─ report/                      微信长回复 HTML 报告、公共链接和 TTL 清理
├─ netdisk/                     百度网盘 OAuth、MCP、授权与待完成动作
├─ map/                         地点、路线、多点规划、静态地图
├─ taxi/                        起终点确认、报价、订单状态和取消
├─ food/                        外卖地址、菜单、购物车、预结算、订单和支付交接
├─ commerce/                    选购建议与快递物流
├─ travel/                      美团酒旅官方 CLI 适配
├─ news/                        天行新闻查询、会话分页和缓存
├─ payment/                     微信支付回调与退款入口
└─ care/                        医疗照护身份、授权、记录、签到、计划、任务、告警和 API
```

### 对话和工具边界

`WechatConversationService` 负责把记忆、RAG、媒体和用户消息封装为 `FunctionCallingAgentRequest`；`FunctionCallingAgentLoop` 负责模型循环、重复调用抑制、参数校验和工具结果回传；`WechatToolRegistry` 再分发到领域服务。医疗对话模式只影响表达和安全提示，权限仍由照护领域服务强制执行。

```text
WechatBotService
  -> WechatConversationService
  -> FunctionCallingAgentLoop
  -> WechatToolRegistry
  -> <domain service / external client>
```

## 医疗照护模块

```text
wechat/care/
├─ config/                      CARE_* 配置与任务策略
├─ model/                       身份、关系、记录、计划、任务、告警、通知模型
├─ repository/                  医疗身份、记录、计划、任务、告警、通知和审计 SQL
├─ rules/                       确定性安全规则引擎
├─ service/                     授权、可信记忆、签到、计划、任务、报告、通知和会话链接
├─ scheduler/                   照护任务生成/到期、通知投递
└─ web/                         `/api/care/v1` 患者、家属、临床端 REST Controller
```

- `CareAuthorizationService` 与 `CarePermissions`：患者关系、角色、最小权限和有效期校验。
- `CareMemoryService`：保留原始记录及确认状态，防止推测成为可信事实。
- `SafetyRuleEngine`：跌倒、迷路、明确紧急求助等确定性规则。
- `CarePlanService` / `CareTaskService`：版本化计划审核、激活、暂停、完成及任务生成。
- `CareNotificationScheduler`：投递待发送照护通知并进行重试。

医疗前端在 `frontend/medical-console/`；专项说明见 [PATIENT_CARE_COORDINATION_AGENT.md](PATIENT_CARE_COORDINATION_AGENT.md) 和 [CARE_BACKEND_API.md](CARE_BACKEND_API.md)。

## 小红书舆情模块

```text
xhs/
├─ source/                       Sidecar HTTP 契约、Job 轮询、采集状态
├─ ingestion/                    导入、清洗、伪匿名化和采集协调
├─ analysis/                     语义分析、情绪、风险评分、事件聚合
├─ incident/                     事件状态流转和审计
├─ alert/                        规则、事件、投递和微信通知
├─ report/                       日报、DOCX/XLSX 和报表 artifact
├─ schedule/                     定时报表、投递、清理、负面舆情邮件
├─ console/                      `/api/xhs-console` 控制台 API 与授权
├─ repository/                   MySQL 持久化实现
└─ config/                       采集、分析、告警、控制台、报表配置
```

采集逻辑位于 `xhs-sidecar/`，与 Java 主进程隔离，只提供已授权账号的只读搜索采集 Job。

## 资源、迁移与前端

```text
src/main/resources/
├─ application.properties         Spring 配置与环境变量占位
├─ db/migration/                  当前 32 个 Flyway 迁移
└─ static/
   ├─ wechat-login/               扫码登录页面
   └─ xhs-console/                舆情管理台页面
```

| 版本范围 | 域 |
| --- | --- |
| V1-V5 | 会话记忆、文件、图片、知识库和网页缓存 |
| V6-V9 | 百度网盘、支付、打车 |
| V10-V11 | 提醒任务、收件人、提醒增强 |
| V12 | 外卖点餐 |
| V13-V19 | 医疗身份、照护记录、告警、计划、任务与医疗登录 |
| V20-V23 | Agent 目标、步骤、评估与复盘 |
| V25-V33 | 小红书舆情基础、分析、告警、访问链接、定时报表、负面报告邮件 |

`V24` 以及旧编号的小红书迁移不在当前工作区；升级已有环境前应先核对 Flyway 历史表并备份。

## Skills、Sidecar 和测试

`skills/*/SKILL.md` 是模型的业务规则来源，`skill.json` 将 Skill 与 Java 工具映射。SkillManager 只负责加载与注入提示词，不执行工具。

| 组件 | 位置 | 用途 |
| --- | --- | --- |
| Browser MCP Sidecar | `browser-mcp-sidecar/` | Docker 中运行 Chromium，提供受限制的页面打开、读取、点击、输入、截图和重置。 |
| XHS Sidecar | `xhs-sidecar/` | Python 中隔离 Spider_XHS，提供只读搜索采集 Job。 |
| 医疗控制台 | `frontend/medical-console/` | 患者、家属、医生的 Web 控制台静态资源。 |

测试重点位于 `src/test/java/com/example/spring/wechat/` 下的 `conversation/`、`reminder/`、`care/`、`email/`、`report/` 和 `bot/`；小红书测试位于 `src/test/java/com/example/spring/xhs/`。
