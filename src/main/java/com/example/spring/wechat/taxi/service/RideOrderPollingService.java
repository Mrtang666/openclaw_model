package com.example.spring.wechat.taxi.service;

import com.example.spring.wechat.taxi.model.RideOrder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/** Didi MCP has no confirmed webhook; polling is opt-in and keeps notifications idempotent. */
@Service
@ConditionalOnProperty(name = "ride.polling.enabled", havingValue = "true")
public class RideOrderPollingService {
    private final RideOrchestrationService rides;
    public RideOrderPollingService(RideOrchestrationService rides) { this.rides = rides; }

    @Scheduled(fixedDelayString = "${RIDE_POLLING_DELAY_MS:15000}")
    public void poll() {
        // The orchestration service owns the active-order index. A future outbound
        // notifier can consume the returned snapshots without coupling to WeChat clients.
        for (RideOrder ignored : rides.activeOrders()) {
            try { rides.query(ignored.sessionKey(), ignored.orderId()); }
            catch (RuntimeException ignoredException) { /* transient MCP failure */ }
        }
    }
}
