CREATE TABLE xhs_analysis_feedback (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    post_id BIGINT NOT NULL,
    feedback_type VARCHAR(32) NOT NULL,
    note VARCHAR(1000) NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'OPEN',
    created_at DATETIME(3) NOT NULL,
    resolved_at DATETIME(3) NULL,
    KEY idx_xhs_analysis_feedback_status (status, created_at),
    KEY idx_xhs_analysis_feedback_post (post_id, created_at),
    CONSTRAINT fk_xhs_analysis_feedback_post FOREIGN KEY (post_id) REFERENCES xhs_posts(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
