package com.example.spring.agent.trace;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(args = "/status")
@ActiveProfiles("test")
class JdbcAgentRunRepositoryTests {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private JdbcAgentRunRepository repository;

    @BeforeEach
    void clean() {
        assertUsingTestDatabase();
        jdbcTemplate.update("DELETE FROM agent_run_steps");
        jdbcTemplate.update("DELETE FROM agent_runs");
        repository = new JdbcAgentRunRepository(
                jdbcTemplate,
                Clock.fixed(Instant.parse("2026-08-03T06:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    void createsRunAppendsStepsAndCompletesIt() {
        AgentRunHandle handle = repository.createRun(
                "WECHAT",
                "session-1",
                "帮我查天气",
                "相关性：STRONG，预算：86400 tokens");

        repository.appendStep(
                handle,
                AgentRunStepType.MODEL_ROUND,
                AgentRunStepStatus.SUCCESS,
                1,
                "",
                "messages=2",
                "tool_calls=[weather]",
                Map.of("tool_count", 1));
        repository.appendStep(
                handle,
                AgentRunStepType.TOOL_RESULT,
                AgentRunStepStatus.SUCCESS,
                1,
                "weather",
                "city=杭州",
                "杭州晴",
                Map.of("status", "SUCCESS"));
        repository.completeRun(
                handle,
                AgentRunStatus.SUCCEEDED,
                "FINAL_ANSWER",
                "杭州今天晴，适合出门。");

        Integer runs = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM agent_runs", Integer.class);
        Integer steps = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM agent_run_steps", Integer.class);
        String status = jdbcTemplate.queryForObject(
                "SELECT status FROM agent_runs WHERE id = ?",
                String.class,
                handle.runId());
        String stopReason = jdbcTemplate.queryForObject(
                "SELECT stop_reason FROM agent_runs WHERE id = ?",
                String.class,
                handle.runId());
        String toolName = jdbcTemplate.queryForObject(
                "SELECT tool_name FROM agent_run_steps WHERE run_id = ? AND step_type = 'TOOL_RESULT'",
                String.class,
                handle.runId());

        assertThat(runs).isEqualTo(1);
        assertThat(steps).isEqualTo(2);
        assertThat(status).isEqualTo("SUCCEEDED");
        assertThat(stopReason).isEqualTo("FINAL_ANSWER");
        assertThat(toolName).isEqualTo("weather");
        assertThat(handle.runKey()).startsWith("agent-run-");
    }

    private void assertUsingTestDatabase() {
        String database = jdbcTemplate.queryForObject("SELECT DATABASE()", String.class);
        if (!"openclaw_test".equals(database)) {
            throw new IllegalStateException("测试禁止清理非 openclaw_test 数据库，当前数据库：" + database);
        }
    }
}
