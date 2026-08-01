package com.example.spring.xhs.report;

import com.example.spring.xhs.repository.XhsReportRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;

@Service
public class XhsDailyReportService {

    private static final ZoneId REPORT_ZONE = ZoneId.of("Asia/Shanghai");

    private final XhsReportRepository repository;

    public XhsDailyReportService(XhsReportRepository repository) {
        this.repository = repository;
    }

    public XhsDailyReport report(String projectKey, String date, int topIncidentLimit) {
        String key = required(projectKey);
        LocalDate reportDate = parseDate(date);
        Instant start = reportDate.atStartOfDay(REPORT_ZONE).toInstant();
        Instant end = reportDate.plusDays(1).atStartOfDay(REPORT_ZONE).toInstant();
        return repository.loadDailyReport(key, reportDate, start, end,
                Math.max(1, Math.min(topIncidentLimit <= 0 ? 5 : topIncidentLimit, 10)));
    }

    public XhsDailyReport reportPeriod(
            String projectKey, LocalDate labelDate, Instant periodStart, Instant periodEnd, int topIncidentLimit) {
        String key = required(projectKey);
        if (periodStart == null || periodEnd == null || !periodStart.isBefore(periodEnd)) {
            throw new IllegalArgumentException("报告统计周期无效");
        }
        LocalDate date = labelDate == null ? periodEnd.atZone(REPORT_ZONE).toLocalDate() : labelDate;
        return repository.loadDailyReport(key, date, periodStart, periodEnd,
                Math.max(1, Math.min(topIncidentLimit <= 0 ? 10 : topIncidentLimit, 100)));
    }

    private LocalDate parseDate(String value) {
        if (value == null || value.isBlank()) {
            return LocalDate.now(REPORT_ZONE);
        }
        try {
            return LocalDate.parse(value.strip());
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException("date 必须使用 yyyy-MM-dd 格式", exception);
        }
    }

    private String required(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("project_key 不能为空");
        }
        return value.strip();
    }
}
