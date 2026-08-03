CREATE TABLE xhs_incident_actions (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    incident_id BIGINT NOT NULL,
    from_status VARCHAR(32) NOT NULL,
    to_status VARCHAR(32) NOT NULL,
    actor_connection_id VARCHAR(64) NOT NULL,
    actor_recipient_id VARCHAR(191) NOT NULL,
    note VARCHAR(1000) NOT NULL,
    created_at DATETIME(3) NOT NULL,
    KEY idx_xhs_incident_actions_incident_created (incident_id, created_at),
    KEY idx_xhs_incident_actions_actor_created (actor_connection_id, actor_recipient_id, created_at),
    CONSTRAINT fk_xhs_incident_actions_incident
        FOREIGN KEY (incident_id) REFERENCES xhs_incidents(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
