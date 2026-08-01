package com.example.spring.xhs.schedule;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class XhsNegativePostEmailScheduler {

    private final XhsNegativePostEmailService service;

    public XhsNegativePostEmailScheduler(XhsNegativePostEmailService service) {
        this.service = service;
    }

    @Scheduled(fixedDelayString = "${xhs.report.negative-email-polling-delay:10s}")
    public void dispatch() {
        service.dispatchPending();
    }
}
