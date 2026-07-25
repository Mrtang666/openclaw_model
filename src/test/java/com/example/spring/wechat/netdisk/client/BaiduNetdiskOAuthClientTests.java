package com.example.spring.wechat.netdisk.client;

import com.example.spring.wechat.netdisk.config.BaiduNetdiskProperties;
import com.example.spring.wechat.netdisk.model.NetdiskOAuthToken;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class BaiduNetdiskOAuthClientTests {

    @Test
    void buildsAuthorizationUrlWithOAuthParameters() {
        BaiduNetdiskOAuthClient client = new BaiduNetdiskOAuthClient(
                RestClient.builder(),
                new ObjectMapper(),
                properties());

        String url = client.buildAuthorizationUrl("state-1");

        assertThat(url).startsWith("https://openapi.baidu.com/oauth/2.0/authorize?");
        assertThat(url).contains("response_type=code");
        assertThat(url).contains("client_id=app-key");
        assertThat(url).contains("redirect_uri=https://openclaw.example.com/api/netdisk/baidu/callback");
        assertThat(url).contains("scope=basic,netdisk");
        assertThat(url).contains("state=state-1");
    }

    @Test
    void prefersExplicitOAuthClientIdWhenBuildingAuthorizationUrl() {
        BaiduNetdiskOAuthClient client = new BaiduNetdiskOAuthClient(
                RestClient.builder(),
                new ObjectMapper(),
                properties("oauth-client-id"));

        String url = client.buildAuthorizationUrl("state-1");

        assertThat(url).contains("client_id=oauth-client-id");
    }

    @Test
    void exchangesCodeAndParsesTokenResponse() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(URI.create(
                        "https://openapi.baidu.com/oauth/2.0/token?grant_type=authorization_code&code=code-1&client_id=app-key&client_secret=client-secret&redirect_uri=https://openclaw.example.com/api/netdisk/baidu/callback")))
                .andRespond(withSuccess("""
                        {
                          "access_token": "access-token-1",
                          "refresh_token": "refresh-token-1",
                          "expires_in": 3600,
                          "scope": "basic netdisk"
                        }
                        """, MediaType.APPLICATION_JSON));
        BaiduNetdiskOAuthClient client = new BaiduNetdiskOAuthClient(builder, new ObjectMapper(), properties());

        NetdiskOAuthToken token = client.exchangeCode("code-1");

        assertThat(token.accessToken()).isEqualTo("access-token-1");
        assertThat(token.refreshToken()).isEqualTo("refresh-token-1");
        assertThat(token.expiresInSeconds()).isEqualTo(3600);
        assertThat(token.scope()).isEqualTo("basic netdisk");
        server.verify();
    }

    @Test
    void rejectsOAuthConfigWhenOnlyAppIdIsConfigured() {
        BaiduNetdiskOAuthClient client = new BaiduNetdiskOAuthClient(
                RestClient.builder(),
                new ObjectMapper(),
                new BaiduNetdiskProperties(
                        true,
                        "client-id",
                        "",
                        "",
                        "client-secret",
                        "sign-key",
                        "https://openclaw.example.com/api/netdisk/baidu/callback",
                        "https://openapi.baidu.com/oauth/2.0/authorize",
                        "https://openapi.baidu.com/oauth/2.0/token",
                        "https://mcp-pan.baidu.com/sse",
                        "test-encryption-key",
                        10,
                        30,
                        20_000,
                        5,
                        "/OpenClaw/"));

        assertThatThrownBy(() -> client.buildAuthorizationUrl("state-1"))
                .hasMessageContaining("BAIDU_NETDISK_APP_KEY");
    }

    private BaiduNetdiskProperties properties() {
        return properties("");
    }

    private BaiduNetdiskProperties properties(String oauthClientId) {
        return new BaiduNetdiskProperties(
                true,
                "client-id",
                "app-key",
                oauthClientId,
                "client-secret",
                "sign-key",
                "https://openclaw.example.com/api/netdisk/baidu/callback",
                "https://openapi.baidu.com/oauth/2.0/authorize",
                "https://openapi.baidu.com/oauth/2.0/token",
                "https://mcp-pan.baidu.com/sse",
                "test-encryption-key",
                10,
                30,
                20_000,
                5,
                "/OpenClaw/");
    }
}
