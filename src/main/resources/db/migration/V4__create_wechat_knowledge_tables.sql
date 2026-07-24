CREATE TABLE wechat_knowledge_document (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    session_key VARCHAR(191) NOT NULL,
    title VARCHAR(255) NOT NULL,
    source_type VARCHAR(32) NOT NULL,
    source_url TEXT NULL,
    tags VARCHAR(512) NULL,
    content_hash VARCHAR(64) NOT NULL,
    chunk_count INT NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    deleted TINYINT NOT NULL DEFAULT 0,
    KEY idx_knowledge_session (session_key),
    KEY idx_knowledge_hash (content_hash),
    KEY idx_knowledge_deleted (deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE wechat_knowledge_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    session_key VARCHAR(191) NOT NULL,
    operation VARCHAR(64) NOT NULL,
    document_id BIGINT NULL,
    query_text TEXT NULL,
    result_summary TEXT NULL,
    created_at DATETIME(3) NOT NULL,
    KEY idx_knowledge_log_session (session_key),
    KEY idx_knowledge_log_document (document_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
