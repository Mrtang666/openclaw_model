package com.example.spring.xhs.link;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import com.example.spring.xhs.source.XhsResolvedLink;
import com.example.spring.xhs.source.XhsSourceClient;

import java.net.URI;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

@Service
public class XhsPostLinkService {

    private final JdbcTemplate jdbcTemplate;
    private final XhsSourceClient sourceClient;

    public XhsPostLinkService(JdbcTemplate jdbcTemplate, ObjectProvider<XhsSourceClient> sourceClientProvider) {
        this.jdbcTemplate = jdbcTemplate;
        this.sourceClient = sourceClientProvider.getIfAvailable();
    }

    public URI accessUri(long postId) {
        List<PostLink> links = jdbcTemplate.query("""
                SELECT p.source_post_id, p.access_url, p.project_id, p.title,
                       pr.project_key, pr.name project_name
                FROM xhs_posts p
                JOIN xhs_monitor_projects pr ON pr.id = p.project_id
                WHERE p.id = ?
                """, (rs, row) -> new PostLink(
                rs.getString("source_post_id"), rs.getString("access_url"),
                rs.getLong("project_id"), rs.getString("project_key"),
                rs.getString("project_name"), rs.getString("title")), postId);
        if (links.isEmpty()) {
            throw new IllegalArgumentException("未找到指定的小红书笔记");
        }
        PostLink link = links.get(0);
        String safeUrl = XhsAccessUrlPolicy.sanitize(link.accessUrl(), link.sourcePostId());
        if (safeUrl.isBlank()) {
            safeUrl = refresh(link);
        }
        return URI.create(safeUrl);
    }

    private String refresh(PostLink link) {
        if (sourceClient == null) {
            throw new IllegalStateException("采集 Sidecar 未启用，无法刷新原帖链接");
        }
        String query = latestQuery(link.projectId());
        if (query.isBlank()) {
            query = link.title();
        }
        if (query == null || query.isBlank()) {
            throw new IllegalStateException("该笔记缺少可用于刷新链接的搜索关键词");
        }
        XhsResolvedLink resolved = sourceClient.resolveLink(link.sourcePostId(), query, 100);
        String safeUrl = XhsAccessUrlPolicy.sanitize(resolved.accessUrl(), link.sourcePostId());
        if (!resolved.found() || safeUrl.isBlank()) {
            if ("AUTH_EXPIRED".equals(resolved.errorCode())) {
                throw new IllegalStateException("小红书授权 Cookie 已失效，请更新后重启 Sidecar");
            }
            throw new IllegalStateException("未能在最新搜索结果中找到该笔记，请重新采集项目后再试");
        }
        jdbcTemplate.update("""
                UPDATE xhs_posts
                SET access_url = ?, access_url_refreshed_at = ?
                WHERE project_id = ? AND source_post_id = ?
                """, safeUrl, Timestamp.from(Instant.now()), link.projectId(), link.sourcePostId());
        return safeUrl;
    }

    private String latestQuery(long projectId) {
        List<String> queries = jdbcTemplate.query("""
                SELECT query_text
                FROM xhs_collection_jobs
                WHERE project_id = ? AND status IN ('SUCCEEDED', 'PARTIAL')
                  AND query_text IS NOT NULL AND query_text <> ''
                ORDER BY started_at DESC, id DESC
                LIMIT 1
                """, (rs, row) -> rs.getString("query_text"), projectId);
        return queries.isEmpty() || queries.get(0) == null ? "" : queries.get(0).strip();
    }

    private record PostLink(String sourcePostId, String accessUrl, long projectId,
                            String projectKey, String projectName, String title) {
    }
}
