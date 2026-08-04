CREATE TABLE agent_trace_access_audit (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    actor VARCHAR(191) NOT NULL,
    action VARCHAR(64) NOT NULL,
    target_type VARCHAR(32) NOT NULL,
    target_key VARCHAR(191) NOT NULL,
    allowed BOOLEAN NOT NULL,
    reason VARCHAR(64) NOT NULL,
    remote_address VARCHAR(128) NULL,
    user_agent VARCHAR(512) NULL,
    created_at DATETIME(3) NOT NULL,
    KEY idx_agent_trace_access_target (target_type, target_key, created_at),
    KEY idx_agent_trace_access_actor_created (actor, created_at),
    KEY idx_agent_trace_access_allowed_created (allowed, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
