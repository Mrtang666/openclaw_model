ALTER TABLE xhs_report_schedules
    ADD COLUMN negative_email_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN negative_email_minimum_risk_score INT NOT NULL DEFAULT 60,
    ADD COLUMN negative_email_high_risk_only BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN negative_email_cooldown_minutes INT NOT NULL DEFAULT 30;

CREATE TABLE xhs_negative_post_deliveries (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    project_id BIGINT NOT NULL,
    post_id BIGINT NOT NULL,
    schedule_id BIGINT NOT NULL,
    recipient_email VARCHAR(320) NOT NULL,
    status VARCHAR(16) NOT NULL,
    attempt_count INT NOT NULL DEFAULT 0,
    next_attempt_at DATETIME(3) NOT NULL,
    last_error TEXT NULL,
    sent_at DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    UNIQUE KEY uk_xhs_negative_post_recipient (post_id, schedule_id, recipient_email),
    KEY idx_xhs_negative_delivery_pending (status, next_attempt_at),
    CONSTRAINT fk_xhs_negative_delivery_project FOREIGN KEY (project_id) REFERENCES xhs_monitor_projects(id),
    CONSTRAINT fk_xhs_negative_delivery_post FOREIGN KEY (post_id) REFERENCES xhs_posts(id),
    CONSTRAINT fk_xhs_negative_delivery_schedule FOREIGN KEY (schedule_id) REFERENCES xhs_report_schedules(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
