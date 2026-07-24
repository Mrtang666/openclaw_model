CREATE TABLE payment_orders (
    payment_id VARCHAR(64) PRIMARY KEY,
    ride_order_id VARCHAR(128) NOT NULL,
    amount DECIMAL(10,2) NOT NULL,
    channel VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    transaction_id VARCHAR(128) NULL,
    raw_json MEDIUMTEXT NULL,
    created_at DATETIME(3) NOT NULL,
    paid_at DATETIME(3) NULL,
    KEY idx_payment_ride_order (ride_order_id), KEY idx_payment_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE payment_events (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    payment_id VARCHAR(64) NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    payload MEDIUMTEXT NOT NULL,
    created_at DATETIME(3) NOT NULL,
    KEY idx_payment_events_payment (payment_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
