ALTER TABLE xhs_posts
    ADD COLUMN analysis_lock_token VARCHAR(64) NULL,
    ADD COLUMN analysis_locked_at DATETIME(3) NULL,
    ADD KEY idx_xhs_posts_analysis_claim (analysis_lock_token, analysis_locked_at);
