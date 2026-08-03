CREATE TABLE medical_care_task_action_tokens (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    task_instance_id BIGINT NOT NULL,
    actor_user_id BIGINT NOT NULL,
    actor_role VARCHAR(32) NOT NULL,
    token_hash CHAR(64) NOT NULL,
    expires_at DATETIME(3) NOT NULL,
    used_at DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    UNIQUE KEY uk_care_task_action_token_hash (token_hash),
    KEY idx_care_task_action_token_task_actor (task_instance_id, actor_user_id, used_at),
    KEY idx_care_task_action_token_expiry (expires_at, used_at),
    CONSTRAINT fk_care_task_action_token_task
        FOREIGN KEY (task_instance_id) REFERENCES medical_care_task_instances(id) ON DELETE CASCADE,
    CONSTRAINT fk_care_task_action_token_actor
        FOREIGN KEY (actor_user_id) REFERENCES medical_users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
