CREATE TABLE IF NOT EXISTS wechat_documents (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    wechat_user_id VARCHAR(191) NOT NULL,
    source_reference VARCHAR(255) NULL,
    file_name VARCHAR(255) NOT NULL,
    mime_type VARCHAR(191) NULL,
    document_format VARCHAR(32) NOT NULL,
    sha256 VARCHAR(128) NULL,
    md5 VARCHAR(128) NULL,
    size_bytes BIGINT NOT NULL DEFAULT 0,
    local_path VARCHAR(1024) NULL,
    summary MEDIUMTEXT NULL,
    created_at DATETIME(3) NOT NULL,
    KEY idx_wechat_documents_user_created (wechat_user_id, created_at),
    KEY idx_wechat_documents_sha256 (sha256)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS wechat_document_chunks (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    document_id BIGINT NOT NULL,
    chunk_index INT NOT NULL,
    title VARCHAR(255) NULL,
    chunk_text MEDIUMTEXT NOT NULL,
    summary TEXT NULL,
    created_at DATETIME(3) NOT NULL,
    KEY idx_document_chunks_document_index (document_id, chunk_index),
    CONSTRAINT fk_document_chunks_document
        FOREIGN KEY (document_id) REFERENCES wechat_documents(id)
        ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS wechat_generated_documents (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    wechat_user_id VARCHAR(191) NOT NULL,
    source_document_id BIGINT NULL,
    file_name VARCHAR(255) NOT NULL,
    document_format VARCHAR(32) NOT NULL,
    local_path VARCHAR(1024) NULL,
    requirement_summary TEXT NULL,
    created_at DATETIME(3) NOT NULL,
    KEY idx_generated_documents_user_created (wechat_user_id, created_at),
    CONSTRAINT fk_generated_documents_source
        FOREIGN KEY (source_document_id) REFERENCES wechat_documents(id)
        ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
