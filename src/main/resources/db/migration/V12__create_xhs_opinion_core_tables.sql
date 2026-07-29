CREATE TABLE xhs_monitor_projects (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    project_key VARCHAR(128) NOT NULL,
    name VARCHAR(255) NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    UNIQUE KEY uk_xhs_projects_key (project_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE xhs_monitor_terms (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    project_id BIGINT NOT NULL,
    term_type VARCHAR(32) NOT NULL,
    term_value VARCHAR(255) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME(3) NOT NULL,
    UNIQUE KEY uk_xhs_terms_project_type_value (project_id, term_type, term_value),
    KEY idx_xhs_terms_project_enabled (project_id, enabled),
    CONSTRAINT fk_xhs_terms_project FOREIGN KEY (project_id) REFERENCES xhs_monitor_projects(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE xhs_collection_jobs (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    job_key VARCHAR(64) NOT NULL,
    project_id BIGINT NOT NULL,
    source_type VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    complete BOOLEAN NOT NULL DEFAULT FALSE,
    record_count INT NOT NULL DEFAULT 0,
    next_cursor TEXT NULL,
    error_code VARCHAR(64) NULL,
    error_message TEXT NULL,
    started_at DATETIME(3) NOT NULL,
    finished_at DATETIME(3) NULL,
    UNIQUE KEY uk_xhs_collection_jobs_key (job_key),
    KEY idx_xhs_collection_jobs_project_started (project_id, started_at),
    CONSTRAINT fk_xhs_collection_jobs_project FOREIGN KEY (project_id) REFERENCES xhs_monitor_projects(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE xhs_source_checkpoints (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    project_id BIGINT NOT NULL,
    source_type VARCHAR(32) NOT NULL,
    checkpoint_key VARCHAR(128) NOT NULL,
    cursor_value TEXT NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    UNIQUE KEY uk_xhs_checkpoints_project_source_key (project_id, source_type, checkpoint_key),
    CONSTRAINT fk_xhs_checkpoints_project FOREIGN KEY (project_id) REFERENCES xhs_monitor_projects(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE xhs_posts (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    project_id BIGINT NOT NULL,
    source_type VARCHAR(32) NOT NULL,
    source_post_id VARCHAR(191) NOT NULL,
    source_url TEXT NOT NULL,
    author_key VARCHAR(64) NOT NULL,
    title VARCHAR(500) NOT NULL,
    content MEDIUMTEXT NOT NULL,
    note_type VARCHAR(32) NOT NULL,
    tags_json JSON NOT NULL,
    published_at DATETIME(3) NULL,
    first_collected_at DATETIME(3) NOT NULL,
    last_collected_at DATETIME(3) NOT NULL,
    raw_json MEDIUMTEXT NULL,
    UNIQUE KEY uk_xhs_posts_project_source_id (project_id, source_type, source_post_id),
    KEY idx_xhs_posts_project_published (project_id, published_at),
    KEY idx_xhs_posts_project_collected (project_id, last_collected_at),
    CONSTRAINT fk_xhs_posts_project FOREIGN KEY (project_id) REFERENCES xhs_monitor_projects(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE xhs_comments (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    post_id BIGINT NOT NULL,
    source_comment_id VARCHAR(191) NOT NULL,
    parent_comment_id VARCHAR(191) NULL,
    author_key VARCHAR(64) NOT NULL,
    content MEDIUMTEXT NOT NULL,
    liked_count BIGINT NOT NULL DEFAULT 0,
    published_at DATETIME(3) NULL,
    first_collected_at DATETIME(3) NOT NULL,
    last_collected_at DATETIME(3) NOT NULL,
    raw_json MEDIUMTEXT NULL,
    UNIQUE KEY uk_xhs_comments_post_source_id (post_id, source_comment_id),
    KEY idx_xhs_comments_post_published (post_id, published_at),
    CONSTRAINT fk_xhs_comments_post FOREIGN KEY (post_id) REFERENCES xhs_posts(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE xhs_metric_snapshots (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    post_id BIGINT NOT NULL,
    snapshot_at DATETIME(3) NOT NULL,
    liked_count BIGINT NOT NULL DEFAULT 0,
    collected_count BIGINT NOT NULL DEFAULT 0,
    comment_count BIGINT NOT NULL DEFAULT 0,
    share_count BIGINT NOT NULL DEFAULT 0,
    UNIQUE KEY uk_xhs_metrics_post_snapshot (post_id, snapshot_at),
    KEY idx_xhs_metrics_snapshot (snapshot_at),
    CONSTRAINT fk_xhs_metrics_post FOREIGN KEY (post_id) REFERENCES xhs_posts(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
