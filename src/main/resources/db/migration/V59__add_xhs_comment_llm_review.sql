ALTER TABLE xhs_comment_analysis_results
    ADD COLUMN analysis_version VARCHAR(64) NULL AFTER analysis_method,
    ADD COLUMN confidence DECIMAL(6,5) NOT NULL DEFAULT 0 AFTER analysis_version,
    ADD COLUMN summary VARCHAR(1000) NULL AFTER confidence,
    ADD COLUMN evidence_json JSON NULL AFTER summary,
    ADD COLUMN analysis_status VARCHAR(16) NOT NULL DEFAULT 'PENDING' AFTER evidence_json,
    ADD COLUMN attempt_count INT NOT NULL DEFAULT 0 AFTER analysis_status,
    ADD COLUMN error_message VARCHAR(2000) NULL AFTER attempt_count,
    ADD COLUMN analysis_lock_token VARCHAR(64) NULL AFTER error_message,
    ADD COLUMN analysis_locked_at DATETIME(3) NULL AFTER analysis_lock_token,
    ADD KEY idx_xhs_comment_review_claim(
        is_negative, analysis_status, attempt_count, analysis_locked_at, analyzed_at);

UPDATE xhs_comment_analysis_results
SET analysis_status = CASE WHEN is_negative THEN 'PENDING' ELSE 'SKIPPED' END;

CREATE TABLE xhs_comment_analysis_executions (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    batch_key VARCHAR(64) NOT NULL,
    analysis_version VARCHAR(64) NOT NULL,
    model_name VARCHAR(128) NOT NULL,
    comment_count INT NOT NULL,
    prompt_tokens INT NOT NULL DEFAULT 0,
    completion_tokens INT NOT NULL DEFAULT 0,
    total_tokens INT NOT NULL DEFAULT 0,
    duration_ms BIGINT NOT NULL DEFAULT 0,
    status VARCHAR(16) NOT NULL,
    error_message VARCHAR(2000) NULL,
    created_at DATETIME(3) NOT NULL,
    UNIQUE KEY uk_xhs_comment_analysis_batch (batch_key),
    KEY idx_xhs_comment_execution_created (created_at, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
