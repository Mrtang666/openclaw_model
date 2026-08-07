CREATE TABLE xhs_comment_analysis_results (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    post_id BIGINT NOT NULL,
    source_comment_id VARCHAR(191) NOT NULL,
    sentiment VARCHAR(16) NOT NULL,
    risk_score INT NOT NULL DEFAULT 0,
    is_negative BOOLEAN NOT NULL DEFAULT FALSE,
    analysis_method VARCHAR(32) NOT NULL DEFAULT 'RULE',
    analyzed_at DATETIME(3) NOT NULL,
    UNIQUE KEY uk_xhs_comment_analysis_post_comment (post_id, source_comment_id),
    KEY idx_xhs_comment_analysis_negative (post_id, is_negative, risk_score),
    CONSTRAINT fk_xhs_comment_analysis_post FOREIGN KEY (post_id)
        REFERENCES xhs_posts(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO xhs_comment_analysis_results(
    post_id, source_comment_id, sentiment, risk_score, is_negative,
    analysis_method, analyzed_at)
SELECT c.post_id, c.source_comment_id,
       CASE WHEN c.content LIKE '%不好%' OR c.content LIKE '%失望%'
                  OR c.content LIKE '%问题%' OR c.content LIKE '%无效%'
                  OR c.content LIKE '%过敏%' OR c.content LIKE '%红肿%'
                  OR c.content LIKE '%疼%' OR c.content LIKE '%欺骗%'
                  OR c.content LIKE '%退款%' OR c.content LIKE '%维权%'
                  OR c.content LIKE '%假货%' OR c.content LIKE '%踩雷%'
                  OR c.content LIKE '%避雷%' OR c.content LIKE '%不推荐%'
            THEN 'NEGATIVE' ELSE 'NEUTRAL' END,
       CASE WHEN c.content LIKE '%不好%' OR c.content LIKE '%失望%'
                  OR c.content LIKE '%问题%' OR c.content LIKE '%无效%'
                  OR c.content LIKE '%过敏%' OR c.content LIKE '%红肿%'
                  OR c.content LIKE '%疼%' OR c.content LIKE '%欺骗%'
                  OR c.content LIKE '%退款%' OR c.content LIKE '%维权%'
                  OR c.content LIKE '%假货%' OR c.content LIKE '%踩雷%'
                  OR c.content LIKE '%避雷%' OR c.content LIKE '%不推荐%'
            THEN 25 ELSE 0 END,
       CASE WHEN c.content LIKE '%不好%' OR c.content LIKE '%失望%'
                  OR c.content LIKE '%问题%' OR c.content LIKE '%无效%'
                  OR c.content LIKE '%过敏%' OR c.content LIKE '%红肿%'
                  OR c.content LIKE '%疼%' OR c.content LIKE '%欺骗%'
                  OR c.content LIKE '%退款%' OR c.content LIKE '%维权%'
                  OR c.content LIKE '%假货%' OR c.content LIKE '%踩雷%'
                  OR c.content LIKE '%避雷%' OR c.content LIKE '%不推荐%'
            THEN TRUE ELSE FALSE END,
       'RULE', c.last_collected_at
FROM xhs_comments c;
