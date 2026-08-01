CREATE TABLE xhs_report_run_collection_jobs (
    run_id BIGINT NOT NULL,
    job_key VARCHAR(64) NOT NULL,
    created_at DATETIME(3) NOT NULL,
    PRIMARY KEY (run_id, job_key),
    KEY idx_xhs_report_run_collection_job_key (job_key),
    CONSTRAINT fk_xhs_report_run_collection_run FOREIGN KEY (run_id)
        REFERENCES xhs_report_runs(id) ON DELETE CASCADE,
    CONSTRAINT fk_xhs_report_run_collection_job FOREIGN KEY (job_key)
        REFERENCES xhs_collection_jobs(job_key) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
