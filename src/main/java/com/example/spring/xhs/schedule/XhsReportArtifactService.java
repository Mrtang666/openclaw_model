package com.example.spring.xhs.schedule;

import com.example.spring.xhs.report.XhsReportArtifactStorage;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class XhsReportArtifactService {

    private final JdbcTemplate jdbcTemplate;
    private final XhsReportArtifactStorage storage;

    public XhsReportArtifactService(JdbcTemplate jdbcTemplate, XhsReportArtifactStorage storage) {
        this.jdbcTemplate = jdbcTemplate;
        this.storage = storage;
    }

    public Download download(long artifactId) {
        List<Metadata> values = jdbcTemplate.query("""
                SELECT storage_key, file_name, content_type FROM xhs_report_artifacts WHERE id = ?
                """, (rs, row) -> new Metadata(rs.getString("storage_key"), rs.getString("file_name"),
                rs.getString("content_type")), artifactId);
        if (values.isEmpty()) {
            throw new IllegalArgumentException("报告文件不存在");
        }
        Metadata value = values.get(0);
        return new Download(storage.read(value.storageKey()), value.fileName(), value.contentType());
    }

    private record Metadata(String storageKey, String fileName, String contentType) {
    }

    public record Download(byte[] bytes, String fileName, String contentType) {
        public Download {
            bytes = bytes == null ? new byte[0] : bytes.clone();
        }

        @Override
        public byte[] bytes() {
            return bytes.clone();
        }
    }
}
