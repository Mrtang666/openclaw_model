CREATE TABLE ride_quotes (
    quote_id VARCHAR(64) PRIMARY KEY,
    session_key VARCHAR(255) NOT NULL,
    origin_name VARCHAR(255) NOT NULL,
    origin_location VARCHAR(64) NOT NULL,
    destination_name VARCHAR(255) NOT NULL,
    destination_location VARCHAR(64) NOT NULL,
    estimate_trace_id VARCHAR(128) NOT NULL,
    raw_json MEDIUMTEXT NULL,
    expires_at DATETIME(3) NOT NULL,
    created_at DATETIME(3) NOT NULL,
    KEY idx_ride_quotes_session (session_key), KEY idx_ride_quotes_expiry (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE ride_quote_options (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    quote_id VARCHAR(64) NOT NULL,
    option_index INT NOT NULL,
    product_category VARCHAR(64) NOT NULL,
    option_name VARCHAR(128) NOT NULL,
    min_price DECIMAL(10,2) NULL,
    max_price DECIMAL(10,2) NULL,
    duration_seconds INT NULL,
    raw_json MEDIUMTEXT NULL,
    UNIQUE KEY uk_ride_quote_option (quote_id, option_index),
    CONSTRAINT fk_ride_quote_option_quote FOREIGN KEY (quote_id) REFERENCES ride_quotes(quote_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE ride_orders (
    order_id VARCHAR(128) PRIMARY KEY,
    session_key VARCHAR(255) NOT NULL,
    quote_id VARCHAR(64) NOT NULL,
    product_category VARCHAR(255) NOT NULL,
    status VARCHAR(32) NOT NULL,
    driver_name VARCHAR(128) NULL,
    driver_phone VARCHAR(64) NULL,
    vehicle_plate VARCHAR(32) NULL,
    eta_seconds INT NULL,
    final_fare DECIMAL(10,2) NULL,
    raw_json MEDIUMTEXT NULL,
    updated_at DATETIME(3) NOT NULL,
    created_at DATETIME(3) NOT NULL,
    KEY idx_ride_orders_session_status (session_key, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE ride_order_events (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    order_id VARCHAR(128) NOT NULL,
    from_status VARCHAR(32) NULL,
    to_status VARCHAR(32) NOT NULL,
    payload MEDIUMTEXT NULL,
    created_at DATETIME(3) NOT NULL,
    KEY idx_ride_events_order (order_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
