# 微信地图工具

## 1. 能力范围

微信地图工具名为 `map_search`，由 Function Calling Agent 自动选择，不需要修改微信会话主流程。

| operation | 用途 | 主要参数 |
| --- | --- | --- |
| `place_search` | 搜索地点并列出候选结果 | `query`、`city` |
| `place_detail` | 查询并介绍具体地点 | `query` 或 `place_id`、`city` |
| `route` | 查询两地距离和交通方案 | `origin`、`destination`、`city`、`transport_mode` |
| `multi_route` | 规划多个地点并生成文本方案和完整路线图 | `locations`、`city`、`transport_mode`、`order_policy` |
| `nearby_search` | 查询地点周边美食、景点、商场等 | `center`、`city`、`category`、`radius_meters` |

`transport_mode` 支持 `all`、`driving`、`transit`、`walking`。

`category` 支持 `all`、`food`、`attraction`、`shopping`。

`multi_route` 一次支持 2 到 12 个地点。`order_policy=preserve` 保持用户顺序，`optimize` 固定起点并近似优化中间地点；可通过 `fixed_end`、`round_trip` 和 `include_map_image` 控制终点、环线与图片输出。

## 2. 包结构

```text
wechat/map/
├─ client/
│  ├─ MapClient.java
│  ├─ AmapMapClient.java
│  └─ AmapStaticMapClient.java
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

- `AmapMapClient` 负责地点和路径规划请求，并解析路线步骤与折线坐标。
- `AmapStaticMapClient` 使用真实地点坐标和路线折线生成静态路线图，并在图片下方添加地点图例；高德静态底图失败时会自动生成本地路线示意图。
- `MapService` 负责地点消歧、顺序优化、逐段路线汇总、地图和票务平台链接生成。
- `MapWechatTool` 负责 Function Calling 参数、能力边界、微信文本与图片回复格式化。
- `FunctionCallingAgentLoop` 会保留地图工具返回的文本和图片媒体。

## 3. 配置

在本地 `.env` 中配置：

```properties
AMAP_WEATHER_KEY=你的高德Web服务Key
AMAP_MAP_KEY=
AMAP_MAP_BASE_URL=https://restapi.amap.com
AMAP_MAP_STATIC_IMAGE_ENABLED=true
```

地图工具默认复用 `AMAP_WEATHER_KEY`。如果团队希望把天气和地图配额隔离，再单独填写 `AMAP_MAP_KEY`。

Key 类型必须是高德开放平台的 Web 服务 Key，并确保账号和配额允许使用地点搜索、路径规划和静态地图等 Web 服务接口。没有静态地图权限时可关闭 `AMAP_MAP_STATIC_IMAGE_ENABLED`，工具会自动降级为文本路线。不要把真实 Key 写入 `application.properties`、测试代码、README 或提交到仓库。

## 4. 票务和推荐边界

- 高德地点数据不等于实时票务库存。
- 工具只为景点生成第三方票务平台搜索入口，不直接下单。
- 是否售票、价格、余票、开放时间和退改规则必须以平台或景区官方页面为准。
- 周边推荐优先按距离展示；评分、人均消费和营业时间可能缺失或更新延迟。
- 路线时间是地图服务估算值，会受到实时路况、候车和换乘影响。
- `optimize` 当前基于地点坐标距离进行近似排序，不承诺实时路况下的全局最优顺序。

## 5. 测试

```powershell
mvn -q "-Dtest=MapWechatToolTests,AmapMapClientTests,AmapStaticMapClientTests,MapServiceTests" test
mvn -q "-Dtest=WechatToolRegistryTests,FunctionCallingToolSchemaConverterTests,FunctionCallingAgentLoopTests" test
mvn -q test
git diff --check
```
