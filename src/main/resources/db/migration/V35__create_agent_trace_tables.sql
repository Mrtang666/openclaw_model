CREATE TABLE agent_runs (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    run_key VARCHAR(191) NOT NULL,
    channel VARCHAR(32) NOT NULL,
    session_key VARCHAR(191) NOT NULL,
    user_text TEXT NOT NULL,
    context_summary TEXT NULL,
    status VARCHAR(32) NOT NULL,
    stop_reason VARCHAR(64) NULL,
    final_reply_summary TEXT NULL,
    started_at DATETIME(3) NOT NULL,
    completed_at DATETIME(3) NULL,
    UNIQUE KEY uk_agent_runs_run_key (run_key),
    KEY idx_agent_runs_session_started (session_key, started_at),
    KEY idx_agent_runs_status_started (status, started_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE agent_run_steps (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    run_id BIGINT NOT NULL,
    step_index INT NOT NULL,
    step_type VARCHAR(32) NOT NULL,
    round_number INT NULL,
    tool_name VARCHAR(128) NULL,
    status VARCHAR(32) NOT NULL,
    input_summary TEXT NULL,
    output_summary TEXT NULL,
    metadata_json JSON NULL,
    created_at DATETIME(3) NOT NULL,
    UNIQUE KEY uk_agent_run_steps_order (run_id, step_index),
    KEY idx_agent_run_steps_run_type (run_id, step_type),
    KEY idx_agent_run_steps_tool (tool_name, status),
    CONSTRAINT fk_agent_run_steps_run
        FOREIGN KEY (run_id) REFERENCES agent_runs(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
