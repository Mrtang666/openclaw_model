ALTER TABLE xhs_alert_deliveries
    ADD COLUMN claim_token VARCHAR(64) NULL,
    ADD COLUMN claimed_at DATETIME(3) NULL,
    ADD KEY idx_xhs_alert_deliveries_claim (status, claimed_at);
