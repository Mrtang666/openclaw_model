CREATE TABLE food_delivery_addresses (
    address_id VARCHAR(64) PRIMARY KEY,
    user_key VARCHAR(191) NOT NULL,
    label VARCHAR(64) NOT NULL,
    recipient_name_encrypted TEXT NOT NULL,
    recipient_phone_encrypted TEXT NOT NULL,
    city VARCHAR(64) NOT NULL,
    district VARCHAR(128) NOT NULL,
    detail_encrypted TEXT NOT NULL,
    longitude VARCHAR(32) NULL,
    latitude VARCHAR(32) NULL,
    is_default BOOLEAN NOT NULL DEFAULT FALSE,
    last_used_at DATETIME(3) NOT NULL,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    KEY idx_food_addresses_user_used (user_key, last_used_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE food_order_drafts (
    user_key VARCHAR(191) PRIMARY KEY,
    address_id VARCHAR(64) NOT NULL,
    merchant_id VARCHAR(128) NOT NULL,
    merchant_name VARCHAR(255) NOT NULL,
    items_json JSON NOT NULL,
    remark VARCHAR(500) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    KEY idx_food_drafts_updated (updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE food_order_previews (
    preview_id VARCHAR(64) PRIMARY KEY,
    user_key VARCHAR(191) NOT NULL,
    address_id VARCHAR(64) NOT NULL,
    merchant_id VARCHAR(128) NOT NULL,
    merchant_name VARCHAR(255) NOT NULL,
    items_json JSON NOT NULL,
    subtotal DECIMAL(10,2) NULL,
    packing_fee DECIMAL(10,2) NULL,
    delivery_fee DECIMAL(10,2) NULL,
    discount_amount DECIMAL(10,2) NULL,
    total_amount DECIMAL(10,2) NOT NULL,
    eta_minutes INT NULL,
    confirmation_token_hash VARCHAR(64) NOT NULL,
    raw_json MEDIUMTEXT NULL,
    expires_at DATETIME(3) NOT NULL,
    created_at DATETIME(3) NOT NULL,
    KEY idx_food_previews_user_expiry (user_key, expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE food_orders (
    order_id VARCHAR(64) PRIMARY KEY,
    provider_order_id VARCHAR(128) NOT NULL,
    user_key VARCHAR(191) NOT NULL,
    preview_id VARCHAR(64) NOT NULL,
    merchant_name VARCHAR(255) NOT NULL,
    status VARCHAR(32) NOT NULL,
    total_amount DECIMAL(10,2) NOT NULL,
    eta_minutes INT NULL,
    progress_text VARCHAR(500) NOT NULL,
    raw_json MEDIUMTEXT NULL,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    KEY idx_food_orders_user_status (user_key, status, updated_at),
    UNIQUE KEY uk_food_orders_provider (provider_order_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE food_order_events (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    order_id VARCHAR(64) NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    payload MEDIUMTEXT NULL,
    created_at DATETIME(3) NOT NULL,
    KEY idx_food_order_events_order (order_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE food_payment_handoffs (
    handoff_id VARCHAR(64) PRIMARY KEY,
    order_id VARCHAR(64) NOT NULL,
    handoff_type VARCHAR(32) NOT NULL,
    target_url TEXT NOT NULL,
    fallback_url TEXT NULL,
    status VARCHAR(32) NOT NULL,
    expires_at DATETIME(3) NOT NULL,
    created_at DATETIME(3) NOT NULL,
    KEY idx_food_payment_order (order_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
