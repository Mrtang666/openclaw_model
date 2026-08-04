CREATE TABLE xhs_semantic_cache (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    content_hash CHAR(64) NOT NULL,
    analysis_version VARCHAR(64) NOT NULL,
    payload_json JSON NOT NULL,
    model_name VARCHAR(128) NOT NULL,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    UNIQUE KEY uk_xhs_semantic_cache_hash_version (content_hash, analysis_version),
    KEY idx_xhs_semantic_cache_updated (updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
