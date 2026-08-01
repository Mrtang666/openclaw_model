package com.example.spring.xhs.ingestion;

import com.example.spring.xhs.model.XhsCommentImport;
import com.example.spring.xhs.model.XhsPostImport;
import com.example.spring.xhs.model.XhsSourceType;
import com.example.spring.xhs.repository.XhsOpinionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class XhsJsonImportServiceTests {

    @Test
    void importsSpiderFieldsAndNestedComments() {
        FakeRepository repository = new FakeRepository();
        XhsJsonImportService service = new XhsJsonImportService(
                new ObjectMapper(),
                repository,
                new XhsAuthorKeyHasher("test-secret"));

        var result = service.importJson("brand-a", "品牌 A", json("""
                {
                  "source": "SPIDER_XHS_LAB",
                  "collectedAt": "2026-07-28T02:00:00Z",
                  "posts": [{
                    "note_id": "note-1",
                    "note_url": "https://www.xiaohongshu.com/explore/note-1?xsec_token=secret-token",
                    "access_url": "https://www.xiaohongshu.com/explore/note-1?xsec_token=access-token&xsec_source=pc_search&other=discarded",
                    "user_id": "raw-user-id",
                    "title": "使用体验",
                    "desc": "使用后脸部发红",
                    "note_type": "图集",
                    "tags": ["品牌A", "敏感肌"],
                    "upload_time": "2026-07-27 12:30:00",
                    "liked_count": "1.2万",
                    "collected_count": "320",
                    "comment_count": 18,
                    "share_count": "2K",
                    "comments": [{
                      "comment_id": "comment-1",
                      "user_id": "comment-user",
                      "content": "我也出现了类似情况",
                      "like_count": 5,
                      "create_time": 1785126600000,
                      "sub_comments": [{
                        "comment_id": "comment-2",
                        "parent_comment_id": "comment-1",
                        "content": "建议停用"
                      }]
                    }]
                  }]
                }
                """));

        assertThat(result.projectKey()).isEqualTo("brand-a");
        assertThat(result.sourceType()).isEqualTo(XhsSourceType.SPIDER_XHS_LAB);
        assertThat(result.postCount()).isEqualTo(1);
        assertThat(result.commentCount()).isEqualTo(2);
        assertThat(result.skippedCount()).isZero();

        XhsPostImport post = repository.posts.get(0);
        assertThat(post.sourcePostId()).isEqualTo("note-1");
        assertThat(post.sourceUrl()).isEqualTo("https://www.xiaohongshu.com/explore/note-1");
        assertThat(post.accessUrl()).isEqualTo("https://www.xiaohongshu.com/explore/note-1?xsec_token=access-token&xsec_source=pc_search");
        assertThat(post.authorKey()).hasSize(64).doesNotContain("raw-user-id");
        assertThat(post.rawJson()).doesNotContain("raw-user-id", "comment-user", "secret-token", "access-token", "xsec_token");
        assertThat(post.content()).isEqualTo("使用后脸部发红");
        assertThat(post.tags()).containsExactly("品牌A", "敏感肌");
        assertThat(post.publishedAt()).isEqualTo(Instant.parse("2026-07-27T04:30:00Z"));
        assertThat(post.metrics().likedCount()).isEqualTo(12_000);
        assertThat(post.metrics().shareCount()).isEqualTo(2_000);
        assertThat(repository.comments).extracting(XhsCommentImport::sourceCommentId)
                .containsExactly("comment-1", "comment-2");
        assertThat(repository.metricPostIds).containsExactly(101L);
    }

    @Test
    void acceptsArrayInputAndSkipsRecordsWithoutIds() {
        FakeRepository repository = new FakeRepository();
        XhsJsonImportService service = new XhsJsonImportService(
                new ObjectMapper(),
                repository,
                new XhsAuthorKeyHasher("test-secret"));

        var result = service.importJson("brand-a", "品牌 A", json("""
                [
                  {"note_id":"valid-1","title":"有效笔记"},
                  {"title":"缺少笔记 ID"}
                ]
                """));

        assertThat(result.sourceType()).isEqualTo(XhsSourceType.FILE_IMPORT);
        assertThat(result.postCount()).isEqualTo(1);
        assertThat(result.skippedCount()).isEqualTo(1);
        assertThat(repository.posts).extracting(XhsPostImport::sourcePostId).containsExactly("valid-1");
    }

    private ByteArrayInputStream json(String value) {
        return new ByteArrayInputStream(value.getBytes(StandardCharsets.UTF_8));
    }

    private static final class FakeRepository implements XhsOpinionRepository {

        private final List<XhsPostImport> posts = new ArrayList<>();
        private final List<XhsCommentImport> comments = new ArrayList<>();
        private final List<Long> metricPostIds = new ArrayList<>();

        @Override
        public long ensureProject(String projectKey, String projectName, Instant now) {
            return 7L;
        }

        @Override
        public long upsertPost(long projectId, XhsPostImport post) {
            posts.add(post);
            return 100L + posts.size();
        }

        @Override
        public void upsertComment(long postId, XhsCommentImport comment) {
            comments.add(comment);
        }

        @Override
        public void saveMetricSnapshot(long postId, XhsPostImport post) {
            metricPostIds.add(postId);
        }
    }
}
