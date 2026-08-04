ALTER TABLE xhs_collection_jobs
    ADD COLUMN next_poll_at DATETIME(3) NULL,
    ADD COLUMN last_polled_at DATETIME(3) NULL,
    ADD KEY idx_xhs_collection_jobs_poll_due (status, next_poll_at);
