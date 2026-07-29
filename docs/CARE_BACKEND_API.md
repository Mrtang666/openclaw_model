# 患者照护协同后端 API

## 1. 当前交付范围

本阶段提供患者端、家属端和医生/医院端共用的后端基础能力：

- 微信身份与角色初始化；
- Bearer 会话认证；
- 患者向家属或医护人员授权；
- 患者可信记忆记录与确认；
- 每日签到；
- 跌倒、迷路和明确紧急求助的确定性规则；
- 安全告警查询、确认和处理；
- 主动微信告警通知、失败重试和幂等控制；
- 带版本审核的照护计划；
- 每日、每周和一次性任务生成；
- 任务完成、延后、超时及家属升级提醒；
- 患者状态摘要；
- 敏感访问审计。

本阶段不提供诊断、处方、药量调整、医院 HIS 接入、设备定位和前端页面。

## 2. 配置

在本地 `.env` 中配置：

```properties
CARE_BOOTSTRAP_KEY=使用高强度随机值
CARE_SESSION_TTL_HOURS=12
CARE_NOTIFICATION_ENABLED=true
CARE_TASK_ENABLED=true
CARE_TASK_POLL_INTERVAL_MS=60000
CARE_TASK_GENERATION_HORIZON_DAYS=1
CARE_TASK_MAX_POSTPONE_MINUTES=1440
```

`CARE_BOOTSTRAP_KEY` 为空时，账号初始化接口不可用。密钥不得提交到仓库或写入日志。

## 3. 数据库迁移

```text
V13__create_medical_identity_tables.sql
V14__create_medical_care_record_tables.sql
V15__create_medical_alert_notification_tables.sql
V16__create_medical_care_plan_task_tables.sql
```

应用启动时由 Flyway 自动执行。不要手工修改已经执行过的迁移文件。

## 4. 通用协议

API 前缀：

```text
/api/care/v1
```

除初始化接口外，请求需要携带：

```http
Authorization: Bearer <access-token>
X-Request-Id: <可选的请求追踪编号>
```

统一响应：

```json
{
  "code": "OK",
  "message": "success",
  "data": {},
  "traceId": "request-id",
  "timestamp": "2026-07-29T10:00:00Z"
}
```

常见错误码：

```text
INVALID_ARGUMENT
UNAUTHORIZED
FORBIDDEN
NOT_FOUND
CONFLICT
CONFIGURATION_ERROR
INTERNAL_ERROR
```

## 5. 账号初始化

开发和联调阶段通过受保护的初始化接口创建患者、家属或医护账号：

```http
POST /api/care/v1/bootstrap/users
X-Care-Bootstrap-Key: <bootstrap-key>
Content-Type: application/json
```

```json
{
  "connectionId": "wechat-connection-id",
  "fromUserId": "wechat-user-id",
  "displayName": "测试患者",
  "role": "PATIENT"
}
```

支持角色：

```text
PATIENT
CAREGIVER
FAMILY
DOCTOR
NURSE
THERAPIST
DIETITIAN
ADMIN
```

响应中的 `accessToken` 只返回一次，数据库只保存 SHA-256 摘要。

## 6. 患者端

```text
GET  /api/care/v1/patient/status
POST /api/care/v1/patient/memories
GET  /api/care/v1/patient/memories
POST /api/care/v1/patient/checkins
GET  /api/care/v1/patient/checkins
GET  /api/care/v1/patient/alerts
POST /api/care/v1/patient/access-grants
GET  /api/care/v1/patient/plans
GET  /api/care/v1/patient/plans/{planId}
GET  /api/care/v1/patient/tasks
POST /api/care/v1/patient/tasks/{taskId}/complete
POST /api/care/v1/patient/tasks/{taskId}/postpone
```

患者通过 `access-grants` 使用家属或医护人员的 `userCode` 建立关系。未明确传入权限时，后端按关系角色授予默认权限，同时写入患者同意记录。

记忆可见范围：

```text
PATIENT_ONLY
CARE_TEAM
CLINICAL
```

## 7. 家属端

```text
GET   /api/care/v1/family/patients
GET   /api/care/v1/family/patients/{patientId}/status
GET   /api/care/v1/family/patients/{patientId}/memories
PATCH /api/care/v1/family/memories/{memoryId}/confirmation
GET   /api/care/v1/family/patients/{patientId}/checkins
GET   /api/care/v1/family/patients/{patientId}/alerts
POST  /api/care/v1/family/alerts/{alertId}/acknowledge
POST  /api/care/v1/family/alerts/{alertId}/resolve
GET   /api/care/v1/family/patients/{patientId}/plans
GET   /api/care/v1/family/plans/{planId}
POST  /api/care/v1/family/patients/{patientId}/plans
POST  /api/care/v1/family/plans/{planId}/revisions
POST  /api/care/v1/family/plans/{planId}/submit
GET   /api/care/v1/family/patients/{patientId}/tasks
POST  /api/care/v1/family/tasks/{taskId}/complete
POST  /api/care/v1/family/tasks/{taskId}/postpone
```

