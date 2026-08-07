ALTER TABLE xhs_post_images
    ADD COLUMN analysis_version VARCHAR(64) NULL AFTER analysis_status,
    ADD COLUMN sentiment VARCHAR(16) NULL AFTER analysis_version,
    ADD COLUMN risk_score INT NOT NULL DEFAULT 0 AFTER sentiment,
    ADD COLUMN contains_product BOOLEAN NOT NULL DEFAULT FALSE AFTER risk_score,
    ADD COLUMN summary VARCHAR(1000) NULL AFTER contains_product,
    ADD COLUMN evidence_json JSON NULL AFTER summary,
    ADD COLUMN attempt_count INT NOT NULL DEFAULT 0 AFTER evidence_json,
    ADD COLUMN error_message VARCHAR(2000) NULL AFTER attempt_count,
    ADD COLUMN analyzed_at DATETIME(3) NULL AFTER error_message,
    ADD COLUMN analysis_lock_token VARCHAR(64) NULL AFTER analyzed_at,
    ADD COLUMN analysis_locked_at DATETIME(3) NULL AFTER analysis_lock_token,
    ADD KEY idx_xhs_image_analysis_claim(
        analysis_status, attempt_count, analysis_locked_at, last_collected_at),
    ADD KEY idx_xhs_image_analysis_cache(image_hash, analysis_version, analysis_status);
