CREATE TABLE xhs_image_analysis_executions (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    image_id BIGINT NOT NULL,
    post_id BIGINT NOT NULL,
    analysis_version VARCHAR(64) NOT NULL,
    model_name VARCHAR(128) NOT NULL,
    prompt_tokens INT NOT NULL DEFAULT 0,
    completion_tokens INT NOT NULL DEFAULT 0,
    total_tokens INT NOT NULL DEFAULT 0,
    duration_ms BIGINT NOT NULL DEFAULT 0,
    status VARCHAR(16) NOT NULL,
    error_message VARCHAR(2000) NULL,
    created_at DATETIME(3) NOT NULL,
    KEY idx_xhs_image_execution_post_created (post_id, created_at),
    KEY idx_xhs_image_execution_status_created (status, created_at),
    CONSTRAINT fk_xhs_image_execution_image FOREIGN KEY (image_id)
        REFERENCES xhs_post_images(id) ON DELETE CASCADE,
    CONSTRAINT fk_xhs_image_execution_post FOREIGN KEY (post_id)
        REFERENCES xhs_posts(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
