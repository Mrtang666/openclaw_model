CREATE TABLE medical_care_plans (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    patient_user_id BIGINT NOT NULL,
    plan_type VARCHAR(32) NOT NULL,
    title VARCHAR(255) NOT NULL,
    status VARCHAR(32) NOT NULL,
    clinical_review_required BOOLEAN NOT NULL DEFAULT TRUE,
    current_revision INT NOT NULL DEFAULT 1,
    created_by_user_id BIGINT NOT NULL,
    submitted_at DATETIME(3) NULL,
    reviewed_by_user_id BIGINT NULL,
    reviewed_at DATETIME(3) NULL,
    review_note VARCHAR(1000) NULL,
    activated_at DATETIME(3) NULL,
    ended_at DATETIME(3) NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    UNIQUE KEY uk_medical_care_plan_idempotency (idempotency_key),
    KEY idx_medical_care_plan_patient_status (patient_user_id, status, updated_at),
    CONSTRAINT fk_medical_care_plan_patient
        FOREIGN KEY (patient_user_id) REFERENCES medical_users(id) ON DELETE CASCADE,
    CONSTRAINT fk_medical_care_plan_creator
        FOREIGN KEY (created_by_user_id) REFERENCES medical_users(id) ON DELETE RESTRICT,
    CONSTRAINT fk_medical_care_plan_reviewer
        FOREIGN KEY (reviewed_by_user_id) REFERENCES medical_users(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE medical_care_plan_versions (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    plan_id BIGINT NOT NULL,
    revision INT NOT NULL,
    summary VARCHAR(2000) NOT NULL,
    instructions MEDIUMTEXT NOT NULL,
    effective_from DATE NOT NULL,
    effective_to DATE NULL,
    timezone VARCHAR(64) NOT NULL,
    authored_by_user_id BIGINT NOT NULL,
    created_at DATETIME(3) NOT NULL,
    UNIQUE KEY uk_medical_care_plan_revision (plan_id, revision),
    CONSTRAINT fk_medical_care_plan_version_plan
        FOREIGN KEY (plan_id) REFERENCES medical_care_plans(id) ON DELETE CASCADE,
    CONSTRAINT fk_medical_care_plan_version_author
        FOREIGN KEY (authored_by_user_id) REFERENCES medical_users(id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE medical_care_task_templates (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    plan_version_id BIGINT NOT NULL,
    task_type VARCHAR(32) NOT NULL,
    title VARCHAR(255) NOT NULL,
    instructions VARCHAR(4000) NOT NULL,
    schedule_type VARCHAR(16) NOT NULL,
    local_time TIME NOT NULL,
    scheduled_date DATE NULL,
    day_of_week TINYINT NULL,
    start_date DATE NOT NULL,
    end_date DATE NULL,
    grace_period_minutes INT NOT NULL DEFAULT 60,
    escalation_after_minutes INT NOT NULL DEFAULT 120,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    sort_order INT NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL,
    KEY idx_medical_task_template_version (plan_version_id, enabled, sort_order),
    CONSTRAINT fk_medical_task_template_version
        FOREIGN KEY (plan_version_id) REFERENCES medical_care_plan_versions(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE medical_care_task_instances (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    plan_id BIGINT NOT NULL,
    plan_version_id BIGINT NOT NULL,
    task_template_id BIGINT NOT NULL,
    patient_user_id BIGINT NOT NULL,
    scheduled_for DATE NOT NULL,
    due_at DATETIME(3) NOT NULL,
    status VARCHAR(16) NOT NULL,
    completed_by_user_id BIGINT NULL,
    completed_at DATETIME(3) NULL,
    result_note VARCHAR(1000) NULL,
    snooze_count INT NOT NULL DEFAULT 0,
    reminder_enqueued_at DATETIME(3) NULL,
    overdue_notified_at DATETIME(3) NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    UNIQUE KEY uk_medical_task_instance_idempotency (idempotency_key),
    KEY idx_medical_task_instance_patient_due (patient_user_id, due_at, status),
    KEY idx_medical_task_instance_reminder (status, reminder_enqueued_at, due_at),
    KEY idx_medical_task_instance_overdue (status, overdue_notified_at, due_at),
    CONSTRAINT fk_medical_task_instance_plan
        FOREIGN KEY (plan_id) REFERENCES medical_care_plans(id) ON DELETE CASCADE,
    CONSTRAINT fk_medical_task_instance_version
        FOREIGN KEY (plan_version_id) REFERENCES medical_care_plan_versions(id) ON DELETE RESTRICT,
    CONSTRAINT fk_medical_task_instance_template
        FOREIGN KEY (task_template_id) REFERENCES medical_care_task_templates(id) ON DELETE RESTRICT,
    CONSTRAINT fk_medical_task_instance_patient
        FOREIGN KEY (patient_user_id) REFERENCES medical_users(id) ON DELETE CASCADE,
    CONSTRAINT fk_medical_task_instance_completed_by
        FOREIGN KEY (completed_by_user_id) REFERENCES medical_users(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE medical_care_task_events (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    task_instance_id BIGINT NOT NULL,
    actor_user_id BIGINT NULL,
    event_type VARCHAR(32) NOT NULL,
    note VARCHAR(1000) NULL,
    previous_due_at DATETIME(3) NULL,
    current_due_at DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL,
    KEY idx_medical_task_event_task_time (task_instance_id, created_at),
    CONSTRAINT fk_medical_task_event_instance
        FOREIGN KEY (task_instance_id) REFERENCES medical_care_task_instances(id) ON DELETE CASCADE,
    CONSTRAINT fk_medical_task_event_actor
        FOREIGN KEY (actor_user_id) REFERENCES medical_users(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO medical_permission_definitions
    (permission_code, permission_name, description, created_at)
VALUES
    ('PATIENT_PLAN_READ', '查看照护计划', '查看患者已授权的照护计划及其版本', CURRENT_TIMESTAMP(3)),
    ('PATIENT_PLAN_MANAGE', '管理照护计划', '创建、提交或变更患者照护计划', CURRENT_TIMESTAMP(3)),
    ('PATIENT_PLAN_REVIEW', '审核照护计划', '审核并激活需要专业确认的照护计划', CURRENT_TIMESTAMP(3)),
    ('PATIENT_TASK_READ', '查看照护任务', '查看患者照护任务及执行状态', CURRENT_TIMESTAMP(3)),
    ('PATIENT_TASK_UPDATE', '更新照护任务', '代表患者完成或延后已授权的照护任务', CURRENT_TIMESTAMP(3));
