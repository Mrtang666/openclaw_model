package com.example.spring.wechat.netdisk.auth;

import com.example.spring.wechat.netdisk.model.NetdiskOAuthToken;

/**
 * 网盘 OAuth 客户端抽象。
 *
 * <p>授权服务只依赖这个接口，不直接关心百度接口的 HTTP 细节。
 * 这样后续测试可以用假的 OAuth 客户端，也方便未来接入其他网盘平台。</p>
 */
public interface NetdiskOAuthClient {

    String buildAuthorizationUrl(String state);

    NetdiskOAuthToken exchangeCode(String code);

    NetdiskOAuthToken refreshToken(String refreshToken);
}
