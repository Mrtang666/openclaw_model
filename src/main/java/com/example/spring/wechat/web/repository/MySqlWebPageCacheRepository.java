package com.example.spring.wechat.web.repository;

import com.example.spring.wechat.web.model.WebPageContent;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;

/**
 * MySQL 网页缓存实现，缓存网页标题和正文。
 */
@Repository
public class MySqlWebPageCacheRepository implements WebPageCacheRepository {

    private final JdbcTemplate jdbcTemplate;

    public MySqlWebPageCacheRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<WebPageContent> findFresh(String urlHash, Instant now) {
        return jdbcTemplate.query("""
                        SELECT url, title, content, fetched_at
                        FROM wechat_web_page_cache
                        WHERE url_hash = ? AND (expire_at IS NULL OR expire_at > ?)
                        LIMIT 1
                        """,
                ps -> {
                    ps.setString(1, urlHash);
                    ps.setTimestamp(2, Timestamp.from(now));
                },
                rs -> {
                    if (!rs.next()) {
                        return Optional.empty();
                    }
                    return Optional.of(new WebPageContent(
                            rs.getString("url"),
                            rs.getString("title"),
                            rs.getString("content"),
                            rs.getTimestamp("fetched_at").toInstant()));
                });
    }

    @Override
    public void save(String urlHash, WebPageContent page, Instant expireAt) {
        jdbcTemplate.update("""
                        INSERT INTO wechat_web_page_cache(url_hash, url, title, content, fetched_at, expire_at)
                        VALUES (?, ?, ?, ?, ?, ?)
                        ON DUPLICATE KEY UPDATE
                            url = VALUES(url),
                            title = VALUES(title),
                            content = VALUES(content),
                            fetched_at = VALUES(fetched_at),
                            expire_at = VALUES(expire_at)
                        """,
                urlHash,
                page.url(),
                page.title(),
                page.content(),
                Timestamp.from(page.fetchedAt()),
                expireAt == null ? null : Timestamp.from(expireAt));
    }
}
