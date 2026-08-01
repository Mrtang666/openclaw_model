ALTER TABLE xhs_collection_jobs
    ADD COLUMN query_text VARCHAR(500) NOT NULL DEFAULT '' AFTER source_type,
    ADD COLUMN external_job_id VARCHAR(191) NULL AFTER query_text,
    ADD COLUMN attempt_count INT NOT NULL DEFAULT 0 AFTER complete,
    ADD KEY idx_xhs_collection_jobs_status_started (status, started_at),
    ADD KEY idx_xhs_collection_jobs_external (external_job_id);
