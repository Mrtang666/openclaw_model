package com.example.spring.cli;

import com.example.spring.agent.AgentService;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

class ConsoleRunnerTests {

    @Test
    void readsCommandsUntilExit() {
        AgentService agentService = mock(AgentService.class);
        doAnswer(invocation -> {
            com.example.spring.agent.ReplyEmitter emitter = invocation.getArgument(1);
            emitter.emit("状态：");
            emitter.emit("RUNNING");
            return null;
        }).when(agentService).handleStreaming(eq("/status"), any());
        ByteArrayInputStream input = new ByteArrayInputStream(
                "/status\nexit\n".getBytes(StandardCharsets.UTF_8));
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ConsoleRunner runner = new ConsoleRunner(
                agentService, input, new PrintStream(output, true, StandardCharsets.UTF_8));

        runner.run();

        assertThat(output.toString(StandardCharsets.UTF_8))
                .contains("OpenClaw CLI 已启动")
                .contains("状态：RUNNING")
                .contains("程序已退出");
    }
}
