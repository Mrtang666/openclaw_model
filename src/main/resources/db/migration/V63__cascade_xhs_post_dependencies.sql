ALTER TABLE xhs_comments DROP FOREIGN KEY fk_xhs_comments_post;
ALTER TABLE xhs_comments ADD CONSTRAINT fk_xhs_comments_post FOREIGN KEY (post_id)
    REFERENCES xhs_posts(id) ON DELETE CASCADE;

ALTER TABLE xhs_metric_snapshots DROP FOREIGN KEY fk_xhs_metrics_post;
ALTER TABLE xhs_metric_snapshots ADD CONSTRAINT fk_xhs_metrics_post FOREIGN KEY (post_id)
    REFERENCES xhs_posts(id) ON DELETE CASCADE;

ALTER TABLE xhs_analysis_results DROP FOREIGN KEY fk_xhs_analysis_post;
ALTER TABLE xhs_analysis_results ADD CONSTRAINT fk_xhs_analysis_post FOREIGN KEY (post_id)
    REFERENCES xhs_posts(id) ON DELETE CASCADE;

ALTER TABLE xhs_analysis_executions DROP FOREIGN KEY fk_xhs_analysis_execution_post;
ALTER TABLE xhs_analysis_executions ADD CONSTRAINT fk_xhs_analysis_execution_post FOREIGN KEY (post_id)
    REFERENCES xhs_posts(id) ON DELETE CASCADE;

ALTER TABLE xhs_analysis_feedback DROP FOREIGN KEY fk_xhs_analysis_feedback_post;
ALTER TABLE xhs_analysis_feedback ADD CONSTRAINT fk_xhs_analysis_feedback_post FOREIGN KEY (post_id)
    REFERENCES xhs_posts(id) ON DELETE CASCADE;

ALTER TABLE xhs_incident_posts DROP FOREIGN KEY fk_xhs_incident_posts_post;
ALTER TABLE xhs_incident_posts ADD CONSTRAINT fk_xhs_incident_posts_post FOREIGN KEY (post_id)
    REFERENCES xhs_posts(id) ON DELETE CASCADE;

ALTER TABLE xhs_negative_post_deliveries DROP FOREIGN KEY fk_xhs_negative_delivery_post;
ALTER TABLE xhs_negative_post_deliveries ADD CONSTRAINT fk_xhs_negative_delivery_post FOREIGN KEY (post_id)
    REFERENCES xhs_posts(id) ON DELETE CASCADE;
