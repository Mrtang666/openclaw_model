package com.example.spring.xhs.schedule;

import com.example.spring.xhs.report.XhsReportArtifactStorage;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

@Component
@ConditionalOnProperty(prefix = "xhs.scheduled-report", name = "enabled", havingValue = "true")
public class XhsReportArtifactCleanup {

    private final JdbcTemplate jdbcTemplate;
    private final XhsReportArtifactStorage storage;

    public XhsReportArtifactCleanup(JdbcTemplate jdbcTemplate, XhsReportArtifactStorage storage) {
        this.jdbcTemplate = jdbcTemplate;
        this.storage = storage;
    }

    @Scheduled(fixedDelayString = "${xhs.scheduled-report.cleanup-delay:1h}",
            initialDelayString = "${xhs.scheduled-report.cleanup-delay:1h}")
    public int deleteExpired() {
        List<ExpiredArtifact> values = jdbcTemplate.query("""
                SELECT id, storage_key FROM xhs_report_artifacts
                WHERE expires_at <= ? ORDER BY expires_at, id LIMIT 500
                """, (rs, row) -> new ExpiredArtifact(rs.getLong("id"), rs.getString("storage_key")),
                Timestamp.from(Instant.now()));
        int deleted = 0;
        for (ExpiredArtifact value : values) {
            storage.delete(value.storageKey());
            deleted += jdbcTemplate.update("DELETE FROM xhs_report_artifacts WHERE id = ?", value.id());
        }
        return deleted;
    }

    private record ExpiredArtifact(long id, String storageKey) {
    }
}
