package com.example.spring.agent.goal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

@Service
public class AgentGoalService {

    private static final Logger log = LoggerFactory.getLogger(AgentGoalService.class);
    private static final String CHANNEL_WECHAT = "WECHAT";
    private static final String GOAL_TYPE_WECHAT_MESSAGE = "wechat_message";

    private final AgentGoalRepository repository;
    private final Clock clock;

    @Autowired
    public AgentGoalService(AgentGoalRepository repository) {
        this(repository, Clock.systemUTC());
    }

    AgentGoalService(AgentGoalRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock == null ? Clock.systemUTC() : clock;
    }

    public Optional<AgentGoalHandle> startWechatGoal(String sessionKey, String objective) {
        try {
            return Optional.of(repository.create(new AgentGoalDraft(
                    CHANNEL_WECHAT,
                    sessionKey,
                    GOAL_TYPE_WECHAT_MESSAGE,
                    objective,
                    AgentGoalStatus.RUNNING,
                    now())));
        } catch (RuntimeException exception) {
            log.warn("Agent Goal 创建失败，channel={}, sessionKey={}, error={}",
                    CHANNEL_WECHAT, safe(sessionKey), rootMessage(exception));
            return Optional.empty();
        }
    }

    public void complete(AgentGoalHandle handle, String resultSummary) {
        update(handle, AgentGoalStatus.COMPLETED, resultSummary);
    }

    public void fail(AgentGoalHandle handle, String resultSummary) {
        update(handle, AgentGoalStatus.FAILED, resultSummary);
    }

    public void recordToolStep(
            AgentGoalHandle handle,
            String toolName,
            Map<String, String> arguments,
            String resultSummary,
            String status) {
        if (handle == null) {
            return;
        }
        try {
            repository.recordStep(handle, new AgentGoalStepDraft(
                    toolName,
                    arguments,
                    resultSummary,
                    status,
                    now()));
        } catch (RuntimeException exception) {
            log.warn("Agent Goal Step 记录失败，goalId={}, tool={}, status={}, error={}",
                    handle.goalId(), safe(toolName), safe(status), rootMessage(exception));
        }
    }

    public void recordEvaluation(
            AgentGoalHandle handle,
            String evaluatorName,
            AgentGoalEvaluationStatus status,
            String reasoning) {
        if (handle == null) {
            return;
        }
        try {
            repository.recordEvaluation(handle, new AgentGoalEvaluationDraft(
                    evaluatorName,
                    status,
                    reasoning,
                    now()));
        } catch (RuntimeException exception) {
            log.warn("Agent Goal Evaluation 记录失败，goalId={}, evaluator={}, status={}, error={}",
                    handle.goalId(), safe(evaluatorName), status, rootMessage(exception));
        }
    }

    public void recordFailureReviewAction(AgentGoalHandle handle, String reason) {
        if (handle == null) {
            return;
        }
        try {
            repository.recordReviewAction(handle, new AgentGoalReviewActionDraft(
                    reviewActionType(reason),
                    AgentGoalReviewActionStatus.PENDING,
                    reason,
                    now()));
        } catch (RuntimeException exception) {
            log.warn("Agent Goal Review Action 记录失败，goalId={}, error={}",
                    handle.goalId(), rootMessage(exception));
        }
    }

    private void update(AgentGoalHandle handle, AgentGoalStatus status, String resultSummary) {
        if (handle == null) {
            return;
        }
        try {
            repository.updateStatus(handle, status, resultSummary, now());
        } catch (RuntimeException exception) {
            log.warn("Agent Goal 状态更新失败，goalId={}, status={}, error={}",
                    handle.goalId(), status, rootMessage(exception));
        }
    }

    private AgentGoalReviewActionType reviewActionType(String reason) {
        String value = safe(reason).toLowerCase(java.util.Locale.ROOT);
        if (value.contains("no user-visible reply") || value.contains("未返回可用回复")) {
            return AgentGoalReviewActionType.IMPROVE_PROMPT;
        }
        return AgentGoalReviewActionType.RETRY;
    }

    private Instant now() {
        return Instant.now(clock);
    }

    private String safe(String value) {
        return value == null ? "" : value.strip();
    }

    private String rootMessage(Throwable exception) {
        Throwable current = exception;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }
}
