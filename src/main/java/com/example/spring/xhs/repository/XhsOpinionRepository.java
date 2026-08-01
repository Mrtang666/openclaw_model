package com.example.spring.xhs.repository;

import com.example.spring.xhs.model.XhsCommentImport;
import com.example.spring.xhs.model.XhsPostImport;

import java.time.Instant;

public interface XhsOpinionRepository {

    long ensureProject(String projectKey, String projectName, Instant now);

    long upsertPost(long projectId, XhsPostImport post);

    void upsertComment(long postId, XhsCommentImport comment);

    void saveMetricSnapshot(long postId, XhsPostImport post);
}
