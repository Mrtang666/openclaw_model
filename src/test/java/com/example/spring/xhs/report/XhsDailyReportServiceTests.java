package com.example.spring.xhs.report;

import com.example.spring.xhs.repository.XhsReportRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class XhsDailyReportServiceTests {

    @Test
    void usesShanghaiCalendarDayAndCapsTopIncidentLimit() {
        RecordingRepository repository = new RecordingRepository();
        XhsDailyReportService service = new XhsDailyReportService(repository);

        service.report("brand-a", "2026-07-28", 99);

        assertThat(repository.reportDate).isEqualTo(LocalDate.of(2026, 7, 28));
        assertThat(repository.periodStart).isEqualTo(Instant.parse("2026-07-27T16:00:00Z"));
        assertThat(repository.periodEnd).isEqualTo(Instant.parse("2026-07-28T16:00:00Z"));
        assertThat(repository.limit).isEqualTo(10);
    }

    private static final class RecordingRepository implements XhsReportRepository {
        private LocalDate reportDate;
        private Instant periodStart;
        private Instant periodEnd;
        private int limit;

        @Override
        public XhsDailyReport loadDailyReport(String projectKey, LocalDate reportDate,
                                              Instant periodStart, Instant periodEnd, int topIncidentLimit) {
            this.reportDate = reportDate;
            this.periodStart = periodStart;
            this.periodEnd = periodEnd;
            this.limit = topIncidentLimit;
            return new XhsDailyReport(
                    projectKey, "品牌 A", reportDate, periodStart, periodEnd,
                    0, 0, 0, 0, 0, 0, 0, 0, null, null, null);
        }
    }
}
