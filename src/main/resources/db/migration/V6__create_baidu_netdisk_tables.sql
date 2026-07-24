CREATE TABLE user_netdisk_authorizations (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id VARCHAR(128) NOT NULL,
    provider VARCHAR(32) NOT NULL,
    access_token_encrypted TEXT NOT NULL,
    refresh_token_encrypted TEXT NULL,
    expires_at DATETIME(3) NULL,
    scope TEXT NULL,
    status VARCHAR(32) NOT NULL,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    UNIQUE KEY uk_user_provider (user_id, provider),
    KEY idx_netdisk_auth_user (user_id),
    KEY idx_netdisk_auth_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE netdisk_auth_states (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    state VARCHAR(128) NOT NULL,
    user_id VARCHAR(128) NOT NULL,
    provider VARCHAR(32) NOT NULL,
    operation VARCHAR(32) NOT NULL,
    redirect_after_auth TEXT NULL,
    pending_action_id BIGINT NULL,
    expires_at DATETIME(3) NOT NULL,
    used TINYINT(1) NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL,
    UNIQUE KEY uk_netdisk_auth_state (state),
    KEY idx_netdisk_auth_state_user (user_id),
    KEY idx_netdisk_auth_state_expires_at (expires_at),
    KEY idx_netdisk_auth_state_pending_action (pending_action_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE netdisk_pending_actions (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id VARCHAR(128) NOT NULL,
    provider VARCHAR(32) NOT NULL,
    action_type VARCHAR(64) NOT NULL,
    payload_json MEDIUMTEXT NOT NULL,
    status VARCHAR(32) NOT NULL,
    error_message TEXT NULL,
    expires_at DATETIME(3) NOT NULL,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    KEY idx_netdisk_pending_user (user_id),
    KEY idx_netdisk_pending_provider (provider),
    KEY idx_netdisk_pending_status (status),
    KEY idx_netdisk_pending_expires_at (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE netdisk_operation_logs (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id VARCHAR(128) NOT NULL,
    provider VARCHAR(32) NOT NULL,
    operation VARCHAR(64) NOT NULL,
    request_summary TEXT NULL,
    result_summary TEXT NULL,
    status VARCHAR(32) NOT NULL,
    error_message TEXT NULL,
    created_at DATETIME(3) NOT NULL,
    KEY idx_netdisk_log_user (user_id),
    KEY idx_netdisk_log_provider (provider),
    KEY idx_netdisk_log_operation (operation),
    KEY idx_netdisk_log_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
