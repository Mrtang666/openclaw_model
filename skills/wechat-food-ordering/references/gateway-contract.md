# 外卖网关契约

`FOOD_DELIVERY_BASE_URL` 指向正式外卖开放接口适配器，或隔离部署的 Playwright Worker。

## 接口

```text
POST /v1/food/merchants/search
POST /v1/food/menu
POST /v1/food/orders/preview
POST /v1/food/orders
POST /v1/food/orders/{providerOrderId}/payment
GET  /v1/food/orders/{providerOrderId}
POST /v1/food/orders/{providerOrderId}/cancel
```

所有接口使用 JSON。配置 `FOOD_DELIVERY_API_KEY` 时，客户端发送 Bearer Token。

## 强制返回字段

- 预结算：`total`、`expires_at`，建议返回费用明细和 `eta_minutes`。
- 创建订单：`provider_order_id`。缺少该字段时本地拒绝确认订单成功。
- 支付交接：`type`，以及 `target` 或 `fallback_target` 至少一个。
- 查询订单：`status`，可返回 `progress_text` 和 `eta_minutes`。

## Playwright Worker

Worker 必须独立隔离每个微信用户的浏览器上下文。操作饿了么 H5 时，每次交互前读取当前页面状态，不复用陈旧元素引用，不绕过验证码、登录授权、支付确认或平台风控。
