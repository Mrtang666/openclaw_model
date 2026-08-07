package com.example.spring.xhs.schedule;

import com.example.spring.wechat.email.client.EmailClient;
import com.example.spring.wechat.email.model.EmailAttachment;
import com.example.spring.wechat.email.model.EmailMessage;
import com.example.spring.xhs.analysis.XhsAnalysisCandidate;
import com.example.spring.xhs.analysis.XhsRiskAssessment;
import com.example.spring.xhs.analysis.XhsSemanticAssessment;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class XhsNegativePostEmailService {

    private final JdbcTemplate jdbcTemplate;
    private final EmailClient emailClient;

    public XhsNegativePostEmailService(JdbcTemplate jdbcTemplate, EmailClient emailClient) {
        this.jdbcTemplate = jdbcTemplate;
        this.emailClient = emailClient;
    }

    @Transactional
    public void enqueue(XhsAnalysisCandidate candidate, XhsSemanticAssessment semantic, XhsRiskAssessment risk) {
        if (candidate == null) {
            return;
        }
        enqueue(candidate.postId());
    }

    @Transactional
    public void enqueue(long postId) {
        List<RiskSignal> signals = jdbcTemplate.query("""
                SELECT p.project_id, COALESCE(a.sentiment, 'NEUTRAL') body_sentiment,
                       COALESCE(a.risk_score, 0) body_risk_score,
                       COALESCE(NULLIF(a.risk_category, ''), '其他') body_risk_category,
                       COALESCE(a.summary, '') body_summary,
                       COALESCE(ca.negative_count, 0) negative_comment_count,
                       COALESCE(ca.maximum_risk_score, 0) comment_risk_score,
                       COALESCE((SELECT c2.summary FROM xhs_comment_analysis_results c2
                           WHERE c2.post_id = p.id AND c2.is_negative = TRUE
                           ORDER BY c2.risk_score DESC, c2.analyzed_at DESC, c2.id DESC LIMIT 1), '') comment_summary,
                       COALESCE(ia.negative_count, 0) negative_image_count,
                       COALESCE(ia.maximum_risk_score, 0) image_risk_score,
                       COALESCE((SELECT i2.summary FROM xhs_post_images i2
                           WHERE i2.post_id = p.id AND i2.analysis_status = 'SUCCEEDED'
                             AND i2.sentiment = 'NEGATIVE'
                           ORDER BY i2.risk_score DESC, i2.analyzed_at DESC, i2.id DESC LIMIT 1), '') image_summary
                FROM xhs_posts p
                LEFT JOIN xhs_analysis_results a ON a.post_id = p.id
                LEFT JOIN (
                    SELECT post_id, SUM(is_negative) negative_count,
                           MAX(CASE WHEN is_negative THEN risk_score ELSE 0 END) maximum_risk_score
                    FROM xhs_comment_analysis_results GROUP BY post_id
                ) ca ON ca.post_id = p.id
                LEFT JOIN (
                    SELECT post_id, SUM(sentiment = 'NEGATIVE') negative_count,
                           MAX(CASE WHEN sentiment = 'NEGATIVE' THEN risk_score ELSE 0 END) maximum_risk_score
                    FROM xhs_post_images WHERE analysis_status = 'SUCCEEDED' GROUP BY post_id
                ) ia ON ia.post_id = p.id
                WHERE p.id = ?
                """, this::mapRiskSignal, postId);
        if (signals.isEmpty()) {
            return;
        }
        RiskSignal signal = signals.get(0);
        if (!signal.negative()) {
            return;
        }
        List<ScheduleTarget> targets = jdbcTemplate.query("""
                SELECT s.id, s.negative_email_minimum_risk_score, s.negative_email_high_risk_only,
                       s.negative_email_cooldown_minutes, r.target_value
                FROM xhs_report_schedules s
                JOIN xhs_report_recipients r ON r.schedule_id = s.id AND r.channel = 'EMAIL' AND r.enabled = 1
                WHERE s.project_id = ? AND s.enabled = 1 AND s.negative_email_enabled = 1
                """, this::mapTarget, signal.projectId());
        Instant now = Instant.now();
        for (ScheduleTarget target : targets) {
            if (signal.riskScore() < target.minimumRiskScore()
                    || target.highRiskOnly() && signal.riskScore() < 80) {
                continue;
            }
            Integer recent = jdbcTemplate.queryForObject("""
                    SELECT COUNT(*) FROM xhs_negative_post_deliveries
                    WHERE post_id = ? AND schedule_id = ? AND recipient_email = ?
                      AND created_at >= ?
                    """, Integer.class, postId, target.scheduleId(), target.email(),
                    Timestamp.from(now.minusSeconds(target.cooldownMinutes() * 60L)));
            if (recent != null && recent > 0) {
                continue;
            }
            jdbcTemplate.update("""
                    INSERT IGNORE INTO xhs_negative_post_deliveries(
                        project_id, post_id, schedule_id, recipient_email,
                        risk_source, risk_score_snapshot, risk_category_snapshot, risk_summary_snapshot, status,
                        attempt_count, next_attempt_at, deduplication_bucket, created_at, updated_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'PENDING', 0, ?, ?, ?, ?)
                    """, signal.projectId(), postId, target.scheduleId(), target.email(),
                    signal.source(), signal.riskScore(), signal.riskCategory(), signal.summary(),
                    Timestamp.from(now), now.getEpochSecond() / Math.max(60L, target.cooldownMinutes() * 60L),
                    Timestamp.from(now), Timestamp.from(now));
        }
    }

    public int dispatchPending() {
        Instant now = Instant.now();
        jdbcTemplate.update("""
                UPDATE xhs_negative_post_deliveries
                SET status = 'PENDING', lock_token = NULL, locked_at = NULL, updated_at = ?
                WHERE status = 'PROCESSING' AND locked_at < ?
                """, Timestamp.from(now), Timestamp.from(now.minusSeconds(900)));
        String lockToken = UUID.randomUUID().toString();
        jdbcTemplate.update("""
                UPDATE xhs_negative_post_deliveries
                SET status = 'PROCESSING', lock_token = ?, locked_at = ?, updated_at = ?
                WHERE status = 'PENDING' AND next_attempt_at <= ?
                ORDER BY next_attempt_at, id LIMIT 20
                """, lockToken, Timestamp.from(now), Timestamp.from(now), Timestamp.from(now));
        List<Delivery> deliveries = jdbcTemplate.query("""
                SELECT d.id, d.post_id, d.recipient_email, pr.project_key, p.title,
                       p.source_url, p.published_at, d.risk_source,
                       COALESCE(NULLIF(d.risk_summary_snapshot, ''), a.summary, '') summary,
                       'NEGATIVE' sentiment,
                       COALESCE(NULLIF(d.risk_category_snapshot, ''), a.risk_category, '其他') risk_category,
                       COALESCE(NULLIF(d.risk_score_snapshot, 0), a.risk_score, 0) risk_score
                FROM xhs_negative_post_deliveries d
                JOIN xhs_posts p ON p.id = d.post_id
                JOIN xhs_monitor_projects pr ON pr.id = p.project_id
                LEFT JOIN xhs_analysis_results a ON a.id = (
                    SELECT a2.id FROM xhs_analysis_results a2
                    WHERE a2.post_id = p.id ORDER BY a2.analyzed_at DESC, a2.id DESC LIMIT 1)
                WHERE d.status = 'PROCESSING' AND d.lock_token = ?
                ORDER BY d.id
                """, this::mapDelivery, lockToken);
        deliveries.forEach(this::dispatch);
        return deliveries.size();
    }

    public List<DeliveryView> history(String projectKey, int limit) {
        String filter = projectKey == null || projectKey.isBlank() ? null : projectKey.strip();
        return jdbcTemplate.query("""
                SELECT d.id, pr.project_key, d.post_id, p.title, d.recipient_email,
                       d.risk_source, d.risk_score_snapshot, d.risk_category_snapshot,
                       d.status, d.attempt_count, d.last_error, d.created_at, d.sent_at, d.updated_at
                FROM xhs_negative_post_deliveries d
                JOIN xhs_posts p ON p.id = d.post_id
                JOIN xhs_monitor_projects pr ON pr.id = d.project_id
                WHERE (? IS NULL OR pr.project_key = ?)
                ORDER BY d.created_at DESC, d.id DESC
                LIMIT ?
                """, (rs, row) -> new DeliveryView(
                rs.getLong("id"), rs.getString("project_key"), rs.getLong("post_id"),
                rs.getString("title"), rs.getString("recipient_email"), rs.getString("risk_source"),
                rs.getInt("risk_score_snapshot"), rs.getString("risk_category_snapshot"),
                rs.getString("status"), rs.getInt("attempt_count"), rs.getString("last_error"),
                instant(rs, "created_at"), instant(rs, "sent_at"), instant(rs, "updated_at")),
                filter, filter, Math.max(1, Math.min(limit <= 0 ? 50 : limit, 200)));
    }

    @Transactional
    public void retry(long deliveryId, String projectKey) {
        if (projectKey == null || projectKey.isBlank()) {
            throw new IllegalArgumentException("projectKey 不能为空");
        }
        Instant now = Instant.now();
        int updated = jdbcTemplate.update("""
                UPDATE xhs_negative_post_deliveries d
                JOIN xhs_monitor_projects p ON p.id = d.project_id
                SET d.status = 'PENDING', d.attempt_count = 0, d.next_attempt_at = ?,
                    d.last_error = NULL, d.lock_token = NULL, d.locked_at = NULL, d.updated_at = ?
                WHERE d.id = ? AND p.project_key = ? AND d.status = 'FAILED'
                """, Timestamp.from(now), Timestamp.from(now), deliveryId, projectKey.strip());
        if (updated != 1) {
            throw new IllegalArgumentException("失败邮件记录不存在或已重新进入发送队列");
        }
    }

    private void dispatch(Delivery delivery) {
        try {
            byte[] report = report(delivery);
            String body = "小红书负面舆情检测报告\n\n"
                    + "项目：" + delivery.projectKey() + "\n"
                    + "标题：" + delivery.title() + "\n"
                    + "发布时间：" + delivery.publishedAt() + "\n"
                    + "风险来源：" + sourceLabel(delivery.riskSource()) + "\n"
                    + "情感：" + delivery.sentiment() + "\n"
                    + "风险分：" + delivery.riskScore() + "\n"
                    + "风险类别：" + delivery.riskCategory() + "\n"
                    + "摘要：" + delivery.summary() + "\n"
                    + "原帖链接：" + delivery.sourceUrl();
            emailClient.sendWithAttachments(
                    new EmailMessage(List.of(delivery.email()), "【小红书负面舆情·"
                            + sourceLabel(delivery.riskSource()) + "】" + delivery.title(), body, List.of(), List.of()),
                    List.of(new EmailAttachment(report, "负面舆情_" + sourceLabel(delivery.riskSource())
                            + "_" + delivery.postId() + ".docx",
                            "application/vnd.openxmlformats-officedocument.wordprocessingml.document")));
            mark(delivery.id(), "SENT", null, true);
        } catch (RuntimeException exception) {
            mark(delivery.id(), "FAILED", safeMessage(exception), false);
        }
    }

    private byte[] report(Delivery value) {
        try (XWPFDocument document = new XWPFDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            paragraph(document, "小红书负面舆情帖子报告");
            paragraph(document, "项目：" + value.projectKey());
            paragraph(document, "标题：" + value.title());
            paragraph(document, "发布时间：" + value.publishedAt());
            paragraph(document, "风险来源：" + sourceLabel(value.riskSource()));
            paragraph(document, "情感：" + value.sentiment());
            paragraph(document, "风险分：" + value.riskScore());
            paragraph(document, "风险类别：" + value.riskCategory());
            paragraph(document, "分析摘要：" + value.summary());
            paragraph(document, "原帖链接：" + value.sourceUrl());
            document.write(output);
            return output.toByteArray();
        } catch (Exception exception) {
            throw new IllegalStateException("无法生成负面帖子报告", exception);
        }
    }

    private void paragraph(XWPFDocument document, String text) {
        XWPFParagraph paragraph = document.createParagraph();
        paragraph.createRun().setText(text == null ? "" : text);
    }

    private void mark(long id, String status, String error, boolean sent) {
        Instant now = Instant.now();
        jdbcTemplate.update("""
                UPDATE xhs_negative_post_deliveries
                SET status = CASE WHEN ? = 'SENT' THEN 'SENT' WHEN attempt_count + 1 >= 3 THEN 'FAILED' ELSE 'PENDING' END,
                    attempt_count = attempt_count + 1, last_error = ?,
                    sent_at = ?, next_attempt_at = ?, lock_token = NULL, locked_at = NULL,
                    updated_at = ? WHERE id = ?
                """, status, error, sent ? Timestamp.from(now) : null,
                Timestamp.from(sent ? now : now.plusSeconds(300)), Timestamp.from(now), id);
    }

    private ScheduleTarget mapTarget(ResultSet rs, int row) throws SQLException {
        return new ScheduleTarget(rs.getLong("id"), rs.getInt("negative_email_minimum_risk_score"),
                rs.getBoolean("negative_email_high_risk_only"), rs.getInt("negative_email_cooldown_minutes"),
                rs.getString("target_value"));
    }

    private RiskSignal mapRiskSignal(ResultSet rs, int row) throws SQLException {
        int bodyRisk = rs.getInt("body_risk_score");
        int commentRisk = rs.getInt("comment_risk_score");
        int imageRisk = rs.getInt("image_risk_score");
        int effectiveRisk = Math.max(bodyRisk, Math.max(commentRisk, imageRisk));
        boolean negative = "NEGATIVE".equalsIgnoreCase(rs.getString("body_sentiment"))
                || rs.getInt("negative_comment_count") > 0 || rs.getInt("negative_image_count") > 0;
        String source = riskSource(bodyRisk, commentRisk, imageRisk, effectiveRisk);
        String category = rs.getString("body_risk_category");
        String summary = rs.getString("body_summary");
        if (commentRisk > bodyRisk && commentRisk >= imageRisk) {
            category = "评论负面反馈";
            summary = rs.getString("comment_summary");
        } else if (imageRisk > bodyRisk && imageRisk > commentRisk) {
            category = "图片负面反馈";
            summary = rs.getString("image_summary");
        }
        return new RiskSignal(rs.getLong("project_id"), negative, effectiveRisk, source,
                category == null || category.isBlank() ? "其他" : category,
                summary == null ? "" : summary);
    }

    private String riskSource(int bodyRisk, int commentRisk, int imageRisk, int effectiveRisk) {
        java.util.ArrayList<String> sources = new java.util.ArrayList<>(3);
        if (bodyRisk == effectiveRisk && bodyRisk > 0) sources.add("POST");
        if (commentRisk == effectiveRisk && commentRisk > 0) sources.add("COMMENT");
        if (imageRisk == effectiveRisk && imageRisk > 0) sources.add("IMAGE");
        return sources.isEmpty() ? "POST" : String.join("+", sources);
    }

    private String sourceLabel(String value) {
        if (value == null || value.isBlank()) return "正文";
        return value.replace("POST", "正文").replace("COMMENT", "评论").replace("IMAGE", "图片")
                .replace("+", "+");
    }

    private Delivery mapDelivery(ResultSet rs, int row) throws SQLException {
        return new Delivery(rs.getLong("id"), rs.getLong("post_id"), rs.getString("recipient_email"),
                rs.getString("project_key"), rs.getString("title"), rs.getString("source_url"),
                rs.getTimestamp("published_at") == null ? null : rs.getTimestamp("published_at").toInstant(),
                rs.getString("risk_source"), rs.getString("summary"),
                rs.getString("sentiment"), rs.getString("risk_category"),
                rs.getInt("risk_score"));
    }

    private String safeMessage(Throwable throwable) {
        String message = throwable == null ? "未知错误" : throwable.getMessage();
        return message == null || message.isBlank() ? "未知错误" : message.substring(0, Math.min(message.length(), 2000));
    }

    private record ScheduleTarget(long scheduleId, int minimumRiskScore, boolean highRiskOnly,
                                  int cooldownMinutes, String email) {
    }

    private Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    private record RiskSignal(long projectId, boolean negative, int riskScore, String source,
                              String riskCategory, String summary) {
    }

    private record Delivery(long id, long postId, String email, String projectKey, String title,
                            String sourceUrl, Instant publishedAt, String riskSource,
                            String summary, String sentiment,
                            String riskCategory, int riskScore) {
    }

    public record DeliveryView(long id, String projectKey, long postId, String title,
                               String recipientEmail, String riskSource, int riskScore,
                               String riskCategory, String status, int attemptCount,
                               String lastError, Instant createdAt, Instant sentAt, Instant updatedAt) {
    }
}
