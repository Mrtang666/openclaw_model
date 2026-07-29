package com.example.spring.xhs.console;

import com.example.spring.xhs.ingestion.XhsCollectionCoordinator;
import com.example.spring.xhs.report.XhsDailyReportService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class XhsConsoleServiceTests {

    @Test
    void rejectsProjectDeletionWhenConfirmationDoesNotMatch() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        XhsConsoleService service = new XhsConsoleService(
                jdbcTemplate,
                new ObjectMapper(),
                mock(XhsCollectionCoordinator.class),
                mock(XhsDailyReportService.class));

        assertThatThrownBy(() -> service.deleteProject("company-x", "other-project"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("完整项目标识");
        verifyNoInteractions(jdbcTemplate);
    }
}
