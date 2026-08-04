package com.example.spring.xhs.alert;

import com.example.spring.xhs.config.XhsAlertProperties;
import com.example.spring.xhs.repository.XhsAlertRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
public class XhsAlertService {

    private final XhsAlertRepository repository;
    private final XhsAlertNotifier notifier;
    private final XhsAlertProperties properties;

    public XhsAlertService(XhsAlertRepository repository, XhsAlertNotifier notifier, XhsAlertProperties properties) {
        this.repository = repository;
        this.notifier = notifier;
        this.properties = properties;
    }

    public void evaluate(long incidentId, int riskScore, String riskLevel) {
        if (properties.enabled()) {
            repository.createEventsForIncident(incidentId, riskScore, riskLevel, Instant.now());
        }
    }

    public int dispatchPending() {
        if (!properties.enabled()) {
            return 0;
        }
        List<XhsAlertDelivery> deliveries = repository.claimPendingDeliveries(
                properties.maxDeliveryAttempts(), properties.batchSize());
        deliveries.forEach(this::dispatch);
        return deliveries.size();
    }

    public long subscribeWechat(String projectKey, String connectionId, String recipientId,
                                int minimumRiskScore, int cooldownMinutes) {
        return repository.subscribeWechat(projectKey, connectionId, recipientId,
                minimumRiskScore, cooldownMinutes, Instant.now());
    }

    public boolean acknowledge(String projectKey, long alertEventId, String connectionId, String recipientId) {
        return repository.acknowledge(projectKey, alertEventId, connectionId, recipientId, Instant.now());
    }

    private void dispatch(XhsAlertDelivery delivery) {
        try {
            notifier.send(delivery, message(delivery));
            repository.markDeliverySent(delivery, properties.maxDeliveryAttempts(), Instant.now());
        } catch (RuntimeException exception) {
            repository.markDeliveryFailed(delivery, safeMessage(exception),
                    properties.maxDeliveryAttempts(), Instant.now());
        } finally {
            repository.releaseDeliveryClaim(delivery);
        }
    }

    private String message(XhsAlertDelivery delivery) {
        return """
                小红书舆情告警
                项目：%s
                等级：%s（%d分）
                类别：%s
                事件：%s
                关联笔记：%d 条
                告警编号：%d

                可回复“确认小红书告警 %d，项目 %s”进行确认。
                结果仅覆盖已采集数据。
                """.formatted(
                delivery.projectKey(), delivery.riskLevel(), delivery.riskScore(),
                delivery.riskCategory(), delivery.incidentTitle(), delivery.postCount(),
                delivery.alertEventId(), delivery.alertEventId(), delivery.projectKey()).strip();
    }

    private String safeMessage(Throwable throwable) {
        String message = throwable == null ? "" : throwable.getMessage();
        return message == null || message.isBlank() ? "未知错误" : message;
    }
}
