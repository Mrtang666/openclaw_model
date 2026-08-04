CREATE TABLE xhs_analysis_executions (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    post_id BIGINT NOT NULL,
    analysis_version VARCHAR(64) NOT NULL,
    model_name VARCHAR(128) NOT NULL,
    status VARCHAR(16) NOT NULL,
    prompt_tokens INT NOT NULL DEFAULT 0,
    completion_tokens INT NOT NULL DEFAULT 0,
    total_tokens INT NOT NULL DEFAULT 0,
    duration_ms BIGINT NOT NULL DEFAULT 0,
    error_message VARCHAR(1000) NULL,
    created_at DATETIME(3) NOT NULL,
    KEY idx_xhs_analysis_execution_created (created_at),
    KEY idx_xhs_analysis_execution_post (post_id, created_at),
    KEY idx_xhs_analysis_execution_status (status, created_at),
    CONSTRAINT fk_xhs_analysis_execution_post FOREIGN KEY (post_id) REFERENCES xhs_posts(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
