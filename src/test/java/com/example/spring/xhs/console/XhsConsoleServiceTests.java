package com.example.spring.xhs.console;

import com.example.spring.xhs.ingestion.XhsCollectionCoordinator;
import com.example.spring.xhs.report.XhsDailyReportService;
import com.example.spring.xhs.report.XhsReportArtifactStorage;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class XhsConsoleServiceTests {

    @Test
    void prefixesGenericRiskQueryWithConfiguredProductTerm() {
        XhsConsoleService.ProjectView project = project("小天鹅洗衣机舆情", List.of("小天鹅洗衣机", "洗烘一体机"));

        assertThat(XhsConsoleService.normalizeCollectionQuery(project, "避雷"))
                .isEqualTo("小天鹅洗衣机 避雷");
        assertThat(XhsConsoleService.normalizeCollectionQuery(project, "小红书避雷帖子"))
                .isEqualTo("小天鹅洗衣机 小红书避雷帖子");
    }

    @Test
    void preservesQueryThatAlreadyContainsAProductName() {
        XhsConsoleService.ProjectView project = project("小天鹅洗衣机舆情", List.of("小天鹅洗衣机"));

        assertThat(XhsConsoleService.normalizeCollectionQuery(project, "小天鹅洗衣机 避雷"))
                .isEqualTo("小天鹅洗衣机 避雷");
    }

    @Test
    void fallsBackToProjectNameWhenConfiguredTermsAreGeneric() {
        XhsConsoleService.ProjectView project = project("小天鹅洗衣机", List.of("避雷", "差评"));

        assertThat(XhsConsoleService.normalizeCollectionQuery(project, "避雷"))
                .isEqualTo("小天鹅洗衣机 避雷");
    }

    @Test
    void rejectsProjectDeletionWhenConfirmationDoesNotMatch() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        XhsConsoleService service = new XhsConsoleService(
                jdbcTemplate,
                new ObjectMapper(),
                mock(XhsCollectionCoordinator.class),
                mock(XhsDailyReportService.class),
                mock(XhsReportArtifactStorage.class));

        assertThatThrownBy(() -> service.deleteProject("company-x", "other-project"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("完整项目标识");
        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void rejectsUnsupportedAnalysisFeedbackBeforeDatabaseAccess() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        XhsConsoleService service = new XhsConsoleService(
                jdbcTemplate,
                new ObjectMapper(),
                mock(XhsCollectionCoordinator.class),
                mock(XhsDailyReportService.class),
                mock(XhsReportArtifactStorage.class));

        assertThatThrownBy(() -> service.submitAnalysisFeedback(42L, "UNKNOWN", "invalid type"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("反馈类型");
        verifyNoInteractions(jdbcTemplate);
    }

    private XhsConsoleService.ProjectView project(String name, List<String> terms) {
        return new XhsConsoleService.ProjectView(
                1L, "project-key", name, "ACTIVE", terms, 0, 0, Instant.EPOCH, Instant.EPOCH);
    }
}
