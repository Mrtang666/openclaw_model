package com.example.spring.xhs.schedule;

import com.example.spring.wechat.email.client.EmailClient;
import com.example.spring.wechat.email.model.EmailAttachment;
import com.example.spring.wechat.email.model.EmailMessage;
import com.example.spring.xhs.analysis.XhsAnalysisCandidate;
import com.example.spring.xhs.analysis.XhsRiskAssessment;
import com.example.spring.xhs.analysis.XhsSemanticAssessment;
import com.example.spring.xhs.analysis.XhsSentiment;
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
        if (semantic.sentiment() != XhsSentiment.NEGATIVE) {
            return;
        }
        List<ScheduleTarget> targets = jdbcTemplate.query("""
                SELECT s.id, s.negative_email_minimum_risk_score, s.negative_email_high_risk_only,
                       s.negative_email_cooldown_minutes, r.target_value
                FROM xhs_report_schedules s
                JOIN xhs_report_recipients r ON r.schedule_id = s.id AND r.channel = 'EMAIL' AND r.enabled = 1
                WHERE s.project_id = ? AND s.enabled = 1 AND s.negative_email_enabled = 1
                """, this::mapTarget, candidate.projectId());
        Instant now = Instant.now();
        for (ScheduleTarget target : targets) {
            if (risk.riskScore() < target.minimumRiskScore()
                    || target.highRiskOnly() && !"CRITICAL".equalsIgnoreCase(risk.riskLevel())) {
                continue;
            }
            Integer recent = jdbcTemplate.queryForObject("""
                    SELECT COUNT(*) FROM xhs_negative_post_deliveries
                    WHERE post_id = ? AND schedule_id = ? AND recipient_email = ?
                      AND created_at >= ?
                    """, Integer.class, candidate.postId(), target.scheduleId(), target.email(),
                    Timestamp.from(now.minusSeconds(target.cooldownMinutes() * 60L)));
            if (recent != null && recent > 0) {
                continue;
            }
            jdbcTemplate.update("""
                    INSERT IGNORE INTO xhs_negative_post_deliveries(
                        project_id, post_id, schedule_id, recipient_email, status,
                        attempt_count, next_attempt_at, deduplication_bucket, created_at, updated_at)
                    VALUES (?, ?, ?, ?, 'PENDING', 0, ?, ?, ?, ?)
                    """, candidate.projectId(), candidate.postId(), target.scheduleId(), target.email(),
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
                SELECT d.id, d.post_id, d.recipient_email, pr.project_key, p.title, p.content,
                       p.source_url, p.published_at, a.summary, a.sentiment, a.risk_category,
                       a.risk_score, a.evidence_json
                FROM xhs_negative_post_deliveries d
                JOIN xhs_posts p ON p.id = d.post_id
                JOIN xhs_monitor_projects pr ON pr.id = p.project_id
                JOIN xhs_analysis_results a ON a.id = (
                    SELECT a2.id FROM xhs_analysis_results a2
                    WHERE a2.post_id = p.id ORDER BY a2.analyzed_at DESC, a2.id DESC LIMIT 1)
                WHERE d.status = 'PROCESSING' AND d.lock_token = ?
                ORDER BY d.id
                """, this::mapDelivery, lockToken);
        deliveries.forEach(this::dispatch);
        return deliveries.size();
    }

    private void dispatch(Delivery delivery) {
        try {
            byte[] report = report(delivery);
            String body = "小红书负面舆情检测报告\n\n"
                    + "项目：" + delivery.projectKey() + "\n"
                    + "标题：" + delivery.title() + "\n"
                    + "发布时间：" + delivery.publishedAt() + "\n"
                    + "情感：" + delivery.sentiment() + "\n"
                    + "风险分：" + delivery.riskScore() + "\n"
                    + "风险类别：" + delivery.riskCategory() + "\n"
                    + "摘要：" + delivery.summary() + "\n"
                    + "原帖链接：" + delivery.sourceUrl();
            emailClient.sendWithAttachments(
                    new EmailMessage(List.of(delivery.email()), "【小红书负面舆情】" + delivery.title(), body, List.of(), List.of()),
                    List.of(new EmailAttachment(report, "负面舆情_" + delivery.postId() + ".docx",
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

    private Delivery mapDelivery(ResultSet rs, int row) throws SQLException {
        return new Delivery(rs.getLong("id"), rs.getLong("post_id"), rs.getString("recipient_email"),
                rs.getString("project_key"), rs.getString("title"), rs.getString("source_url"),
                rs.getTimestamp("published_at") == null ? null : rs.getTimestamp("published_at").toInstant(),
                rs.getString("summary"), rs.getString("sentiment"), rs.getString("risk_category"),
                rs.getInt("risk_score"));
    }

    private String safeMessage(Throwable throwable) {
        String message = throwable == null ? "未知错误" : throwable.getMessage();
        return message == null || message.isBlank() ? "未知错误" : message.substring(0, Math.min(message.length(), 2000));
    }

    private record ScheduleTarget(long scheduleId, int minimumRiskScore, boolean highRiskOnly,
                                  int cooldownMinutes, String email) {
    }

    private record Delivery(long id, long postId, String email, String projectKey, String title,
                            String sourceUrl, Instant publishedAt, String summary, String sentiment,
                            String riskCategory, int riskScore) {
    }
}
