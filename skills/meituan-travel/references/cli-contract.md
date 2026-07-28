# 美团酒旅 CLI 契约

项目调用官方 npm 包 `@meituan-travel/ht-ai` 提供的 `ht-ai` 命令。生产环境固定安装经过验证的版本，不在每次请求中下载 `latest`。

```text
ht-ai query
  --query <整理后的旅行需求>
  --origin-query <用户原始旅行需求>
  --channel meituan-developer
  --city <当前城市或主要目的地>
  --output text
```

## 环境

- Node.js 18 或更高版本。
- `MEITUAN_HT_TOKEN` 只通过子进程环境传递。
- 渠道固定为 `meituan-developer`。
- 官方请求最长约 120 秒，项目 CLI 超时应略高于该值，微信任务总超时应更高。
- Windows 全局 npm 安装只提供 `.cmd`/`.ps1` shim。Java 客户端会自动定位同目录下的 `node_modules/@meituan-travel/ht-ai/dist/index.js`，并通过 `node.exe` 执行，不直接启动 shell shim。

## 退出码

| 退出码 | 处理 |
| --- | --- |
| `0` | 将标准输出直接返回微信 |
| `1` | 返回通用查询失败提示 |
| `3` | 返回鉴权配置提示，不显示 Token |
| 其他 | 返回服务暂不可用提示 |

进度和诊断信息来自标准错误流，不得混入最终微信回复。
