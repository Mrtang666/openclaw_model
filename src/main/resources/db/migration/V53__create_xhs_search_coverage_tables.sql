CREATE TABLE xhs_search_executions (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    job_key VARCHAR(64) NOT NULL,
    project_id BIGINT NOT NULL,
    keyword_value VARCHAR(500) NOT NULL,
    keyword_type VARCHAR(32) NOT NULL DEFAULT 'MANUAL',
    query_mode VARCHAR(32) NOT NULL DEFAULT 'STANDARD',
    sort_mode VARCHAR(32) NOT NULL DEFAULT 'GENERAL',
    cursor_start VARCHAR(128) NULL,
    cursor_end VARCHAR(128) NULL,
    requested_limit INT NOT NULL DEFAULT 20,
    status VARCHAR(32) NOT NULL,
    completeness_status VARCHAR(16) NOT NULL DEFAULT 'NOT_STARTED',
    raw_count INT NOT NULL DEFAULT 0,
    imported_count INT NOT NULL DEFAULT 0,
    comment_count INT NOT NULL DEFAULT 0,
    skipped_count INT NOT NULL DEFAULT 0,
    error_code VARCHAR(64) NULL,
    error_message VARCHAR(2000) NULL,
    started_at DATETIME(3) NOT NULL,
    finished_at DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    UNIQUE KEY uk_xhs_search_execution_job (job_key),
    KEY idx_xhs_search_execution_project_started (project_id, started_at),
    KEY idx_xhs_search_execution_status (status, completeness_status, updated_at),
    CONSTRAINT fk_xhs_search_execution_job FOREIGN KEY (job_key)
        REFERENCES xhs_collection_jobs(job_key) ON DELETE CASCADE,
    CONSTRAINT fk_xhs_search_execution_project FOREIGN KEY (project_id)
        REFERENCES xhs_monitor_projects(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE xhs_post_search_hits (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    post_id BIGINT NOT NULL,
    search_execution_id BIGINT NOT NULL,
    keyword_value VARCHAR(500) NOT NULL,
    first_hit_at DATETIME(3) NOT NULL,
    last_hit_at DATETIME(3) NOT NULL,
    UNIQUE KEY uk_xhs_post_search_hit (post_id, search_execution_id),
    KEY idx_xhs_search_hit_execution (search_execution_id, post_id),
    CONSTRAINT fk_xhs_search_hit_post FOREIGN KEY (post_id)
        REFERENCES xhs_posts(id) ON DELETE CASCADE,
    CONSTRAINT fk_xhs_search_hit_execution FOREIGN KEY (search_execution_id)
        REFERENCES xhs_search_executions(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE xhs_post_collection_completeness (
    post_id BIGINT PRIMARY KEY,
    detail_status VARCHAR(16) NOT NULL DEFAULT 'NOT_REQUESTED',
    comments_status VARCHAR(16) NOT NULL DEFAULT 'NOT_REQUESTED',
    images_status VARCHAR(16) NOT NULL DEFAULT 'NOT_REQUESTED',
    expected_comment_count BIGINT NOT NULL DEFAULT 0,
    collected_comment_count INT NOT NULL DEFAULT 0,
    discovered_image_count INT NOT NULL DEFAULT 0,
    last_collected_at DATETIME(3) NOT NULL,
    error_message VARCHAR(2000) NULL,
    updated_at DATETIME(3) NOT NULL,
    KEY idx_xhs_post_completeness_status (comments_status, images_status, updated_at),
    CONSTRAINT fk_xhs_post_completeness_post FOREIGN KEY (post_id)
        REFERENCES xhs_posts(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
