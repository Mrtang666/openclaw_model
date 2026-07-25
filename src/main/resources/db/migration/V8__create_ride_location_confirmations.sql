CREATE TABLE ride_location_confirmations (
    confirmation_id VARCHAR(64) PRIMARY KEY,
    session_key VARCHAR(255) NOT NULL,
    city VARCHAR(64) NOT NULL,
    origin_name VARCHAR(255) NOT NULL,
    origin_address VARCHAR(500) NULL,
    origin_location VARCHAR(64) NOT NULL,
    destination_name VARCHAR(255) NOT NULL,
    destination_address VARCHAR(500) NULL,
    destination_location VARCHAR(64) NOT NULL,
    expires_at DATETIME(3) NOT NULL,
    created_at DATETIME(3) NOT NULL,
    KEY idx_location_confirmation_session (session_key, expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
