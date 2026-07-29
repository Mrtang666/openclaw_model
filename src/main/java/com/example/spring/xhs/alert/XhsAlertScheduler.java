package com.example.spring.xhs.alert;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "xhs.alert", name = "enabled", havingValue = "true")
public class XhsAlertScheduler {

    private final XhsAlertService alertService;

    public XhsAlertScheduler(XhsAlertService alertService) {
        this.alertService = alertService;
    }

    @Scheduled(fixedDelayString = "${xhs.alert.polling-delay:10s}")
    public void dispatch() {
        alertService.dispatchPending();
    }
}
