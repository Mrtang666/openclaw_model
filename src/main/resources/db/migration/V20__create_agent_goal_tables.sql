CREATE TABLE agent_goals (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    channel VARCHAR(32) NOT NULL,
    session_key VARCHAR(191) NOT NULL,
    goal_type VARCHAR(64) NOT NULL,
    objective TEXT NOT NULL,
    status VARCHAR(32) NOT NULL,
    result_summary TEXT NULL,
    started_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    finished_at DATETIME(3) NULL,
    KEY idx_agent_goals_session_status (channel, session_key, status, updated_at),
    KEY idx_agent_goals_status_updated (status, updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
