CREATE TABLE agent_goal_review_actions (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    goal_id BIGINT NOT NULL,
    action_type VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    reason TEXT NOT NULL,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    KEY idx_agent_goal_review_actions_goal_created (goal_id, created_at, id),
    KEY idx_agent_goal_review_actions_status_created (status, created_at),
    KEY idx_agent_goal_review_actions_action_status (action_type, status, created_at),
    CONSTRAINT fk_agent_goal_review_actions_goal
        FOREIGN KEY (goal_id) REFERENCES agent_goals(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
