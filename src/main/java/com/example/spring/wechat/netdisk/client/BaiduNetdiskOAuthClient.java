package com.example.spring.wechat.netdisk.client;

import com.example.spring.wechat.netdisk.auth.NetdiskOAuthClient;
import com.example.spring.wechat.netdisk.config.BaiduNetdiskProperties;
import com.example.spring.wechat.netdisk.exception.NetdiskToolException;
import com.example.spring.wechat.netdisk.model.NetdiskOAuthToken;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Instant;

/**
 * 百度网盘 OAuth HTTP 客户端。
 *
 * <p>负责生成百度授权链接、用授权码换 token、用 refresh token 刷新 token。
 * 这里不保存 token，只把百度返回的数据解析成统一模型交给授权服务处理。</p>
 */
@Component
public class BaiduNetdiskOAuthClient implements NetdiskOAuthClient {

    private static final Logger log = LoggerFactory.getLogger(BaiduNetdiskOAuthClient.class);
    private static final String DEFAULT_AUTH_URL = "https://openapi.baidu.com/oauth/2.0/authorize";
    private static final String DEFAULT_TOKEN_URL = "https://openapi.baidu.com/oauth/2.0/token";
    private static final String NETDISK_SCOPE = "basic,netdisk";

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final BaiduNetdiskProperties properties;

    public BaiduNetdiskOAuthClient(
            RestClient.Builder restClientBuilder,
            ObjectMapper objectMapper,
            BaiduNetdiskProperties properties) {
        this.restClient = restClientBuilder.build();
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    @Override
    public String buildAuthorizationUrl(String state) {
        validateOAuthConfig();
        String url = UriComponentsBuilder.fromHttpUrl(authUrl())
                .queryParam("response_type", "code")
                .queryParam("client_id", clientId())
                .queryParam("redirect_uri", properties.redirectUri())
                .queryParam("scope", NETDISK_SCOPE)
                .queryParam("state", state)
                .toUriString();
        log.info("百度网盘 OAuth 授权链接已生成，clientIdSource={}, clientIdMasked={}, scope={}, redirectUri={}",
                clientIdSource(), mask(clientId()), NETDISK_SCOPE, properties.redirectUri());
        return url;
    }

    @Override
    public NetdiskOAuthToken exchangeCode(String code) {
        validateOAuthConfig();
        JsonNode body = callTokenApi(UriComponentsBuilder.fromHttpUrl(tokenUrl())
                .queryParam("grant_type", "authorization_code")
                .queryParam("code", code)
                .queryParam("client_id", clientId())
                .queryParam("client_secret", clientSecret())
                .queryParam("redirect_uri", properties.redirectUri())
                .toUriString());
        return parseToken(body);
    }

    @Override
    public NetdiskOAuthToken refreshToken(String refreshToken) {
        validateOAuthConfig();
        JsonNode body = callTokenApi(UriComponentsBuilder.fromHttpUrl(tokenUrl())
                .queryParam("grant_type", "refresh_token")
                .queryParam("refresh_token", refreshToken)
                .queryParam("client_id", clientId())
                .queryParam("client_secret", clientSecret())
                .toUriString());
        return parseToken(body);
    }

    private JsonNode callTokenApi(String url) {
        try {
            JsonNode response = restClient.get()
                    .uri(url)
                    .retrieve()
                    .body(JsonNode.class);
            if (response == null) {
                throw new NetdiskToolException("百度网盘授权接口没有返回内容");
            }
            if (response.hasNonNull("error")) {
                throw new NetdiskToolException("百度网盘授权失败："
                        + response.path("error_description").asText(response.path("error").asText()));
            }
            return response;
        } catch (RestClientException exception) {
            throw new NetdiskToolException("调用百度网盘授权接口失败", exception);
        }
    }

    private NetdiskOAuthToken parseToken(JsonNode body) {
        String accessToken = body.path("access_token").asText("");
        if (accessToken.isBlank()) {
            throw new NetdiskToolException("百度网盘授权接口未返回 access_token");
        }
        long expiresIn = body.path("expires_in").asLong(0);
        return new NetdiskOAuthToken(
                accessToken,
                body.path("refresh_token").asText(""),
                expiresIn,
                body.path("scope").asText(""),
                expiresIn > 0 ? Instant.now().plusSeconds(expiresIn) : null);
    }

    private void validateOAuthConfig() {
        if (clientId().isBlank()) {
            throw new NetdiskToolException("百度网盘 OAuth client_id 未配置，请检查 BAIDU_NETDISK_OAUTH_CLIENT_ID 或 BAIDU_NETDISK_APP_KEY");
        }
        if (clientSecret().isBlank()) {
            throw new NetdiskToolException("百度网盘 OAuth client_secret 未配置，请检查 BAIDU_NETDISK_SECRET_KEY");
        }
        if (properties.redirectUri().isBlank()) {
            throw new NetdiskToolException("百度网盘 OAuth 回调地址未配置，请检查 BAIDU_NETDISK_REDIRECT_URI");
        }
    }

    private String authUrl() {
        return properties.authBaseUrl().isBlank() ? DEFAULT_AUTH_URL : properties.authBaseUrl();
    }

    private String tokenUrl() {
        return properties.tokenUrl().isBlank() ? DEFAULT_TOKEN_URL : properties.tokenUrl();
    }

    private String clientId() {
        return firstNonBlank(properties.oauthClientId(), properties.appKey());
    }

    private String clientIdSource() {
        return properties.oauthClientId().isBlank() ? "BAIDU_NETDISK_APP_KEY" : "BAIDU_NETDISK_OAUTH_CLIENT_ID";
    }

    private String clientSecret() {
        return firstNonBlank(properties.secretKey(), properties.signKey());
    }

    private String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first.strip();
        }
        return second == null ? "" : second.strip();
    }

    private String mask(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String normalized = value.strip();
        if (normalized.length() <= 8) {
            return normalized.charAt(0) + "***" + normalized.charAt(normalized.length() - 1);
        }
        return normalized.substring(0, 4) + "***" + normalized.substring(normalized.length() - 4);
    }
}
