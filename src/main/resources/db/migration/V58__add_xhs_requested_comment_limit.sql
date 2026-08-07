ALTER TABLE xhs_search_executions
    ADD COLUMN requested_comment_limit INT NOT NULL DEFAULT 100 AFTER requested_limit;
