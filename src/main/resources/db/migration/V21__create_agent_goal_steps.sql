CREATE TABLE agent_goal_steps (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    goal_id BIGINT NOT NULL,
    tool_name VARCHAR(64) NOT NULL,
    arguments_json JSON NOT NULL,
    result_summary TEXT NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_at DATETIME(3) NOT NULL,
    KEY idx_agent_goal_steps_goal_created (goal_id, created_at, id),
    KEY idx_agent_goal_steps_tool_status (tool_name, status, created_at),
    CONSTRAINT fk_agent_goal_steps_goal
        FOREIGN KEY (goal_id) REFERENCES agent_goals(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
