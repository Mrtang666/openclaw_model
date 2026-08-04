ALTER TABLE xhs_collection_jobs
    ADD COLUMN poll_lock_token VARCHAR(64) NULL,
    ADD COLUMN poll_locked_at DATETIME(3) NULL,
    ADD KEY idx_xhs_collection_jobs_poll_claim (poll_lock_token, poll_locked_at);
