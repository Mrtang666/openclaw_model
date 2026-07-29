package com.example.spring.xhs.console;

import com.example.spring.xhs.config.XhsCollectorProperties;
import org.springframework.http.HttpStatusCode;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.Instant;

@Service
public class XhsConsoleHealthService {

    private final JdbcTemplate jdbcTemplate;
    private final XhsCollectorProperties collectorProperties;
    private final RestClient.Builder restClientBuilder;

    public XhsConsoleHealthService(
            JdbcTemplate jdbcTemplate,
            XhsCollectorProperties collectorProperties,
            RestClient.Builder restClientBuilder) {
        this.jdbcTemplate = jdbcTemplate;
        this.collectorProperties = collectorProperties;
        this.restClientBuilder = restClientBuilder;
    }

    public XhsConsoleHealth health() {
        boolean databaseUp = databaseUp();
        boolean collectorEnabled = collectorProperties.enabled();
        CollectorHealth collector = collectorEnabled ? collectorUp() : new CollectorHealth(false, "采集服务未启用");
        int runningJobs = databaseUp ? runningJobs() : 0;
        String status = databaseUp && (!collectorEnabled || collector.up()) ? "UP" : "DEGRADED";
        return new XhsConsoleHealth(status, databaseUp, collectorEnabled, collector.up(),
                collector.message(), runningJobs, Instant.now());
    }

    private boolean databaseUp() {
        try {
            Integer value = jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            return value != null && value == 1;
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private int runningJobs() {
        try {
            Integer value = jdbcTemplate.queryForObject("""
                    SELECT COUNT(*) FROM xhs_collection_jobs
                    WHERE status IN ('PENDING', 'SUBMITTED', 'RUNNING')
                    """, Integer.class);
            return value == null ? 0 : value;
        } catch (RuntimeException exception) {
            return 0;
        }
    }

    private CollectorHealth collectorUp() {
        if (collectorProperties.baseUrl().isBlank()) {
            return new CollectorHealth(false, "未配置采集服务地址");
        }
        try {
            restClientBuilder.clone().baseUrl(collectorProperties.baseUrl()).build().get()
                    .uri("/health")
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (request, response) -> {
                        throw new IllegalStateException("HTTP " + response.getStatusCode().value());
                    })
                    .toBodilessEntity();
            return new CollectorHealth(true, "采集服务正常");
        } catch (RuntimeException exception) {
            return new CollectorHealth(false, "无法连接采集服务");
        }
    }

    private record CollectorHealth(boolean up, String message) {
    }
}
