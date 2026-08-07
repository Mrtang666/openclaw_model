CREATE TABLE xhs_post_images (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    post_id BIGINT NOT NULL,
    image_hash CHAR(64) NOT NULL,
    image_url TEXT NOT NULL,
    image_order INT NOT NULL DEFAULT 0,
    analysis_status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    first_collected_at DATETIME(3) NOT NULL,
    last_collected_at DATETIME(3) NOT NULL,
    UNIQUE KEY uk_xhs_post_image_hash (post_id, image_hash),
    KEY idx_xhs_post_images_analysis (analysis_status, last_collected_at),
    CONSTRAINT fk_xhs_post_images_post FOREIGN KEY (post_id)
        REFERENCES xhs_posts(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
