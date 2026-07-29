# 照护协作管理台前端原型

这是医疗照护 Agent 的第一版前端原型，独立放在 `frontend/medical-console`，不影响现有 Spring 后端和微信登录页。

## 页面路由

- `#/bind/caregiver`：家属绑定患者页
- `#/bind/doctor`：医生绑定患者页
- `#/caregiver/status`：家属患者状态页
- `#/doctor/patients`：医生患者切换页
- `#/doctor/detail`：医生患者详情页
- `#/doctor/alerts-review`：告警中心和方案审核页

## 当前实现

- 使用本地 Mock 数据展示页面和交互。
- 医生端患者切换会同步影响详情页。
- 绑定页面只做前端交互演示，真实绑定需要后端 token 和权限校验。
- 告警处理、方案确认按钮目前是页面原型，后续接入真实接口。

## 后续接口建议

- 家属端：`GET /api/care/v1/family/patients`
- 家属端：`GET /api/care/v1/family/patients/{patientId}/status`
- 家属端：`GET /api/care/v1/family/patients/{patientId}/tasks`
- 家属端：`GET /api/care/v1/family/patients/{patientId}/alerts`
- 家属端：`GET /api/care/v1/family/patients/{patientId}/checkins`
- 医生端：`GET /api/care/v1/clinical/patients`
- 医生端：`GET /api/care/v1/clinical/patients/{patientId}/status`
- 医生端：`POST /api/care/v1/clinical/plans/{planId}/review`
- 医生端：`POST /api/care/v1/clinical/plans/{planId}/activate`
- 患者端：`POST /api/care/v1/patient/access-grants`

前端只负责展示和交互，患者关系、身份权限、敏感数据访问必须由后端校验。
