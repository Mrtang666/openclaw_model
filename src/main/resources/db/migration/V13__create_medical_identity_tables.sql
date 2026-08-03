CREATE TABLE medical_organizations (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    organization_code VARCHAR(64) NOT NULL,
    organization_name VARCHAR(255) NOT NULL,
    organization_type VARCHAR(32) NOT NULL,
    status VARCHAR(16) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    UNIQUE KEY uk_medical_organizations_code (organization_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE medical_users (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_code VARCHAR(64) NOT NULL,
    display_name VARCHAR(255) NOT NULL,
    status VARCHAR(16) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    first_seen_at DATETIME(3) NOT NULL,
    last_active_at DATETIME(3) NOT NULL,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    UNIQUE KEY uk_medical_users_code (user_code),
    KEY idx_medical_users_status_active (status, last_active_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE medical_user_wechat_bindings (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    connection_id VARCHAR(64) NOT NULL,
    from_user_id VARCHAR(255) NOT NULL,
    latest_session_key VARCHAR(512) NOT NULL,
    status VARCHAR(16) NOT NULL,
    first_seen_at DATETIME(3) NOT NULL,
    last_seen_at DATETIME(3) NOT NULL,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    UNIQUE KEY uk_medical_wechat_connection_user (connection_id, from_user_id),
    KEY idx_medical_wechat_user_status (user_id, status),
    CONSTRAINT fk_medical_wechat_user
        FOREIGN KEY (user_id) REFERENCES medical_users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE medical_user_contacts (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    contact_type VARCHAR(16) NOT NULL,
    contact_value_encrypted TEXT NOT NULL,
    contact_value_hash CHAR(64) NOT NULL,
    is_primary BOOLEAN NOT NULL DEFAULT FALSE,
    verified BOOLEAN NOT NULL DEFAULT FALSE,
    status VARCHAR(16) NOT NULL,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    UNIQUE KEY uk_medical_contact_user_type_hash (user_id, contact_type, contact_value_hash),
    KEY idx_medical_contact_user_status (user_id, status),
    CONSTRAINT fk_medical_contact_user
        FOREIGN KEY (user_id) REFERENCES medical_users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE medical_user_roles (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    role_code VARCHAR(32) NOT NULL,
    status VARCHAR(16) NOT NULL,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    UNIQUE KEY uk_medical_user_role (user_id, role_code),
    KEY idx_medical_roles_role_status (role_code, status),
    CONSTRAINT fk_medical_role_user
        FOREIGN KEY (user_id) REFERENCES medical_users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE medical_organization_members (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    organization_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    member_role VARCHAR(32) NOT NULL,
    department_name VARCHAR(255) NULL,
    professional_title VARCHAR(255) NULL,
    status VARCHAR(16) NOT NULL,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    UNIQUE KEY uk_medical_org_member_role (organization_id, user_id, member_role),
    KEY idx_medical_org_members_user (user_id, status),
    CONSTRAINT fk_medical_org_member_org
        FOREIGN KEY (organization_id) REFERENCES medical_organizations(id) ON DELETE CASCADE,
    CONSTRAINT fk_medical_org_member_user
        FOREIGN KEY (user_id) REFERENCES medical_users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE medical_patient_organization_relations (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    organization_id BIGINT NOT NULL,
    patient_user_id BIGINT NOT NULL,
    care_unit VARCHAR(255) NULL,
    status VARCHAR(16) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    UNIQUE KEY uk_medical_patient_org (organization_id, patient_user_id),
    KEY idx_medical_patient_org_patient (patient_user_id, status),
    CONSTRAINT fk_medical_patient_org_org
        FOREIGN KEY (organization_id) REFERENCES medical_organizations(id) ON DELETE CASCADE,
    CONSTRAINT fk_medical_patient_org_patient
        FOREIGN KEY (patient_user_id) REFERENCES medical_users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE medical_patient_relations (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    viewer_user_id BIGINT NOT NULL,
    patient_user_id BIGINT NOT NULL,
    relation_role VARCHAR(32) NOT NULL,
    relation_label VARCHAR(255) NULL,
    status VARCHAR(16) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    UNIQUE KEY uk_medical_patient_relation (viewer_user_id, patient_user_id, relation_role),
    KEY idx_medical_relation_patient_status (patient_user_id, status),
    KEY idx_medical_relation_viewer_status (viewer_user_id, status),
    CONSTRAINT fk_medical_relation_viewer
        FOREIGN KEY (viewer_user_id) REFERENCES medical_users(id) ON DELETE CASCADE,
    CONSTRAINT fk_medical_relation_patient
        FOREIGN KEY (patient_user_id) REFERENCES medical_users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE medical_consents (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    patient_user_id BIGINT NOT NULL,
    granted_by_user_id BIGINT NOT NULL,
    consent_scope VARCHAR(64) NOT NULL,
    grantee_type VARCHAR(16) NOT NULL,
    grantee_id BIGINT NOT NULL,
    status VARCHAR(16) NOT NULL,
    granted_at DATETIME(3) NOT NULL,
    expires_at DATETIME(3) NULL,
    revoked_at DATETIME(3) NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    UNIQUE KEY uk_medical_consent_scope_grantee
        (patient_user_id, consent_scope, grantee_type, grantee_id),
    KEY idx_medical_consent_patient_status (patient_user_id, status, expires_at),
    CONSTRAINT fk_medical_consent_patient
        FOREIGN KEY (patient_user_id) REFERENCES medical_users(id) ON DELETE CASCADE,
    CONSTRAINT fk_medical_consent_grantor
        FOREIGN KEY (granted_by_user_id) REFERENCES medical_users(id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE medical_permission_definitions (
    permission_code VARCHAR(64) PRIMARY KEY,
    permission_name VARCHAR(128) NOT NULL,
    description VARCHAR(500) NOT NULL,
    created_at DATETIME(3) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE medical_relation_permissions (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    relation_id BIGINT NOT NULL,
    permission_code VARCHAR(64) NOT NULL,
    expires_at DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL,
    UNIQUE KEY uk_medical_relation_permission (relation_id, permission_code),
    KEY idx_medical_relation_permission_expiry (permission_code, expires_at),
    CONSTRAINT fk_medical_relation_permission_relation
        FOREIGN KEY (relation_id) REFERENCES medical_patient_relations(id) ON DELETE CASCADE,
    CONSTRAINT fk_medical_relation_permission_definition
        FOREIGN KEY (permission_code) REFERENCES medical_permission_definitions(permission_code) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE medical_web_sessions (
    token_hash CHAR(64) PRIMARY KEY,
    user_id BIGINT NOT NULL,
    active_role VARCHAR(32) NOT NULL,
    expires_at DATETIME(3) NOT NULL,
    last_seen_at DATETIME(3) NOT NULL,
    revoked_at DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL,
    KEY idx_medical_sessions_user_expiry (user_id, expires_at),
    CONSTRAINT fk_medical_session_user
        FOREIGN KEY (user_id) REFERENCES medical_users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO medical_permission_definitions
    (permission_code, permission_name, description, created_at)
VALUES
    ('PATIENT_STATUS_READ', '查看患者状态', '查看患者状态摘要和趋势', CURRENT_TIMESTAMP(3)),
    ('PATIENT_MEMORY_READ', '查看患者记忆', '查看患者授权共享的可信记忆', CURRENT_TIMESTAMP(3)),
    ('PATIENT_MEMORY_CONFIRM', '确认患者记忆', '确认、修正或拒绝待确认记忆', CURRENT_TIMESTAMP(3)),
    ('PATIENT_CHECKIN_READ', '查看患者签到', '查看患者每日签到记录', CURRENT_TIMESTAMP(3)),
    ('PATIENT_ALERT_READ', '查看患者告警', '查看患者安全告警和处理记录', CURRENT_TIMESTAMP(3)),
    ('PATIENT_ALERT_ACK', '处理患者告警', '确认、处理或标记患者安全告警', CURRENT_TIMESTAMP(3)),
    ('PATIENT_REPORT_READ', '查看患者报告', '查看患者日报和周报', CURRENT_TIMESTAMP(3));
