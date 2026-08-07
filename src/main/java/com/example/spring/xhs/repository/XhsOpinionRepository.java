package com.example.spring.xhs.repository;

import com.example.spring.xhs.model.XhsCommentImport;
import com.example.spring.xhs.model.XhsPostImport;

import java.time.Instant;

public interface XhsOpinionRepository {

    long ensureProject(String projectKey, String projectName, Instant now);

    long upsertPost(long projectId, XhsPostImport post);

    void upsertComment(long postId, XhsCommentImport comment);

    void saveMetricSnapshot(long postId, XhsPostImport post);

    default void recordSearchHit(long postId, String jobKey, String keyword, Instant hitAt) {
    }

    default void updateCollectionCompleteness(long postId, long expectedCommentCount,
                                              int collectedCommentCount, int discoveredImageCount,
                                              Instant collectedAt) {
    }

    default void recordCommentAnalysis(long postId, String sourceCommentId, String sentiment,
                                       int riskScore, boolean negative, Instant analyzedAt) {
    }

    default void recordPostImage(long postId, int imageOrder, String imageUrl, Instant collectedAt) {
    }
}
