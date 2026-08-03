CREATE TABLE memory_graph_nodes (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    session_key VARCHAR(191) NOT NULL,
    conversation_id BIGINT NULL,
    node_type VARCHAR(32) NOT NULL,
    topic_key VARCHAR(191) NULL,
    title VARCHAR(255) NOT NULL,
    content MEDIUMTEXT NOT NULL,
    summary TEXT NULL,
    importance_score DOUBLE NOT NULL DEFAULT 0,
    relevance_score DOUBLE NOT NULL DEFAULT 0,
    confidence_score DOUBLE NOT NULL DEFAULT 0,
    source_message_start_id BIGINT NULL,
    source_message_end_id BIGINT NULL,
    source_type VARCHAR(64) NULL,
    source_ref TEXT NULL,
    tags VARCHAR(512) NULL,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    expires_at DATETIME(3) NULL,
    deleted TINYINT NOT NULL DEFAULT 0,
    KEY idx_memory_nodes_session_type_created (session_key, node_type, created_at),
    KEY idx_memory_nodes_session_topic (session_key, topic_key),
    KEY idx_memory_nodes_conversation_type (conversation_id, node_type),
    KEY idx_memory_nodes_deleted_expires (deleted, expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE memory_graph_edges (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    session_key VARCHAR(191) NOT NULL,
    source_node_id BIGINT NOT NULL,
    target_node_id BIGINT NOT NULL,
    edge_type VARCHAR(32) NOT NULL,
    weight DOUBLE NOT NULL DEFAULT 1,
    created_at DATETIME(3) NOT NULL,
    UNIQUE KEY uk_memory_edges_unique (source_node_id, target_node_id, edge_type),
    KEY idx_memory_edges_source (source_node_id, edge_type),
    KEY idx_memory_edges_target (target_node_id, edge_type),
    KEY idx_memory_edges_session_type (session_key, edge_type),
    CONSTRAINT fk_memory_edges_source
        FOREIGN KEY (source_node_id) REFERENCES memory_graph_nodes(id),
    CONSTRAINT fk_memory_edges_target
        FOREIGN KEY (target_node_id) REFERENCES memory_graph_nodes(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
