CREATE TABLE reminder_tasks (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    session_key VARCHAR(512) NOT NULL,
    connection_id VARCHAR(64) NOT NULL,
    recipient_id VARCHAR(255) NOT NULL,
    title VARCHAR(255) NOT NULL,
    content TEXT NULL,
    repeat_type VARCHAR(16) NOT NULL,
    timezone VARCHAR(64) NOT NULL,
    next_execute_at DATETIME(3) NULL,
    status VARCHAR(16) NOT NULL,
    retry_count INT NOT NULL DEFAULT 0,
    max_retry_count INT NOT NULL DEFAULT 3,
    locked_at DATETIME(3) NULL,
    last_error VARCHAR(512) NULL,
    completed_at DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    KEY idx_reminder_tasks_due (status, next_execute_at),
    KEY idx_reminder_tasks_session (session_key, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE reminder_deliveries (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    task_id BIGINT NOT NULL,
    scheduled_at DATETIME(3) NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    status VARCHAR(16) NOT NULL,
    error_message VARCHAR(512) NULL,
    sent_at DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    UNIQUE KEY uk_reminder_delivery_idempotency (idempotency_key),
    KEY idx_reminder_deliveries_task (task_id, created_at),
    CONSTRAINT fk_reminder_deliveries_task
        FOREIGN KEY (task_id) REFERENCES reminder_tasks(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
