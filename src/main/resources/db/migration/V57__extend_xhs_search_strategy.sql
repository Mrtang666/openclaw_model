ALTER TABLE xhs_search_executions
    ADD COLUMN time_range VARCHAR(16) NOT NULL DEFAULT 'ANY' AFTER sort_mode,
    ADD COLUMN note_type VARCHAR(16) NOT NULL DEFAULT 'ALL' AFTER time_range,
    ADD KEY idx_xhs_search_strategy(
        project_id, sort_mode, time_range, note_type, started_at);
