ALTER TABLE xhs_posts
    ADD COLUMN access_url TEXT NULL AFTER source_url,
    ADD COLUMN access_url_refreshed_at DATETIME(3) NULL AFTER access_url;
