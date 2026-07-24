package com.example.spring.wechat.commerce.logistics.service;

import com.example.spring.wechat.commerce.logistics.client.LogisticsClient;
import com.example.spring.wechat.commerce.logistics.model.Carrier;
import com.example.spring.wechat.commerce.logistics.model.LogisticsServiceException;
import com.example.spring.wechat.commerce.logistics.model.ShipmentEvent;
import com.example.spring.wechat.commerce.logistics.model.ShipmentStatus;
import com.example.spring.wechat.commerce.logistics.model.ShipmentTrace;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LogisticsTrackServiceTests {

    @Test
    void validatesInputAndSortsNewestEventFirst() {
        LogisticsClient client = query -> new ShipmentTrace(
                "SF1****7890",
                Carrier.SF,
                ShipmentStatus.IN_TRANSIT,
                "杭州转运中心",
                "",
                List.of(
                        event("2026-07-23T01:00:00Z", "较早节点"),
                        event("2026-07-23T03:00:00Z", "最新节点")),
                Instant.parse("2026-07-23T04:00:00Z"));
        LogisticsTrackService service = new LogisticsTrackService(client);

        ShipmentTrace trace = service.track("SF1234567890", "顺丰速运", "5678");

        assertThat(trace.carrier()).isEqualTo(Carrier.SF);
        assertThat(trace.events()).extracting(ShipmentEvent::description)
                .containsExactly("最新节点", "较早节点");
    }

    @Test
    void rejectsInvalidInputBeforeCallingClient() {
        LogisticsTrackService service = new LogisticsTrackService(query -> {
            throw new AssertionError("client should not be called");
        });

        assertThatThrownBy(() -> service.track("", "", ""))
                .isInstanceOf(LogisticsServiceException.class)
                .hasMessageContaining("快递单号");
        assertThatThrownBy(() -> service.track("SF1234567890", "sf", "123"))
                .isInstanceOf(LogisticsServiceException.class)
                .hasMessageContaining("phone_last4");
    }

    @Test
    void detectsYuantongFromTrackingNumberPrefix() {
        AtomicReference<Carrier> capturedCarrier = new AtomicReference<>();
        LogisticsTrackService service = new LogisticsTrackService(query -> {
            capturedCarrier.set(query.carrier());
            return new ShipmentTrace(
                    "YT8****4497",
                    query.carrier(),
                    ShipmentStatus.IN_TRANSIT,
                    "杭州转运中心",
                    "",
                    List.of(event("2026-07-23T03:00:00Z", "运输中")),
                    Instant.parse("2026-07-23T04:00:00Z"));
        });

        service.track("YT8888445064497", "", "");

        assertThat(capturedCarrier.get()).isEqualTo(Carrier.YTO);
    }

    private ShipmentEvent event(String instant, String description) {
        return new ShipmentEvent(Instant.parse(instant), "", description);
    }
}
