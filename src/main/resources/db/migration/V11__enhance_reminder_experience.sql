ALTER TABLE reminder_tasks
    ADD COLUMN parent_task_id BIGINT NULL AFTER id,
    ADD KEY idx_reminder_tasks_parent (parent_task_id),
    ADD CONSTRAINT fk_reminder_tasks_parent
        FOREIGN KEY (parent_task_id) REFERENCES reminder_tasks(id) ON DELETE SET NULL;

CREATE TABLE reminder_recipient_bindings (
    bot_id VARCHAR(255) NOT NULL,
    recipient_id VARCHAR(255) NOT NULL,
    connection_id VARCHAR(64) NOT NULL,
    session_key VARCHAR(512) NOT NULL,
    last_seen_at DATETIME(3) NOT NULL,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    PRIMARY KEY (bot_id, recipient_id),
    KEY idx_reminder_bindings_connection (connection_id, recipient_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
