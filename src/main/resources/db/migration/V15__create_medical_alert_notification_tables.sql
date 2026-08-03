CREATE TABLE medical_safety_alerts (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    patient_user_id BIGINT NOT NULL,
    alert_type VARCHAR(64) NOT NULL,
    severity VARCHAR(16) NOT NULL,
    status VARCHAR(32) NOT NULL,
    evidence_type VARCHAR(32) NOT NULL,
    evidence_id BIGINT NULL,
    evidence_text VARCHAR(1000) NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    detected_at DATETIME(3) NOT NULL,
    acknowledged_by_user_id BIGINT NULL,
    acknowledged_at DATETIME(3) NULL,
    resolved_by_user_id BIGINT NULL,
    resolved_at DATETIME(3) NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    UNIQUE KEY uk_medical_alert_idempotency (idempotency_key),
    KEY idx_medical_alert_patient_status_time (patient_user_id, status, detected_at),
    KEY idx_medical_alert_severity_status (severity, status, detected_at),
    CONSTRAINT fk_medical_alert_patient
        FOREIGN KEY (patient_user_id) REFERENCES medical_users(id) ON DELETE CASCADE,
    CONSTRAINT fk_medical_alert_ack_user
        FOREIGN KEY (acknowledged_by_user_id) REFERENCES medical_users(id) ON DELETE SET NULL,
    CONSTRAINT fk_medical_alert_resolve_user
        FOREIGN KEY (resolved_by_user_id) REFERENCES medical_users(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE medical_alert_events (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    alert_id BIGINT NOT NULL,
    actor_user_id BIGINT NULL,
    event_type VARCHAR(32) NOT NULL,
    note VARCHAR(1000) NULL,
    created_at DATETIME(3) NOT NULL,
    KEY idx_medical_alert_events_alert_time (alert_id, created_at),
    CONSTRAINT fk_medical_alert_event_alert
        FOREIGN KEY (alert_id) REFERENCES medical_safety_alerts(id) ON DELETE CASCADE,
    CONSTRAINT fk_medical_alert_event_actor
        FOREIGN KEY (actor_user_id) REFERENCES medical_users(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE medical_notifications (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    to_user_id BIGINT NOT NULL,
    patient_user_id BIGINT NULL,
    connection_id VARCHAR(64) NOT NULL,
    recipient_id VARCHAR(255) NOT NULL,
    notification_type VARCHAR(64) NOT NULL,
    channel VARCHAR(16) NOT NULL,
    content MEDIUMTEXT NOT NULL,
    status VARCHAR(16) NOT NULL,
    scheduled_at DATETIME(3) NOT NULL,
    sent_at DATETIME(3) NULL,
    retry_count INT NOT NULL DEFAULT 0,
    max_retry_count INT NOT NULL DEFAULT 3,
    last_error VARCHAR(1000) NULL,
    locked_at DATETIME(3) NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    UNIQUE KEY uk_medical_notification_idempotency (idempotency_key),
    KEY idx_medical_notification_due (status, scheduled_at),
    KEY idx_medical_notification_user (to_user_id, created_at),
    CONSTRAINT fk_medical_notification_user
        FOREIGN KEY (to_user_id) REFERENCES medical_users(id) ON DELETE CASCADE,
    CONSTRAINT fk_medical_notification_patient
        FOREIGN KEY (patient_user_id) REFERENCES medical_users(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE medical_access_audit_logs (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    actor_user_id BIGINT NULL,
    actor_role VARCHAR(32) NULL,
    target_patient_user_id BIGINT NULL,
    action VARCHAR(64) NOT NULL,
    permission_code VARCHAR(64) NULL,
    resource_type VARCHAR(64) NULL,
    resource_id VARCHAR(128) NULL,
    result VARCHAR(16) NOT NULL,
    reason VARCHAR(1000) NULL,
    request_id VARCHAR(64) NOT NULL,
    created_at DATETIME(3) NOT NULL,
    KEY idx_medical_audit_patient_time (target_patient_user_id, created_at),
    KEY idx_medical_audit_actor_time (actor_user_id, created_at),
    KEY idx_medical_audit_request (request_id),
    CONSTRAINT fk_medical_audit_actor
        FOREIGN KEY (actor_user_id) REFERENCES medical_users(id) ON DELETE SET NULL,
    CONSTRAINT fk_medical_audit_patient
        FOREIGN KEY (target_patient_user_id) REFERENCES medical_users(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
