package com.example.spring.xhs.repository;

import com.example.spring.xhs.report.XhsDailyReport;

import java.time.Instant;
import java.time.LocalDate;

public interface XhsReportRepository {

    XhsDailyReport loadDailyReport(
            String projectKey,
            LocalDate reportDate,
            Instant periodStart,
            Instant periodEnd,
            int topIncidentLimit);
}
