package com.example.spring.xhs.alert;

public record XhsAlertDelivery(
        long deliveryId,
        long alertEventId,
        String projectKey,
        String connectionId,
        String recipientId,
        String incidentTitle,
        String riskCategory,
        int riskScore,
        String riskLevel,
        int postCount,
        int attemptCount) {
}
