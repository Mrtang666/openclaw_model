# 支付与订单状态

## 支付优先级

```text
WECHAT_JSAPI
MINI_PROGRAM
H5
QR_CODE
MANUAL
```

`fallback_target` 优先承载小程序 Scheme，其次承载微信内可打开的 H5 地址。当前 iLink SDK 没有原生小程序卡片接口，因此通过文本链接或 Scheme 交接；不得描述为机器人自动完成支付。

## 状态

```text
ORDER_CREATED
AWAITING_PAYMENT
PAID
MERCHANT_CONFIRMED
PREPARING
RIDER_ASSIGNED
PICKED_UP
DELIVERING
DELIVERED
MERCHANT_REJECTED
CANCEL_PENDING
CANCELLED
REFUND_PENDING
REFUNDED
CLOSED
```

支付和配送状态以平台服务端回调或查询结果为准。用户说“已经支付”不能直接将订单标记为 `PAID`。
