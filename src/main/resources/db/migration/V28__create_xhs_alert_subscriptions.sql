ALTER TABLE xhs_alert_rules
    ADD UNIQUE KEY uk_xhs_alert_rules_project_name (project_id, name);

CREATE TABLE xhs_alert_subscriptions (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    project_id BIGINT NOT NULL,
    rule_id BIGINT NOT NULL,
    channel VARCHAR(32) NOT NULL,
    connection_id VARCHAR(64) NOT NULL,
    recipient_id VARCHAR(191) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    UNIQUE KEY uk_xhs_alert_subscription_target (project_id, rule_id, channel, connection_id, recipient_id),
    KEY idx_xhs_alert_subscriptions_enabled (enabled, project_id),
    CONSTRAINT fk_xhs_alert_subscriptions_project FOREIGN KEY (project_id) REFERENCES xhs_monitor_projects(id),
    CONSTRAINT fk_xhs_alert_subscriptions_rule FOREIGN KEY (rule_id) REFERENCES xhs_alert_rules(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE xhs_alert_deliveries (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    alert_event_id BIGINT NOT NULL,
    subscription_id BIGINT NOT NULL,
    status VARCHAR(32) NOT NULL,
    attempt_count INT NOT NULL DEFAULT 0,
    last_error TEXT NULL,
    sent_at DATETIME(3) NULL,
    updated_at DATETIME(3) NOT NULL,
    UNIQUE KEY uk_xhs_alert_delivery_event_subscription (alert_event_id, subscription_id),
    KEY idx_xhs_alert_deliveries_pending (status, attempt_count, updated_at),
    CONSTRAINT fk_xhs_alert_deliveries_event FOREIGN KEY (alert_event_id) REFERENCES xhs_alert_events(id),
    CONSTRAINT fk_xhs_alert_deliveries_subscription FOREIGN KEY (subscription_id) REFERENCES xhs_alert_subscriptions(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
