package com.example.spring.wechat.netdisk.model;

import java.time.Instant;

/**
 * 百度网盘 OAuth 换取到的 token 信息。
 *
 * <p>access token 用于调用网盘能力，refresh token 用于过期后刷新授权。
 * 真正落库时不会保存明文 token，而是交给加密服务加密后再保存。</p>
 */
public record NetdiskOAuthToken(
        String accessToken,
        String refreshToken,
        long expiresInSeconds,
        String scope,
        Instant expiresAt) {
}
