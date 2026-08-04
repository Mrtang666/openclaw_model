package com.example.spring.xhs.repository;

import com.example.spring.xhs.alert.XhsAlertDelivery;
import java.util.UUID;

import java.time.Instant;
import java.util.List;

public interface XhsAlertRepository {

    long subscribeWechat(
            String projectKey,
            String connectionId,
            String recipientId,
            int minimumRiskScore,
            int cooldownMinutes,
            Instant now);

    int createEventsForIncident(long incidentId, int riskScore, String riskLevel, Instant now);

    List<XhsAlertDelivery> findPendingDeliveries(int maxAttempts, int limit);

    default List<XhsAlertDelivery> claimPendingDeliveries(int maxAttempts, int limit) {
        String token = "legacy-" + UUID.randomUUID();
        return findPendingDeliveries(maxAttempts, limit).stream()
                .map(delivery -> new XhsAlertDelivery(delivery.deliveryId(), delivery.alertEventId(),
                        delivery.projectKey(), delivery.connectionId(), delivery.recipientId(),
                        delivery.incidentTitle(), delivery.riskCategory(), delivery.riskScore(),
                        delivery.riskLevel(), delivery.postCount(), delivery.attemptCount(), token))
                .toList();
    }

    default void releaseDeliveryClaim(XhsAlertDelivery delivery) {
    }

    void markDeliverySent(long deliveryId, long alertEventId, int maxAttempts, Instant now);

    default void markDeliverySent(XhsAlertDelivery delivery, int maxAttempts, Instant now) {
        markDeliverySent(delivery.deliveryId(), delivery.alertEventId(), maxAttempts, now);
    }

    void markDeliveryFailed(
            long deliveryId,
            long alertEventId,
            String errorMessage,
            int maxAttempts,
            Instant now);

    default void markDeliveryFailed(XhsAlertDelivery delivery, String errorMessage,
                                    int maxAttempts, Instant now) {
        markDeliveryFailed(delivery.deliveryId(), delivery.alertEventId(), errorMessage, maxAttempts, now);
    }

    boolean acknowledge(
            String projectKey,
            long alertEventId,
            String connectionId,
            String recipientId,
            Instant now);
}
