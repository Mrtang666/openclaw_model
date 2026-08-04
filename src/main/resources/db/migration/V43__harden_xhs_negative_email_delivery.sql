ALTER TABLE xhs_negative_post_deliveries
    ADD COLUMN deduplication_bucket BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN lock_token VARCHAR(64) NULL,
    ADD COLUMN locked_at DATETIME(3) NULL,
    DROP INDEX uk_xhs_negative_post_recipient,
    ADD UNIQUE KEY uk_xhs_negative_post_recipient_bucket (
        post_id, schedule_id, recipient_email, deduplication_bucket),
    ADD KEY idx_xhs_negative_delivery_lock (lock_token);
