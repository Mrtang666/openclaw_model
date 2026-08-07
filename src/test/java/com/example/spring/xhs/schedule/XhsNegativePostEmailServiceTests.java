package com.example.spring.xhs.schedule;

import com.example.spring.wechat.email.client.EmailClient;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class XhsNegativePostEmailServiceTests {

    @Test
    void rejectsRetryWithoutProjectScopeBeforeDatabaseAccess() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        XhsNegativePostEmailService service = new XhsNegativePostEmailService(
                jdbcTemplate, mock(EmailClient.class));

        assertThatThrownBy(() -> service.retry(12L, " "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("projectKey");
        verifyNoInteractions(jdbcTemplate);
    }
}
