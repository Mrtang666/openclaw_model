CREATE TABLE xhs_report_schedules (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    project_id BIGINT NOT NULL,
    name VARCHAR(255) NOT NULL,
    frequency VARCHAR(16) NOT NULL,
    run_time TIME NOT NULL,
    day_of_week INT NULL,
    day_of_month INT NULL,
    timezone VARCHAR(64) NOT NULL,
    formats VARCHAR(32) NOT NULL,
    collect_before_report BOOLEAN NOT NULL DEFAULT TRUE,
    collection_limit INT NOT NULL DEFAULT 20,
    top_post_limit INT NOT NULL DEFAULT 10,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    next_run_at DATETIME(3) NULL,
    last_run_at DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    UNIQUE KEY uk_xhs_report_schedule_project_name (project_id, name),
    KEY idx_xhs_report_schedule_due (enabled, next_run_at),
    CONSTRAINT fk_xhs_report_schedule_project FOREIGN KEY (project_id)
        REFERENCES xhs_monitor_projects(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE xhs_report_recipients (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    schedule_id BIGINT NOT NULL,
    channel VARCHAR(16) NOT NULL,
    connection_id VARCHAR(64) NOT NULL DEFAULT '',
    target_value VARCHAR(320) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME(3) NOT NULL,
    UNIQUE KEY uk_xhs_report_recipient_target (schedule_id, channel, connection_id, target_value),
    KEY idx_xhs_report_recipient_schedule (schedule_id, enabled),
    CONSTRAINT fk_xhs_report_recipient_schedule FOREIGN KEY (schedule_id)
        REFERENCES xhs_report_schedules(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE xhs_report_runs (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    schedule_id BIGINT NOT NULL,
    scheduled_for DATETIME(3) NOT NULL,
    period_start DATETIME(3) NOT NULL,
    period_end DATETIME(3) NOT NULL,
    status VARCHAR(24) NOT NULL,
    stage_started_at DATETIME(3) NULL,
    deadline_at DATETIME(3) NULL,
    partial_reason TEXT NULL,
    error_message TEXT NULL,
    started_at DATETIME(3) NULL,
    finished_at DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    UNIQUE KEY uk_xhs_report_run_schedule_time (schedule_id, scheduled_for),
    KEY idx_xhs_report_run_status_updated (status, updated_at),
    KEY idx_xhs_report_run_schedule_created (schedule_id, created_at),
    CONSTRAINT fk_xhs_report_run_schedule FOREIGN KEY (schedule_id)
        REFERENCES xhs_report_schedules(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE xhs_report_artifacts (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    run_id BIGINT NOT NULL,
    format VARCHAR(16) NOT NULL,
    file_name VARCHAR(255) NOT NULL,
    storage_key VARCHAR(1000) NOT NULL,
    content_type VARCHAR(191) NOT NULL,
    size_bytes BIGINT NOT NULL,
    sha256 VARCHAR(64) NOT NULL,
    expires_at DATETIME(3) NOT NULL,
    created_at DATETIME(3) NOT NULL,
    UNIQUE KEY uk_xhs_report_artifact_run_format (run_id, format),
    KEY idx_xhs_report_artifact_expiry (expires_at),
    CONSTRAINT fk_xhs_report_artifact_run FOREIGN KEY (run_id)
        REFERENCES xhs_report_runs(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE xhs_report_deliveries (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    run_id BIGINT NOT NULL,
    recipient_id BIGINT NOT NULL,
    status VARCHAR(16) NOT NULL,
    attempt_count INT NOT NULL DEFAULT 0,
    next_attempt_at DATETIME(3) NOT NULL,
    last_error TEXT NULL,
    sent_at DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    UNIQUE KEY uk_xhs_report_delivery_run_recipient (run_id, recipient_id),
    KEY idx_xhs_report_delivery_pending (status, next_attempt_at),
    CONSTRAINT fk_xhs_report_delivery_run FOREIGN KEY (run_id)
        REFERENCES xhs_report_runs(id),
    CONSTRAINT fk_xhs_report_delivery_recipient FOREIGN KEY (recipient_id)
        REFERENCES xhs_report_recipients(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
