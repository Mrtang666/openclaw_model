package com.example.spring.wechat.netdisk.model;

import java.time.Instant;

/**
 * 返回给微信用户的网盘授权引导信息。
 *
 * <p>当用户没有绑定百度网盘时，工具不会静默失败，而是生成授权链接。
 * state 用来防止授权回调串号，expiresAt 用来控制授权链接有效期。</p>
 */
public record NetdiskAuthPrompt(
        String state,
        String authorizationUrl,
        Instant expiresAt) {
}
