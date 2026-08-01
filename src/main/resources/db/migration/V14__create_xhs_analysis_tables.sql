CREATE TABLE xhs_analysis_results (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    post_id BIGINT NOT NULL,
    analysis_version VARCHAR(64) NOT NULL,
    sentiment VARCHAR(16) NOT NULL,
    sentiment_score DECIMAL(6,5) NOT NULL,
    aspects_json JSON NOT NULL,
    risk_category VARCHAR(64) NOT NULL,
    severity INT NOT NULL,
    risk_score INT NOT NULL,
    confidence DECIMAL(6,5) NOT NULL,
    summary TEXT NOT NULL,
    evidence_json JSON NOT NULL,
    explanation_json JSON NOT NULL,
    review_status VARCHAR(32) NOT NULL,
    analyzed_at DATETIME(3) NOT NULL,
    UNIQUE KEY uk_xhs_analysis_post_version (post_id, analysis_version),
    KEY idx_xhs_analysis_risk (risk_score, analyzed_at),
    KEY idx_xhs_analysis_category (risk_category, analyzed_at),
    CONSTRAINT fk_xhs_analysis_post FOREIGN KEY (post_id) REFERENCES xhs_posts(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE xhs_incidents (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    project_id BIGINT NOT NULL,
    incident_key VARCHAR(64) NOT NULL,
    risk_category VARCHAR(64) NOT NULL,
    title VARCHAR(500) NOT NULL,
    status VARCHAR(32) NOT NULL,
    risk_level VARCHAR(16) NOT NULL,
    risk_score INT NOT NULL,
    post_count INT NOT NULL,
    first_seen_at DATETIME(3) NOT NULL,
    last_seen_at DATETIME(3) NOT NULL,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    UNIQUE KEY uk_xhs_incidents_project_key (project_id, incident_key),
    KEY idx_xhs_incidents_project_status (project_id, status, risk_score),
    CONSTRAINT fk_xhs_incidents_project FOREIGN KEY (project_id) REFERENCES xhs_monitor_projects(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE xhs_incident_posts (
    incident_id BIGINT NOT NULL,
    post_id BIGINT NOT NULL,
    linked_at DATETIME(3) NOT NULL,
    PRIMARY KEY (incident_id, post_id),
    CONSTRAINT fk_xhs_incident_posts_incident FOREIGN KEY (incident_id) REFERENCES xhs_incidents(id),
    CONSTRAINT fk_xhs_incident_posts_post FOREIGN KEY (post_id) REFERENCES xhs_posts(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE xhs_alert_rules (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    project_id BIGINT NOT NULL,
    name VARCHAR(255) NOT NULL,
    minimum_risk_score INT NOT NULL,
    cooldown_minutes INT NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    KEY idx_xhs_alert_rules_project_enabled (project_id, enabled),
    CONSTRAINT fk_xhs_alert_rules_project FOREIGN KEY (project_id) REFERENCES xhs_monitor_projects(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE xhs_alert_events (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    alert_key VARCHAR(191) NOT NULL,
    rule_id BIGINT NOT NULL,
    incident_id BIGINT NOT NULL,
    status VARCHAR(32) NOT NULL,
    risk_score INT NOT NULL,
    sent_at DATETIME(3) NULL,
    acknowledged_at DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL,
    UNIQUE KEY uk_xhs_alert_events_key (alert_key),
    KEY idx_xhs_alert_events_status_created (status, created_at),
    CONSTRAINT fk_xhs_alert_events_rule FOREIGN KEY (rule_id) REFERENCES xhs_alert_rules(id),
    CONSTRAINT fk_xhs_alert_events_incident FOREIGN KEY (incident_id) REFERENCES xhs_incidents(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
