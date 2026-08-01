CREATE TABLE medical_login_sessions (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    connection_id VARCHAR(191) NOT NULL,
    login_session_id VARCHAR(191) NOT NULL,
    requested_role VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    bound_user_id BIGINT NULL,
    created_at DATETIME(3) NOT NULL,
    bound_at DATETIME(3) NULL,
    UNIQUE KEY uk_medical_login_session_id (login_session_id),
    KEY idx_medical_login_connection (connection_id, status),
    KEY idx_medical_login_bound_user (bound_user_id),
    CONSTRAINT fk_medical_login_bound_user
        FOREIGN KEY (bound_user_id) REFERENCES medical_users(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
