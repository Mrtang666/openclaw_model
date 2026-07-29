package com.example.spring.xhs.ingestion;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "xhs.collector", name = "enabled", havingValue = "true")
public class XhsCollectionScheduler {

    private final XhsCollectionCoordinator coordinator;

    public XhsCollectionScheduler(XhsCollectionCoordinator coordinator) {
        this.coordinator = coordinator;
    }

    @Scheduled(fixedDelayString = "${xhs.collector.polling-delay:10s}")
    public void poll() {
        coordinator.pollPending();
    }
}
