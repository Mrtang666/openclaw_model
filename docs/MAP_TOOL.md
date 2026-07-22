# 微信地图工具

## 1. 能力范围

微信地图工具名为 `map_search`，由 Function Calling Agent 自动选择，不需要修改微信会话主流程。

| operation | 用途 | 主要参数 |
| --- | --- | --- |
| `place_search` | 搜索地点并列出候选结果 | `query`、`city` |
| `place_detail` | 查询并介绍具体地点 | `query` 或 `place_id`、`city` |
| `route` | 查询两地距离和交通方案 | `origin`、`destination`、`city`、`transport_mode` |
| `nearby_search` | 查询地点周边美食、景点、商场等 | `center`、`city`、`category`、`radius_meters` |

`transport_mode` 支持 `all`、`driving`、`transit`、`walking`。

`category` 支持 `all`、`food`、`attraction`、`shopping`。

## 2. 包结构

```text
wechat/map/
├─ client/
│  ├─ MapClient.java
│  └─ AmapMapClient.java
├─ model/
│  ├─ MapPlace.java
│  ├─ MapRouteOption.java
│  ├─ MapResult.java
│  └─ ...
└─ service/
   └─ MapService.java

wechat/conversation/tools/
└─ MapWechatTool.java
```

- `AmapMapClient` 只负责高德 Web 服务请求和响应映射。
- `MapService` 负责地点解析、业务结果组合、地图和票务平台链接生成。
- `MapWechatTool` 只负责 Function Calling 参数、能力边界和微信文本格式化。
- `WechatConversationService`、`FunctionCallingAgentLoop`、`WechatBotService` 不需要修改。

## 3. 配置

在本地 `.env` 中配置：

```properties
AMAP_WEATHER_KEY=你的高德Web服务Key
AMAP_MAP_KEY=
AMAP_MAP_BASE_URL=https://restapi.amap.com
```

地图工具默认复用 `AMAP_WEATHER_KEY`。如果团队希望把天气和地图配额隔离，再单独填写 `AMAP_MAP_KEY`。

Key 类型必须是高德开放平台的 Web 服务 Key，并确保账号和配额允许使用地点搜索、路径规划等 Web 服务接口。不要把真实 Key 写入 `application.properties`、测试代码、README 或提交到仓库。

## 4. 票务和推荐边界

- 高德地点数据不等于实时票务库存。
- 工具只为景点生成第三方票务平台搜索入口，不直接下单。
- 是否售票、价格、余票、开放时间和退改规则必须以平台或景区官方页面为准。
- 周边推荐优先按距离展示；评分、人均消费和营业时间可能缺失或更新延迟。
- 路线时间是地图服务估算值，会受到实时路况、候车和换乘影响。

## 5. 测试

```powershell
mvn -q "-Dtest=MapWechatToolTests,AmapMapClientTests,MapServiceTests" test
mvn -q "-Dtest=WechatToolRegistryTests,FunctionCallingToolSchemaConverterTests,FunctionCallingAgentLoopTests" test
mvn -q test
git diff --check
```
