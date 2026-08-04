ALTER TABLE xhs_report_runs
    ADD COLUMN claim_token VARCHAR(64) NULL,
    ADD COLUMN claimed_at DATETIME(3) NULL,
    ADD KEY idx_xhs_report_runs_claim (status, claimed_at);
