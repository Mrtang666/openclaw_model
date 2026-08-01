package com.example.spring.agent.goal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

@Service
public class AgentGoalReviewService {

    private static final Logger log = LoggerFactory.getLogger(AgentGoalReviewService.class);
    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 100;

    private final AgentGoalRepository repository;
    private final Clock clock;

    @Autowired
    public AgentGoalReviewService(AgentGoalRepository repository) {
        this(repository, Clock.systemUTC());
    }

    AgentGoalReviewService(AgentGoalRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock == null ? Clock.systemUTC() : clock;
    }

    public List<AgentGoalReviewAction> pendingActions(int limit) {
        try {
            return repository.findPendingReviewActions(normalizeLimit(limit));
        } catch (RuntimeException exception) {
            log.warn("Agent Goal Review Action 查询失败，limit={}, error={}", limit, rootMessage(exception));
            return List.of();
        }
    }

    public void markApplied(long actionId) {
        updateStatus(actionId, AgentGoalReviewActionStatus.APPLIED);
    }

    public void markDismissed(long actionId) {
        updateStatus(actionId, AgentGoalReviewActionStatus.DISMISSED);
    }

    private void updateStatus(long actionId, AgentGoalReviewActionStatus status) {
        if (actionId <= 0) {
            return;
        }
        try {
            repository.updateReviewActionStatus(actionId, status, now());
        } catch (RuntimeException exception) {
            log.warn("Agent Goal Review Action 状态更新失败，actionId={}, status={}, error={}",
                    actionId, status, rootMessage(exception));
        }
    }

    private int normalizeLimit(int limit) {
        if (limit <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, MAX_LIMIT);
    }

    private Instant now() {
        return Instant.now(clock);
    }

    private String rootMessage(Throwable exception) {
        Throwable current = exception;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }
}
