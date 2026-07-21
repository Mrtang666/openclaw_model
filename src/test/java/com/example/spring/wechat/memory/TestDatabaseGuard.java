package com.example.spring.wechat.memory;

import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 测试数据库保护器。
 * 所有会清理数据表的测试在执行 DELETE 前都必须先调用它，避免误连真实业务库。
 */
final class TestDatabaseGuard {

    private static final String EXPECTED_TEST_DATABASE = "openclaw_test";

    private TestDatabaseGuard() {
    }

    /**
     * 确认当前连接的是测试库 openclaw_test。
     * 如果连接到了 openclaw 等真实库，立即抛出异常并阻止后续清表语句执行。
     */
    static void assertUsingTestDatabase(JdbcTemplate jdbcTemplate) {
        String database = jdbcTemplate.queryForObject("SELECT DATABASE()", String.class);
        if (!EXPECTED_TEST_DATABASE.equals(database)) {
            throw new IllegalStateException(
                    "测试禁止清理非 " + EXPECTED_TEST_DATABASE + " 数据库，当前数据库=" + database);
        }
    }
}
