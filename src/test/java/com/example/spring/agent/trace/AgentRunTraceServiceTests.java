package com.example.spring.agent.trace;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentRunTraceServiceTests {

    @Test
    void delegatesTraceCallsToRepository() {
        AgentRunRepository repository = mock(AgentRunRepository.class);
        AgentRunHandle handle = new AgentRunHandle(9L, "run-9");
        when(repository.createRun("WECHAT", "session", "hello", "context"))
                .thenReturn(handle);
        AgentRunTraceService service = new AgentRunTraceService(repository);

        AgentRunHandle started = service.startWechatRun("session", "hello", "context");
        service.recordModelRound(started, 1, "request", "response", Map.of("tool_count", 0));
        service.recordToolResult(started, 1, "weather", AgentRunStepStatus.SUCCESS, "city=杭州", "晴");
        service.recordPolicyDecision(
                started,
                1,
                "weather",
                AgentRunStepStatus.SKIPPED,
                "SKIP_DUPLICATE_TOOL_CALL",
                "signature=weather|{city=杭州}",
                "跳过重复工具调用",
                Map.of("signature", "weather|{city=杭州}"));
        service.complete(started, AgentRunStatus.SUCCEEDED, "FINAL_ANSWER", "ok");

        assertThat(started).isEqualTo(handle);
        verify(repository).appendStep(
                handle,
                AgentRunStepType.MODEL_ROUND,
                AgentRunStepStatus.SUCCESS,
                1,
                "",
                "request",
                "response",
                Map.of("tool_count", 0));
        verify(repository).appendStep(
                handle,
                AgentRunStepType.TOOL_RESULT,
                AgentRunStepStatus.SUCCESS,
                1,
                "weather",
                "city=杭州",
                "晴",
                Map.of());
        verify(repository).appendStep(
                handle,
                AgentRunStepType.POLICY_DECISION,
                AgentRunStepStatus.SKIPPED,
                1,
                "weather",
                "signature=weather|{city=杭州}",
                "跳过重复工具调用",
                Map.of(
                        "decision_type", "SKIP_DUPLICATE_TOOL_CALL",
                        "signature", "weather|{city=杭州}"));
        verify(repository).completeRun(handle, AgentRunStatus.SUCCEEDED, "FINAL_ANSWER", "ok");
    }

    @Test
    void swallowsRepositoryFailuresAndReturnsNoopHandle() {
        AgentRunRepository repository = mock(AgentRunRepository.class);
        doThrow(new IllegalStateException("db down"))
                .when(repository).createRun("WECHAT", "session", "hello", "context");
        AgentRunTraceService service = new AgentRunTraceService(repository);

        AgentRunHandle handle = service.startWechatRun("session", "hello", "context");
        service.recordToolResult(handle, 1, "weather", AgentRunStepStatus.FAILED, "input", "failed");
        service.complete(handle, AgentRunStatus.FAILED, "TOOL_FAILURE", "failed");

        assertThat(handle.active()).isFalse();
    }
}
