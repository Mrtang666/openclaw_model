package com.example.spring.wechat.care;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(args = "/status")
@ActiveProfiles("test")
class MedicalCareSchemaTests {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void flywayCreatesMedicalCareTables() {
        assertUsingTestDatabase();
        List<String> tables = jdbcTemplate.queryForList("""
                SELECT table_name FROM information_schema.tables WHERE table_schema=DATABASE()
                """, String.class);

        assertThat(tables).contains(
                "medical_users",
                "medical_user_wechat_bindings",
                "medical_user_roles",
                "medical_organizations",
                "medical_organization_members",
                "medical_patient_organization_relations",
                "medical_patient_relations",
                "medical_consents",
                "medical_relation_permissions",
                "medical_web_sessions",
                "medical_memory_events",
                "medical_daily_checkins",
                "medical_care_observations",
                "medical_care_plans",
                "medical_care_plan_versions",
                "medical_care_task_templates",
                "medical_care_task_instances",
                "medical_care_task_events",
                "medical_safety_alerts",
                "medical_alert_events",
                "medical_notifications",
                "medical_access_audit_logs");
    }

    @Test
    void permissionDefinitionsAreSeeded() {
        assertUsingTestDatabase();
        List<String> permissions = jdbcTemplate.queryForList(
                "SELECT permission_code FROM medical_permission_definitions ORDER BY permission_code",
                String.class);
        assertThat(permissions).contains(
                "PATIENT_STATUS_READ",
                "PATIENT_MEMORY_READ",
                "PATIENT_MEMORY_CONFIRM",
                "PATIENT_CHECKIN_READ",
                "PATIENT_ALERT_READ",
                "PATIENT_ALERT_ACK",
                "PATIENT_REPORT_READ",
                "PATIENT_PLAN_READ",
                "PATIENT_PLAN_MANAGE",
                "PATIENT_PLAN_REVIEW",
                "PATIENT_TASK_READ",
                "PATIENT_TASK_UPDATE");
    }

    private void assertUsingTestDatabase() {
        assertThat(jdbcTemplate.queryForObject("SELECT DATABASE()", String.class))
                .isEqualTo("openclaw_test");
    }
}
