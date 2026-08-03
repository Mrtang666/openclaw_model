package com.example.spring.wechat.context;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class MemoryGraphMaintenanceScheduler {

    private static final Logger log = LoggerFactory.getLogger(MemoryGraphMaintenanceScheduler.class);

    private final WechatContextProperties properties;

    public MemoryGraphMaintenanceScheduler(WechatContextProperties properties) {
        this.properties = properties;
    }

    @Scheduled(initialDelayString = "${wechat.memory.summary-maintenance-initial-delay-ms:60000}",
            fixedDelayString = "${wechat.memory.summary-maintenance-delay-ms:300000}")
    public void run() {
        if (properties == null || !properties.memoryGraphEnabled() || !properties.longTermMemoryIngestionEnabled()) {
            return;
        }
        log.debug("Memory Graph maintenance tick");
    }
}
