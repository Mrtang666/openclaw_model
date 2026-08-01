package com.example.spring.xhs.schedule;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "xhs.scheduled-report", name = "enabled", havingValue = "true")
public class XhsScheduledReportDispatcher {

    private final XhsReportScheduleService scheduleService;
    private final XhsScheduledReportExecutionService executionService;
    private final XhsScheduledReportDeliveryService deliveryService;

    public XhsScheduledReportDispatcher(
            XhsReportScheduleService scheduleService,
            XhsScheduledReportExecutionService executionService,
            XhsScheduledReportDeliveryService deliveryService) {
        this.scheduleService = scheduleService;
        this.executionService = executionService;
        this.deliveryService = deliveryService;
    }

    @Scheduled(fixedDelayString = "${xhs.scheduled-report.polling-delay:10s}")
    public void dispatch() {
        scheduleService.enqueueDue();
        executionService.processPending();
        deliveryService.dispatchPending();
    }
}
