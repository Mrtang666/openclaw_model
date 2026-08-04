package com.example.spring.xhs.repository;

import com.example.spring.xhs.model.XhsCollectionJob;
import com.example.spring.xhs.model.XhsSourceType;
import com.example.spring.xhs.source.XhsCollectionStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface XhsCollectionJobRepository {

    void create(String jobKey, long projectId, XhsSourceType sourceType, String query, Instant now);

    void markSubmitted(String jobKey, String externalJobId);

    List<XhsCollectionJob> findPending(int limit);

    default List<XhsCollectionClaim> claimPending(int limit) {
        return findPending(limit).stream()
                .map(job -> new XhsCollectionClaim(job, "legacy-" + UUID.randomUUID()))
                .toList();
    }

    default void releaseClaim(XhsCollectionClaim claim) {
    }

    void recordPoll(String jobKey, XhsCollectionStatus status);

    default void recordPoll(String jobKey, XhsCollectionStatus status, String errorMessage, Instant nextPollAt) {
        recordPoll(jobKey, status);
    }

    default void recordPoll(XhsCollectionClaim claim, XhsCollectionStatus status,
                            String errorMessage, Instant nextPollAt) {
        recordPoll(claim.job().jobKey(), status, errorMessage, nextPollAt);
    }

    void finish(
            String jobKey,
            XhsCollectionStatus status,
            boolean complete,
            int recordCount,
            String nextCursor,
            String errorCode,
            String errorMessage,
            Instant finishedAt);

    default void finish(XhsCollectionClaim claim, XhsCollectionStatus status, boolean complete,
                        int recordCount, String nextCursor, String errorCode,
                        String errorMessage, Instant finishedAt) {
        finish(claim.job().jobKey(), status, complete, recordCount, nextCursor,
                errorCode, errorMessage, finishedAt);
    }
}
