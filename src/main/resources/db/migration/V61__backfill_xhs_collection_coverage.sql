INSERT IGNORE INTO xhs_search_executions(
    job_key, project_id, keyword_value, keyword_type, query_mode, sort_mode,
    time_range, note_type, requested_limit, requested_comment_limit,
    status, completeness_status, raw_count, imported_count, comment_count,
    skipped_count, error_code, error_message, started_at, finished_at, created_at, updated_at)
SELECT j.job_key, j.project_id,
       CASE WHEN j.query_text = '' THEN '历史采集' ELSE j.query_text END,
       'LEGACY', 'STANDARD', 'GENERAL', 'ANY', 'ALL',
       GREATEST(j.record_count, 20), 100,
       j.status,
       CASE
           WHEN j.status = 'SUCCEEDED' THEN 'COMPLETE'
           WHEN j.status = 'PARTIAL' THEN 'PARTIAL'
           WHEN j.status = 'FAILED' THEN 'FAILED'
           ELSE 'NOT_STARTED'
       END,
       j.record_count, j.record_count, 0, 0, j.error_code, j.error_message,
       j.started_at, j.finished_at, j.started_at, COALESCE(j.finished_at, j.started_at)
FROM xhs_collection_jobs j
WHERE NOT EXISTS (
    SELECT 1 FROM xhs_search_executions se WHERE se.job_key = j.job_key
);

INSERT IGNORE INTO xhs_post_collection_completeness(
    post_id, detail_status, comments_status, images_status,
    expected_comment_count, collected_comment_count, discovered_image_count,
    last_collected_at, error_message, updated_at)
SELECT p.id, 'COLLECTED',
       CASE
           WHEN COALESCE(m.comment_count, 0) = 0
                OR COALESCE(c.collected_count, 0) >= m.comment_count THEN 'FULL'
           WHEN COALESCE(c.collected_count, 0) > 0 THEN 'PARTIAL'
           ELSE 'NOT_REQUESTED'
       END,
       CASE WHEN COALESCE(i.image_count, 0) > 0 THEN 'DISCOVERED' ELSE 'NOT_REQUESTED' END,
       COALESCE(m.comment_count, 0), COALESCE(c.collected_count, 0), COALESCE(i.image_count, 0),
       p.last_collected_at, NULL, p.last_collected_at
FROM xhs_posts p
LEFT JOIN (
    SELECT snapshot.post_id, snapshot.comment_count
    FROM xhs_metric_snapshots snapshot
    JOIN (
        SELECT post_id, MAX(snapshot_at) snapshot_at
        FROM xhs_metric_snapshots GROUP BY post_id
    ) latest ON latest.post_id = snapshot.post_id AND latest.snapshot_at = snapshot.snapshot_at
) m ON m.post_id = p.id
LEFT JOIN (
    SELECT post_id, COUNT(*) collected_count FROM xhs_comments GROUP BY post_id
) c ON c.post_id = p.id
LEFT JOIN (
    SELECT post_id, COUNT(*) image_count FROM xhs_post_images GROUP BY post_id
) i ON i.post_id = p.id
WHERE NOT EXISTS (
    SELECT 1 FROM xhs_post_collection_completeness pc WHERE pc.post_id = p.id
);
