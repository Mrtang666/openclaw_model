package com.example.spring.wechat.reminder.repository;

import com.example.spring.wechat.reminder.model.ReminderTask;
import com.example.spring.wechat.reminder.model.ReminderStatus;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ReminderTaskRepository {

    ReminderTask save(ReminderTask task);

    Optional<ReminderTask> findById(long id);

    Optional<ReminderTask> findByIdAndSession(long id, String sessionKey);

    List<ReminderTask> listBySession(String sessionKey);

    List<ReminderTask> listBySession(
            String sessionKey, ReminderStatus status, String keyword, int limit);

    Optional<ReminderTask> findLatestDeliveredBySession(String sessionKey);

    boolean cancel(long id, String sessionKey, Instant now);

    boolean complete(long id, String sessionKey, Instant now);

    boolean snooze(long id, String sessionKey, Instant nextExecuteAt, Instant now);

    boolean updateActive(
            long id,
            String sessionKey,
            String title,
            String content,
            String timezone,
            Instant nextExecuteAt,
            Instant now);

    int rebindConnection(
            String previousConnectionId,
            String recipientId,
            String connectionId,
            String sessionKey,
            Instant now);

    int adoptSingleKnownConnection(
            String recipientId,
            String connectionId,
            String sessionKey,
            Instant now);

    int releaseExpiredLocks(Instant expiredBefore, Instant now);

    List<Long> findDueIds(Instant now, int limit);

    boolean claimForDelivery(long id, Instant now);

    void recordDeliveryStarted(long taskId, Instant scheduledAt, String idempotencyKey, Instant now);

    void markDelivered(long taskId, String idempotencyKey, Instant nextExecuteAt, Instant now);

    void markDeliveryFailed(
            long taskId,
            String idempotencyKey,
            Instant retryAt,
            boolean terminal,
            String errorMessage,
            Instant now);
}
