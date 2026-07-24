package com.example.spring.wechat.commerce.logistics.client;

import com.example.spring.wechat.commerce.logistics.model.Carrier;
import com.example.spring.wechat.commerce.logistics.model.LogisticsQuery;
import com.example.spring.wechat.commerce.logistics.model.LogisticsServiceException;
import com.example.spring.wechat.commerce.logistics.model.ShipmentStatus;
import com.example.spring.wechat.commerce.logistics.model.ShipmentTrace;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class Kuaidi100LogisticsClientTests {

    @Test
    void submitsSignedQueryAndParsesShipmentTrace() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        Kuaidi100LogisticsClient client = client(builder, "customer-1", "secret-key");

        server.expect(once(), requestTo("https://poll.kuaidi100.com/poll/query.do"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().string(allOf(
                        containsString("customer=customer-1"),
                        containsString("sign=B13DE8ABF7BF974DFD7297B7228C7E8A"),
                        containsString("SF1234567890"),
                        containsString("shunfeng"))))
                .andRespond(withSuccess("""
                        {
                          "returnCode": "200",
                          "message": "ok",
                          "state": "5",
                          "com": "shunfeng",
                          "data": [
                            {"ftime":"2026-07-23 12:30:00","context":"快件正在派送","location":"杭州市西湖区"}
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        ShipmentTrace trace = client.query(new LogisticsQuery("SF1234567890", Carrier.SF, "5678"));

        assertThat(trace.trackingNoMasked()).isEqualTo("SF1****7890");
        assertThat(trace.carrier()).isEqualTo(Carrier.SF);
        assertThat(trace.status()).isEqualTo(ShipmentStatus.OUT_FOR_DELIVERY);
        assertThat(trace.events()).singleElement().satisfies(event -> {
            assertThat(event.description()).isEqualTo("快件正在派送");
            assertThat(event.occurredAt()).isEqualTo(Instant.parse("2026-07-23T04:30:00Z"));
        });
        server.verify();
    }

    @Test
    void exposesNoQuotaErrorReturnedAsTextResponse() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        Kuaidi100LogisticsClient client = client(builder, "customer-1", "secret-key");

        server.expect(once(), requestTo("https://poll.kuaidi100.com/poll/query.do"))
                .andRespond(withSuccess("""
                        {"result":false,"returnCode":"601","message":"查询次数已达上限"}
                        """, MediaType.TEXT_PLAIN));

        assertThatThrownBy(() -> client.query(new LogisticsQuery("YT8888445064497", Carrier.YTO, "")))
                .isInstanceOf(LogisticsServiceException.class)
                .hasMessageContaining("查询次数已达上限");
        server.verify();
    }

    @Test
    void rejectsMissingConfigurationBeforeCallingRemoteService() {
        Kuaidi100LogisticsClient client = client(RestClient.builder(), "", "");

        assertThatThrownBy(() -> client.query(new LogisticsQuery("SF1234567890", Carrier.SF, "")))
                .isInstanceOf(LogisticsServiceException.class)
                .hasMessageContaining("尚未配置");
    }

    private Kuaidi100LogisticsClient client(RestClient.Builder builder, String customer, String key) {
        return new Kuaidi100LogisticsClient(
                builder,
                new ObjectMapper(),
                customer,
                key,
                "https://poll.kuaidi100.com",
                Clock.fixed(Instant.parse("2026-07-23T04:00:00Z"), ZoneOffset.UTC));
    }
}
