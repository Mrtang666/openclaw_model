package com.example.spring.agent.goal;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class AgentGoalTrackerTests {

    @Test
    void recordsSuccessfulGoalLifecycle() {
        AgentGoalService service = mock(AgentGoalService.class);
        AgentGoalHandle handle = new AgentGoalHandle(7L);
        when(service.startWechatGoal("session", "objective")).thenReturn(Optional.of(handle));

        AgentGoalTracker tracker = AgentGoalTracker.start(service, "session", "objective");
        tracker.recordToolStep("web_search", Map.of("query", "x"), "ok", "SUCCESS");
        tracker.succeed("visible reply");

        verify(service).startWechatGoal("session", "objective");
        verify(service).recordToolStep(handle, "web_search", Map.of("query", "x"), "ok", "SUCCESS");
        verify(service).complete(handle, "visible reply");
        verify(service).recordEvaluation(
                handle,
                "rule-based",
                AgentGoalEvaluationStatus.PASSED,
                "reply contains user-visible content");
    }

    @Test
    void recordsFailedGoalLifecycleAndReviewAction() {
        AgentGoalService service = mock(AgentGoalService.class);
        AgentGoalHandle handle = new AgentGoalHandle(8L);
        when(service.startWechatGoal("session", "objective")).thenReturn(Optional.of(handle));

        AgentGoalTracker tracker = AgentGoalTracker.start(service, "session", "objective");
        tracker.fail(
                "Function Calling Agent Loop 未返回可用回复",
                "Function Calling Agent Loop returned no user-visible reply");

        verify(service).fail(handle, "Function Calling Agent Loop 未返回可用回复");
        verify(service).recordEvaluation(
                handle,
                "rule-based",
                AgentGoalEvaluationStatus.FAILED,
                "Function Calling Agent Loop returned no user-visible reply");
        verify(service).recordFailureReviewAction(
                handle,
                "Function Calling Agent Loop returned no user-visible reply");
    }

    @Test
    void noopsWhenGoalServiceIsUnavailable() {
        AgentGoalTracker tracker = AgentGoalTracker.start(null, "session", "objective");

        assertThatCode(() -> {
            tracker.recordToolStep("web_search", Map.of("query", "x"), "ok", "SUCCESS");
            tracker.succeed("visible reply");
            tracker.fail("user summary", "machine reason");
        }).doesNotThrowAnyException();
    }

    @Test
    void noopsAfterGoalStartFails() {
        AgentGoalService service = mock(AgentGoalService.class);
        when(service.startWechatGoal("session", "objective")).thenThrow(new IllegalStateException("down"));

        AgentGoalTracker tracker = AgentGoalTracker.start(service, "session", "objective");
        tracker.recordToolStep("web_search", Map.of("query", "x"), "ok", "SUCCESS");
        tracker.succeed("visible reply");
        tracker.fail("user summary", "machine reason");

        verify(service).startWechatGoal("session", "objective");
        verifyNoMoreInteractions(service);
    }
}
