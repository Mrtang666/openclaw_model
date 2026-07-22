package com.example.spring.wechat.memory;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(args = "/status")
@ActiveProfiles("test")
class WechatMemorySchemaTests {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void springBootTestsUseIsolatedTestDatabase() {
        TestDatabaseGuard.assertUsingTestDatabase(jdbcTemplate);
    }

    @Test
    void flywayCreatesWechatMemoryTables() {
        List<String> tables = jdbcTemplate.queryForList(
                """
                        SELECT table_name
                        FROM information_schema.tables
                        WHERE table_schema = DATABASE()
                        """,
                String.class);

        assertThat(tables).contains(
                "users",
                "conversations",
                "conversation_messages",
                "conversation_states",
                "user_preferences",
                "conversation_summaries",
                "tool_execution_logs",
                "wechat_images");
    }
}
