CREATE TABLE users (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    wechat_user_id VARCHAR(191) NOT NULL,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    UNIQUE KEY uk_users_wechat_user_id (wechat_user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE conversations (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    channel VARCHAR(32) NOT NULL,
    status VARCHAR(16) NOT NULL,
    started_at DATETIME(3) NOT NULL,
    last_active_at DATETIME(3) NOT NULL,
    closed_at DATETIME(3) NULL,
    KEY idx_conversations_user_active (user_id, channel, status, last_active_at),
    CONSTRAINT fk_conversations_user FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE conversation_messages (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    conversation_id BIGINT NOT NULL,
    source_message_id VARCHAR(191) NULL,
    role VARCHAR(16) NOT NULL,
    content MEDIUMTEXT NOT NULL,
    content_type VARCHAR(16) NOT NULL,
    created_at DATETIME(3) NOT NULL,
    expires_at DATETIME(3) NOT NULL,
    UNIQUE KEY uk_messages_source_message_id (source_message_id),
    KEY idx_messages_conversation_created (conversation_id, created_at, id),
    KEY idx_messages_expires_at (expires_at),
    CONSTRAINT fk_messages_conversation
        FOREIGN KEY (conversation_id) REFERENCES conversations(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE conversation_states (
    conversation_id BIGINT PRIMARY KEY,
    state_json JSON NOT NULL,
    version BIGINT NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    CONSTRAINT fk_states_conversation
        FOREIGN KEY (conversation_id) REFERENCES conversations(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE user_preferences (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    preference_key VARCHAR(64) NOT NULL,
    preference_value_json JSON NOT NULL,
    source VARCHAR(32) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    UNIQUE KEY uk_preferences_user_key (user_id, preference_key),
    CONSTRAINT fk_preferences_user FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE conversation_summaries (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    conversation_id BIGINT NOT NULL,
    summary_text MEDIUMTEXT NOT NULL,
    covered_message_id BIGINT NULL,
    generated_at DATETIME(3) NOT NULL,
    KEY idx_summaries_conversation_generated (conversation_id, generated_at, id),
    CONSTRAINT fk_summaries_conversation
        FOREIGN KEY (conversation_id) REFERENCES conversations(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE tool_execution_logs (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    conversation_id BIGINT NOT NULL,
    tool_name VARCHAR(64) NOT NULL,
    arguments_json JSON NOT NULL,
    result_summary TEXT NOT NULL,
    status VARCHAR(16) NOT NULL,
    created_at DATETIME(3) NOT NULL,
    expires_at DATETIME(3) NOT NULL,
    KEY idx_tool_logs_expires_at (expires_at),
    KEY idx_tool_logs_conversation_created (conversation_id, created_at, id),
    CONSTRAINT fk_tool_logs_conversation
        FOREIGN KEY (conversation_id) REFERENCES conversations(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
