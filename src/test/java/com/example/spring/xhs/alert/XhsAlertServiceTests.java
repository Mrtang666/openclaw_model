package com.example.spring.xhs.alert;

import com.example.spring.xhs.config.XhsAlertProperties;
import com.example.spring.xhs.repository.XhsAlertRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class XhsAlertServiceTests {

    @Test
    void sendsPendingDeliveryAndMarksItSent() {
        FakeRepository repository = new FakeRepository();
        repository.deliveries = List.of(delivery());
        RecordingNotifier notifier = new RecordingNotifier(false);
        XhsAlertService service = new XhsAlertService(
                repository, notifier, new XhsAlertProperties(true, 3, 20));

        int processed = service.dispatchPending();

        assertThat(processed).isEqualTo(1);
        assertThat(repository.sentDeliveryId).isEqualTo(10);
        assertThat(repository.releasedClaims).isEqualTo(1);
        assertThat(notifier.message).contains("CRITICAL", "80分", "告警编号：20", "仅覆盖已采集数据");
    }

    @Test
    void recordsFailureForRetry() {
        FakeRepository repository = new FakeRepository();
        repository.deliveries = List.of(delivery());
        XhsAlertService service = new XhsAlertService(
                repository, new RecordingNotifier(true), new XhsAlertProperties(true, 3, 20));

        service.dispatchPending();

        assertThat(repository.failedDeliveryId).isEqualTo(10);
        assertThat(repository.lastError).contains("send failed");
        assertThat(repository.releasedClaims).isEqualTo(1);
    }

    @Test
    void disabledAlertsDoNotCreateOrDispatchEvents() {
        FakeRepository repository = new FakeRepository();
        XhsAlertService service = new XhsAlertService(
                repository, new RecordingNotifier(false), new XhsAlertProperties(false, 3, 20));

        service.evaluate(30, 80, "CRITICAL");

        assertThat(service.dispatchPending()).isZero();
        assertThat(repository.createdIncidentId).isZero();
    }

    private XhsAlertDelivery delivery() {
        return new XhsAlertDelivery(
                10, 20, "brand-a", "connection-1", "user-1",
                "用户反馈使用后红肿", "CONSUMER_SAFETY", 80, "CRITICAL", 3, 0);
    }

    private static final class RecordingNotifier implements XhsAlertNotifier {
        private final boolean fail;
        private String message;

        private RecordingNotifier(boolean fail) {
            this.fail = fail;
        }

        @Override
        public void send(XhsAlertDelivery delivery, String message) {
            if (fail) {
                throw new IllegalStateException("send failed");
            }
            this.message = message;
        }
    }

    private static final class FakeRepository implements XhsAlertRepository {
        private List<XhsAlertDelivery> deliveries = List.of();
        private long sentDeliveryId;
        private long failedDeliveryId;
        private long createdIncidentId;
        private String lastError;
        private int releasedClaims;

        @Override
        public long subscribeWechat(String projectKey, String connectionId, String recipientId,
                                    int minimumRiskScore, int cooldownMinutes, Instant now) {
            return 1;
        }

        @Override
        public int createEventsForIncident(long incidentId, int riskScore, String riskLevel, Instant now) {
            createdIncidentId = incidentId;
            return 1;
        }

        @Override
        public List<XhsAlertDelivery> findPendingDeliveries(int maxAttempts, int limit) {
            return deliveries;
        }

        @Override
        public void markDeliverySent(long deliveryId, long alertEventId, int maxAttempts, Instant now) {
            sentDeliveryId = deliveryId;
        }

        @Override
        public void markDeliveryFailed(long deliveryId, long alertEventId, String errorMessage, int maxAttempts, Instant now) {
            failedDeliveryId = deliveryId;
            lastError = errorMessage;
        }

        @Override
        public void releaseDeliveryClaim(XhsAlertDelivery delivery) {
            releasedClaims++;
        }

        @Override
        public boolean acknowledge(String projectKey, long alertEventId, String connectionId,
                                   String recipientId, Instant now) {
            return true;
        }
    }
}
