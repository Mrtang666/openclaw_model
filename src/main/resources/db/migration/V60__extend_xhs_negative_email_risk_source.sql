ALTER TABLE xhs_negative_post_deliveries
    ADD COLUMN risk_source VARCHAR(64) NOT NULL DEFAULT 'POST' AFTER recipient_email,
    ADD COLUMN risk_score_snapshot INT NOT NULL DEFAULT 0 AFTER risk_source,
    ADD COLUMN risk_category_snapshot VARCHAR(128) NULL AFTER risk_score_snapshot,
    ADD COLUMN risk_summary_snapshot VARCHAR(1000) NULL AFTER risk_category_snapshot;
