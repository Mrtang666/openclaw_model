CREATE TABLE medical_users (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_code VARCHAR(64) NOT NULL,
    from_user_id VARCHAR(191) NOT NULL,
    display_name VARCHAR(128) NOT NULL,
    latest_session_key VARCHAR(512) NULL,
    status VARCHAR(32) NOT NULL,
    first_seen_at DATETIME(3) NOT NULL,
    last_active_at DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    UNIQUE KEY uk_medical_users_user_code (user_code),
    UNIQUE KEY uk_medical_users_from_user_id (from_user_id),
    KEY idx_medical_users_status (status),
    KEY idx_medical_users_last_active (last_active_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE medical_user_contacts (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    contact_type VARCHAR(32) NOT NULL,
    contact_value VARCHAR(255) NOT NULL,
    is_primary TINYINT(1) NOT NULL DEFAULT 0,
    verified TINYINT(1) NOT NULL DEFAULT 0,
    status VARCHAR(32) NOT NULL,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    UNIQUE KEY uk_medical_contacts_value (user_id, contact_type, contact_value),
    KEY idx_medical_contacts_user (user_id),
    KEY idx_medical_contacts_value (contact_type, contact_value),
    CONSTRAINT fk_medical_contacts_user
        FOREIGN KEY (user_id) REFERENCES medical_users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE medical_user_roles (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    role_code VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    UNIQUE KEY uk_medical_roles_user_role (user_id, role_code),
    KEY idx_medical_roles_role_status (role_code, status),
    CONSTRAINT fk_medical_roles_user
        FOREIGN KEY (user_id) REFERENCES medical_users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE medical_login_sessions (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    connection_id VARCHAR(191) NOT NULL,
    login_session_id VARCHAR(191) NOT NULL,
    requested_role VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    bound_user_id BIGINT NULL,
    created_at DATETIME(3) NOT NULL,
    bound_at DATETIME(3) NULL,
    UNIQUE KEY uk_medical_login_session_id (login_session_id),
    KEY idx_medical_login_connection (connection_id, status),
    KEY idx_medical_login_bound_user (bound_user_id),
    CONSTRAINT fk_medical_login_bound_user
        FOREIGN KEY (bound_user_id) REFERENCES medical_users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE medical_patient_relations (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    viewer_user_id BIGINT NOT NULL,
    patient_user_id BIGINT NOT NULL,
    relation_role VARCHAR(32) NOT NULL,
    relation_label VARCHAR(128) NULL,
    status VARCHAR(32) NOT NULL,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    UNIQUE KEY uk_medical_relation_unique (viewer_user_id, patient_user_id, relation_role),
    KEY idx_medical_relation_viewer (viewer_user_id, status),
    KEY idx_medical_relation_patient (patient_user_id, relation_role, status),
    CONSTRAINT fk_medical_relation_viewer
        FOREIGN KEY (viewer_user_id) REFERENCES medical_users(id),
    CONSTRAINT fk_medical_relation_patient
        FOREIGN KEY (patient_user_id) REFERENCES medical_users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE medical_permission_definitions (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    permission_code VARCHAR(64) NOT NULL,
    permission_name VARCHAR(128) NOT NULL,
    description VARCHAR(500) NULL,
    created_at DATETIME(3) NOT NULL,
    UNIQUE KEY uk_medical_permission_code (permission_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE medical_relation_permissions (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    relation_id BIGINT NOT NULL,
    permission_code VARCHAR(64) NOT NULL,
    expires_at DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL,
    UNIQUE KEY uk_medical_relation_permission (relation_id, permission_code),
    KEY idx_medical_relation_permission_code (permission_code),
    KEY idx_medical_relation_permission_expires (expires_at),
    CONSTRAINT fk_medical_relation_perm_relation
        FOREIGN KEY (relation_id) REFERENCES medical_patient_relations(id),
    CONSTRAINT fk_medical_relation_perm_code
        FOREIGN KEY (permission_code) REFERENCES medical_permission_definitions(permission_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE medical_care_plans (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    patient_user_id BIGINT NOT NULL,
    doctor_user_id BIGINT NOT NULL,
    title VARCHAR(255) NOT NULL,
    status VARCHAR(32) NOT NULL,
    active_version_id BIGINT NULL,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    activated_at DATETIME(3) NULL,
    KEY idx_medical_plans_patient_status (patient_user_id, status),
    KEY idx_medical_plans_doctor_status (doctor_user_id, status),
    KEY idx_medical_plans_active_version (active_version_id),
    CONSTRAINT fk_medical_plans_patient
        FOREIGN KEY (patient_user_id) REFERENCES medical_users(id),
    CONSTRAINT fk_medical_plans_doctor
        FOREIGN KEY (doctor_user_id) REFERENCES medical_users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE medical_care_plan_versions (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    plan_id BIGINT NOT NULL,
    version_no INT NOT NULL,
    doctor_original_text MEDIUMTEXT NULL,
    bot_refined_text MEDIUMTEXT NULL,
    doctor_confirmed_text MEDIUMTEXT NULL,
    review_status VARCHAR(32) NOT NULL,
    created_by_user_id BIGINT NOT NULL,
    created_at DATETIME(3) NOT NULL,
    confirmed_at DATETIME(3) NULL,
    UNIQUE KEY uk_medical_plan_version (plan_id, version_no),
    KEY idx_medical_plan_versions_status (plan_id, review_status),
    KEY idx_medical_plan_versions_creator (created_by_user_id),
    CONSTRAINT fk_medical_versions_plan
        FOREIGN KEY (plan_id) REFERENCES medical_care_plans(id),
    CONSTRAINT fk_medical_versions_creator
        FOREIGN KEY (created_by_user_id) REFERENCES medical_users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

ALTER TABLE medical_care_plans
    ADD CONSTRAINT fk_medical_plans_active_version
        FOREIGN KEY (active_version_id) REFERENCES medical_care_plan_versions(id);

CREATE TABLE medical_plan_tasks (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    plan_id BIGINT NOT NULL,
    patient_user_id BIGINT NOT NULL,
    created_by_user_id BIGINT NOT NULL,
    task_type VARCHAR(64) NOT NULL,
    task_title VARCHAR(255) NOT NULL,
    task_detail TEXT NULL,
    schedule_rule VARCHAR(512) NOT NULL,
    need_confirmation TINYINT(1) NOT NULL DEFAULT 1,
    status VARCHAR(32) NOT NULL,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    KEY idx_medical_tasks_plan_status (plan_id, status),
    KEY idx_medical_tasks_patient_status (patient_user_id, status),
    KEY idx_medical_tasks_creator (created_by_user_id),
    CONSTRAINT fk_medical_tasks_plan
        FOREIGN KEY (plan_id) REFERENCES medical_care_plans(id),
    CONSTRAINT fk_medical_tasks_patient
        FOREIGN KEY (patient_user_id) REFERENCES medical_users(id),
    CONSTRAINT fk_medical_tasks_creator
        FOREIGN KEY (created_by_user_id) REFERENCES medical_users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE medical_task_instances (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    task_id BIGINT NOT NULL,
    patient_user_id BIGINT NOT NULL,
    due_at DATETIME(3) NOT NULL,
    reminded_at DATETIME(3) NULL,
    status VARCHAR(32) NOT NULL,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    UNIQUE KEY uk_medical_task_instance_due (task_id, due_at),
    KEY idx_medical_instances_patient_due (patient_user_id, due_at),
    KEY idx_medical_instances_status_due (status, due_at),
    CONSTRAINT fk_medical_instances_task
        FOREIGN KEY (task_id) REFERENCES medical_plan_tasks(id),
    CONSTRAINT fk_medical_instances_patient
        FOREIGN KEY (patient_user_id) REFERENCES medical_users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE medical_task_checkins (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    task_instance_id BIGINT NOT NULL,
    patient_user_id BIGINT NOT NULL,
    checkin_text TEXT NULL,
    checkin_status VARCHAR(32) NOT NULL,
    bot_analysis TEXT NULL,
    created_at DATETIME(3) NOT NULL,
    KEY idx_medical_checkins_instance (task_instance_id, created_at),
    KEY idx_medical_checkins_patient (patient_user_id, created_at),
    CONSTRAINT fk_medical_checkins_instance
        FOREIGN KEY (task_instance_id) REFERENCES medical_task_instances(id),
    CONSTRAINT fk_medical_checkins_patient
        FOREIGN KEY (patient_user_id) REFERENCES medical_users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE medical_family_info_subscriptions (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    family_user_id BIGINT NOT NULL,
    patient_user_id BIGINT NOT NULL,
    schedule_rule VARCHAR(512) NOT NULL,
    info_scope VARCHAR(255) NOT NULL,
    status VARCHAR(32) NOT NULL,
    last_sent_at DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    UNIQUE KEY uk_medical_family_subscription (family_user_id, patient_user_id, info_scope),
    KEY idx_medical_family_sub_patient (patient_user_id, status),
    KEY idx_medical_family_sub_next (status, last_sent_at),
    CONSTRAINT fk_medical_family_sub_family
        FOREIGN KEY (family_user_id) REFERENCES medical_users(id),
    CONSTRAINT fk_medical_family_sub_patient
        FOREIGN KEY (patient_user_id) REFERENCES medical_users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE medical_patient_status_snapshots (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    patient_user_id BIGINT NOT NULL,
    generated_for_user_id BIGINT NOT NULL,
    summary_text MEDIUMTEXT NOT NULL,
    risk_level VARCHAR(32) NOT NULL,
    source_range_start DATETIME(3) NULL,
    source_range_end DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL,
    KEY idx_medical_snapshots_patient_created (patient_user_id, created_at),
    KEY idx_medical_snapshots_viewer_created (generated_for_user_id, created_at),
    KEY idx_medical_snapshots_risk (risk_level, created_at),
    CONSTRAINT fk_medical_snapshots_patient
        FOREIGN KEY (patient_user_id) REFERENCES medical_users(id),
    CONSTRAINT fk_medical_snapshots_viewer
        FOREIGN KEY (generated_for_user_id) REFERENCES medical_users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE medical_email_messages (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    patient_user_id BIGINT NOT NULL,
    from_user_id BIGINT NOT NULL,
    to_user_id BIGINT NOT NULL,
    email_to VARCHAR(255) NOT NULL,
    email_subject VARCHAR(255) NOT NULL,
    original_text MEDIUMTEXT NULL,
    bot_refined_text MEDIUMTEXT NULL,
    send_status VARCHAR(32) NOT NULL,
    error_message TEXT NULL,
    created_at DATETIME(3) NOT NULL,
    sent_at DATETIME(3) NULL,
    KEY idx_medical_email_patient_created (patient_user_id, created_at),
    KEY idx_medical_email_from_created (from_user_id, created_at),
    KEY idx_medical_email_to_created (to_user_id, created_at),
    KEY idx_medical_email_status (send_status, created_at),
    CONSTRAINT fk_medical_email_patient
        FOREIGN KEY (patient_user_id) REFERENCES medical_users(id),
    CONSTRAINT fk_medical_email_from_user
        FOREIGN KEY (from_user_id) REFERENCES medical_users(id),
    CONSTRAINT fk_medical_email_to_user
        FOREIGN KEY (to_user_id) REFERENCES medical_users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE medical_notifications (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    to_user_id BIGINT NOT NULL,
    patient_user_id BIGINT NULL,
    notification_type VARCHAR(64) NOT NULL,
    channel VARCHAR(32) NOT NULL,
    content TEXT NOT NULL,
    status VARCHAR(32) NOT NULL,
    scheduled_at DATETIME(3) NULL,
    sent_at DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL,
    KEY idx_medical_notifications_to_status (to_user_id, status, scheduled_at),
    KEY idx_medical_notifications_patient (patient_user_id, created_at),
    KEY idx_medical_notifications_due (status, scheduled_at),
    CONSTRAINT fk_medical_notifications_to_user
        FOREIGN KEY (to_user_id) REFERENCES medical_users(id),
    CONSTRAINT fk_medical_notifications_patient
        FOREIGN KEY (patient_user_id) REFERENCES medical_users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE medical_access_audit_logs (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    actor_user_id BIGINT NOT NULL,
    target_patient_user_id BIGINT NOT NULL,
    action VARCHAR(64) NOT NULL,
    permission_code VARCHAR(64) NOT NULL,
    result VARCHAR(32) NOT NULL,
    reason VARCHAR(500) NULL,
    created_at DATETIME(3) NOT NULL,
    KEY idx_medical_audit_actor_created (actor_user_id, created_at),
    KEY idx_medical_audit_patient_created (target_patient_user_id, created_at),
    KEY idx_medical_audit_permission (permission_code, result, created_at),
    CONSTRAINT fk_medical_audit_actor
        FOREIGN KEY (actor_user_id) REFERENCES medical_users(id),
    CONSTRAINT fk_medical_audit_patient
        FOREIGN KEY (target_patient_user_id) REFERENCES medical_users(id),
    CONSTRAINT fk_medical_audit_permission
        FOREIGN KEY (permission_code) REFERENCES medical_permission_definitions(permission_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
