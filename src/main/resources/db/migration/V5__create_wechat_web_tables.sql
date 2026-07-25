CREATE TABLE wechat_web_page_cache (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    url_hash VARCHAR(64) NOT NULL,
    url TEXT NOT NULL,
    title VARCHAR(255) NULL,
    content MEDIUMTEXT NULL,
    fetched_at DATETIME(3) NOT NULL,
    expire_at DATETIME(3) NULL,
    UNIQUE KEY uk_web_url_hash (url_hash),
    KEY idx_web_cache_expire_at (expire_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