家属只能访问患者已经授权的数据。确认、修正记忆和处理告警时必须提交当前 `version`，版本不一致返回 `CONFLICT`。

## 8. 医生/医院端

```text
GET   /api/care/v1/clinical/patients
GET   /api/care/v1/clinical/patients/{patientId}/status
GET   /api/care/v1/clinical/patients/{patientId}/memories
PATCH /api/care/v1/clinical/memories/{memoryId}/confirmation
GET   /api/care/v1/clinical/patients/{patientId}/checkins
GET   /api/care/v1/clinical/patients/{patientId}/alerts
POST  /api/care/v1/clinical/alerts/{alertId}/acknowledge
POST  /api/care/v1/clinical/alerts/{alertId}/resolve
GET   /api/care/v1/clinical/patients/{patientId}/plans
GET   /api/care/v1/clinical/plans/{planId}
POST  /api/care/v1/clinical/patients/{patientId}/plans
POST  /api/care/v1/clinical/plans/{planId}/revisions
POST  /api/care/v1/clinical/plans/{planId}/submit
POST  /api/care/v1/clinical/plans/{planId}/review
POST  /api/care/v1/clinical/plans/{planId}/activate
POST  /api/care/v1/clinical/plans/{planId}/pause
POST  /api/care/v1/clinical/plans/{planId}/resume
POST  /api/care/v1/clinical/plans/{planId}/complete
GET   /api/care/v1/clinical/patients/{patientId}/tasks
POST  /api/care/v1/clinical/tasks/{taskId}/complete
POST  /api/care/v1/clinical/tasks/{taskId}/postpone
```

医护角色包括 `DOCTOR`、`NURSE`、`THERAPIST` 和 `DIETITIAN`。机构、机构成员和患者机构关系表已经建立，机构管理 API 留到后续阶段。

## 9. 照护计划与任务

计划状态按以下流程变化：

```text
DRAFT → WAITING_REVIEW → APPROVED → ACTIVE → PAUSED → ACTIVE
   ↑           │                                  └→ COMPLETED
   └── REJECT ─┘
```

计划被驳回后通过 `revisions` 创建不可变的新版本，旧版本和已生成的任务仍保留。所有计划在当前阶段都需要医护人员审核：

- `MEDICATION` 只允许医生审核；
- `NUTRITION` 允许医生或营养师审核；
- `REHABILITATION` 允许医生或康复师审核；
- 其他计划允许有效医护角色审核。

计划支持 `MEDICATION`、`NUTRITION`、`REHABILITATION`、`COGNITIVE_TRAINING`、`DAILY_CHECKIN`、`SLEEP_ROUTINE`、`FOLLOW_UP`、`CAREGIVER_SHIFT` 和 `CUSTOM`。

任务模板支持：

```text
ONCE
DAILY
WEEKLY
```

`WEEKLY` 的 `dayOfWeek` 使用 ISO 编号：周一为 1，周日为 7。任务到期后先通知患者，超过宽限期标记为 `OVERDUE`，超过升级时间后通知拥有 `PATIENT_TASK_READ` 权限的照护人。通知正文不携带药名、病情或计划指令。

家属默认拥有计划和任务读取权限以及代办权限，但默认不能制定计划。患者可以显式授予 `PATIENT_PLAN_MANAGE`，允许特定家属起草和修订计划；计划仍需医护审核后才能生效。

## 10. 安全告警

第一阶段只使用确定性规则：

```text
FALL_REPORTED
POSSIBLE_WANDERING
EXPLICIT_DISTRESS
```

告警状态：

```text
OPEN → ACKNOWLEDGED → RESOLVED
                    → FALSE_ALARM
```

告警通知只发送告警等级和编号，不在微信通知正文中发送完整患者健康内容。

## 11. 医疗边界

- 模型不得生成诊断结论；
- 系统不得自动调整药物或治疗方案；
- 模糊模型判断不能直接创建紧急告警；
- 所有非患者本人访问均需要有效关系权限；
- 查看、授权、确认和处理均写入审计日志；
- 测试使用虚构数据，不使用真实病历和联系方式。
