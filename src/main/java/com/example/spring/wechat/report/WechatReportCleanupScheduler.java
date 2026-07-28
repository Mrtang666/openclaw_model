package com.example.spring.wechat.report;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class WechatReportCleanupScheduler {

    private final WechatReportService reportService;

    public WechatReportCleanupScheduler(WechatReportService reportService) {
        this.reportService = reportService;
    }

    @Scheduled(fixedDelayString = "${wechat.report.cleanup-interval-ms:3600000}")
    public void cleanupExpiredReports() {
        reportService.cleanupExpired();
    }
}
