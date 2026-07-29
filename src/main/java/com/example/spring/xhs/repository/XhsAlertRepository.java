package com.example.spring.xhs.repository;

import com.example.spring.xhs.alert.XhsAlertDelivery;

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

    void markDeliverySent(long deliveryId, long alertEventId, int maxAttempts, Instant now);

    void markDeliveryFailed(
            long deliveryId,
            long alertEventId,
            String errorMessage,
            int maxAttempts,
            Instant now);

    boolean acknowledge(
            String projectKey,
            long alertEventId,
            String connectionId,
            String recipientId,
            Instant now);
}
