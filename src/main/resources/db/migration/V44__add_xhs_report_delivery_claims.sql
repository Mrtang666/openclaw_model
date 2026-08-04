ALTER TABLE xhs_report_deliveries
    ADD COLUMN claim_token VARCHAR(64) NULL,
    ADD COLUMN claimed_at DATETIME(3) NULL,
    ADD KEY idx_xhs_report_deliveries_claim (status, claimed_at);
