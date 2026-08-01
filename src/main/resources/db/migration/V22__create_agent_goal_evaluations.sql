CREATE TABLE agent_goal_evaluations (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    goal_id BIGINT NOT NULL,
    evaluator_name VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL,
    reasoning TEXT NOT NULL,
    created_at DATETIME(3) NOT NULL,
    KEY idx_agent_goal_evaluations_goal_created (goal_id, created_at, id),
    KEY idx_agent_goal_evaluations_status_created (status, created_at),
    CONSTRAINT fk_agent_goal_evaluations_goal
        FOREIGN KEY (goal_id) REFERENCES agent_goals(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
