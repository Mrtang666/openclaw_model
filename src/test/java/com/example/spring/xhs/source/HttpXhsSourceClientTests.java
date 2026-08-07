package com.example.spring.xhs.source;

import com.example.spring.xhs.config.XhsCollectorProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

class HttpXhsSourceClientTests {

    @Test
    void wiresProductionConstructorWhenCollectorIsEnabled() {
        new ApplicationContextRunner()
                .withPropertyValues("xhs.collector.enabled=true")
                .withBean(RestClient.Builder.class, RestClient::builder)
                .withBean(ObjectMapper.class, ObjectMapper::new)
                .withBean(XhsCollectorProperties.class, () -> new XhsCollectorProperties(
                        true,
                        "http://collector.test",
                        "secret",
                        null,
                        null,
                        30))
                .withUserConfiguration(HttpXhsSourceClient.class)
                .run(context -> assertThat(context).hasSingleBean(HttpXhsSourceClient.class));
    }

    @Test
    void submitsSearchWithoutLeakingProjectMetadata() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://collector.test");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        HttpXhsSourceClient client = new HttpXhsSourceClient(
                builder.defaultHeader("X-Collector-Api-Key", "secret").build(),
                new ObjectMapper());
        server.expect(requestTo("http://collector.test/internal/v1/jobs/search"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Collector-Api-Key", "secret"))
                .andExpect(jsonPath("$.query").value("品牌 A"))
                .andExpect(jsonPath("$.limit").value(20))
                .andExpect(jsonPath("$.sortMode").value("GENERAL"))
                .andExpect(jsonPath("$.timeRange").value("ANY"))
                .andExpect(jsonPath("$.noteType").value("ALL"))
                .andRespond(withSuccess("{\"jobId\":\"external-1\"}", MediaType.APPLICATION_JSON));

        XhsCollectorSubmission result = client.submitSearch(
                new XhsCollectionRequest("project-a", "项目 A", "品牌 A", 20, ""));

        assertThat(result.externalJobId()).isEqualTo("external-1");
        server.verify();
    }

    @Test
    void productionClientSendsFixedLengthJsonBody() throws Exception {
        AtomicLong contentLength = new AtomicLong(-1);
        AtomicReference<String> transferEncoding = new AtomicReference<>();
        AtomicReference<String> requestBody = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/internal/v1/jobs/search", exchange -> {
            contentLength.set(Long.parseLong(exchange.getRequestHeaders().getFirst("Content-Length")));
            transferEncoding.set(exchange.getRequestHeaders().getFirst("Transfer-Encoding"));
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] response = "{\"jobId\":\"external-fixed-length\"}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(202, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        try {
            HttpXhsSourceClient client = new HttpXhsSourceClient(
                    RestClient.builder(),
                    new ObjectMapper(),
                    new XhsCollectorProperties(true,
                            "http://127.0.0.1:" + server.getAddress().getPort(),
                            "secret", null, null, 30));

            XhsCollectorSubmission result = client.submitSearch(
                    new XhsCollectionRequest("xhs-smoke", "武汉旅游测试", "武汉游玩攻略", 3, ""));

            assertThat(result.externalJobId()).isEqualTo("external-fixed-length");
            assertThat(contentLength.get()).isPositive();
            assertThat(transferEncoding.get()).isNull();
            assertThat(new ObjectMapper().readTree(requestBody.get()).path("query").asText())
                    .isEqualTo("武汉游玩攻略");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void exposesSidecarErrorContract() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://collector.test");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        HttpXhsSourceClient client = new HttpXhsSourceClient(builder.build(), new ObjectMapper());
        server.expect(requestTo("http://collector.test/internal/v1/jobs/search"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                {"errorCode":"INVALID_REQUEST","errorMessage":"query is required"}
                                """));

        assertThatThrownBy(() -> client.submitSearch(
                new XhsCollectionRequest("xhs-smoke", "武汉旅游测试", "武汉游玩攻略", 3, "")))
                .isInstanceOf(XhsCollectorException.class)
                .hasMessageContaining("HTTP 400")
                .hasMessageContaining("INVALID_REQUEST")
                .hasMessageContaining("query is required");
        server.verify();
    }

    @Test
    void explainsHowToStartAnUnavailableSidecar() throws Exception {
        int unavailablePort;
        try (ServerSocket socket = new ServerSocket(0)) {
            unavailablePort = socket.getLocalPort();
        }
        HttpXhsSourceClient client = new HttpXhsSourceClient(
                RestClient.builder(),
                new ObjectMapper(),
                new XhsCollectorProperties(true,
                        "http://127.0.0.1:" + unavailablePort,
                        "secret", Duration.ofMillis(200), Duration.ofSeconds(1), 1));

        assertThatThrownBy(() -> client.submitSearch(
                new XhsCollectionRequest("xhs-smoke", "武汉旅游测试", "武汉游玩攻略", 1, "")))
                .isInstanceOf(XhsCollectorException.class)
                .hasMessageContaining("无法连接小红书采集 Sidecar")
                .hasMessageContaining("run-xhs-local.ps1");
    }

    @Test
    void readsCompletedJobUsingNormalizedContract() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://collector.test");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        HttpXhsSourceClient client = new HttpXhsSourceClient(builder.build(), new ObjectMapper());
        server.expect(requestTo("http://collector.test/internal/v1/jobs/external-1"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {
                          "status":"SUCCEEDED",
                          "complete":true,
                          "records":[{"sourcePostId":"note-1"}],
                          "collectedAt":"2026-07-28T02:00:00Z"
                        }
                        """, MediaType.APPLICATION_JSON));

        XhsCollectorJobResult result = client.getJob("external-1");

        assertThat(result.status()).isEqualTo(XhsCollectionStatus.SUCCEEDED);
        assertThat(result.complete()).isTrue();
        assertThat(result.records()).hasSize(1);
        server.verify();
    }

    @Test
    void resolvesAStoredNoteLinkUsingFastSidecarEndpoint() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://collector.test");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        HttpXhsSourceClient client = new HttpXhsSourceClient(builder.build(), new ObjectMapper());
        server.expect(requestTo("http://collector.test/internal/v1/links/resolve"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.noteId").value("note-1"))
                .andExpect(jsonPath("$.query").value("品牌 A"))
                .andRespond(withSuccess("""
                        {"status":"FOUND","accessUrl":"https://www.xiaohongshu.com/explore/note-1?xsec_token=value&xsec_source=pc_search"}
                        """, MediaType.APPLICATION_JSON));

        XhsResolvedLink result = client.resolveLink("note-1", "品牌 A", 100);

        assertThat(result.found()).isTrue();
        assertThat(result.accessUrl()).contains("xsec_token=value");
        server.verify();
    }
}
