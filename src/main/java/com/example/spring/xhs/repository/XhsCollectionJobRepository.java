package com.example.spring.xhs.repository;

import com.example.spring.xhs.model.XhsCollectionJob;
import com.example.spring.xhs.model.XhsSourceType;
import com.example.spring.xhs.source.XhsCollectionStatus;

import java.time.Instant;
import java.util.List;

public interface XhsCollectionJobRepository {

    void create(String jobKey, long projectId, XhsSourceType sourceType, String query, Instant now);

    void markSubmitted(String jobKey, String externalJobId);

    List<XhsCollectionJob> findPending(int limit);

    void recordPoll(String jobKey, XhsCollectionStatus status);

    void finish(
            String jobKey,
            XhsCollectionStatus status,
            boolean complete,
            int recordCount,
            String nextCursor,
            String errorCode,
            String errorMessage,
            Instant finishedAt);
}
