package com.example.spring.wechat.conversation.agent;

import com.example.spring.tool.protocol.function.FunctionCallingMessage;
import com.example.spring.wechat.bot.WechatReply;
import com.example.spring.wechat.image.generation.model.ImageGenerationResult;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AgentLoopStateTests {

    @Test
    void initializesMessagesAndTracksToolProgress() {
        AgentLoopState state = AgentLoopState.start("system prompt", "user prompt", "old history");

        assertThat(state.messages())
                .extracting(FunctionCallingMessage::role)
                .containsExactly("system", "user");
        assertThat(state.rollingHistory()).isEqualTo("old history");
        assertThat(state.stopReason()).isEqualTo(AgentLoopStopReason.NONE);

        state.recordToolResult("weather", "sunny result");

        assertThat(state.previousToolResult()).isEqualTo("sunny result");
        assertThat(state.lastToolFailure()).isEmpty();
        assertThat(state.rollingHistory()).contains("old history", "weather", "sunny result");
    }

    @Test
    void tracksFailuresAndStopReason() {
        AgentLoopState state = AgentLoopState.start("system prompt", "user prompt", "");

        state.recordToolFailure("web_search", "search failed");
        state.stop(AgentLoopStopReason.TOOL_FAILURE);

        assertThat(state.previousToolResult()).isEqualTo("search failed");
        assertThat(state.lastToolFailure()).isEqualTo("search failed");
        assertThat(state.rollingHistory()).contains("web_search", "search failed");
        assertThat(state.stopReason()).isEqualTo(AgentLoopStopReason.TOOL_FAILURE);
    }

    @Test
    void replacesExistingMediaOfSameType() {
        AgentLoopState state = AgentLoopState.start("system prompt", "user prompt", "");

        state.addVisibleParts(List.of(WechatReply.Part.image("first", image("first.png"))));
        state.replaceExistingMediaOfSameType(List.of(WechatReply.Part.image("second", image("second.png"))));
        state.addVisibleParts(List.of(WechatReply.Part.image("second", image("second.png"))));

        assertThat(state.visibleParts()).hasSize(1);
        assertThat(state.visibleParts().get(0).image().fileName()).isEqualTo("second.png");
    }

    @Test
    void keepsRollingHistoryWindowToRecentToolResults() {
        AgentLoopState state = AgentLoopState.start("system prompt", "user prompt", "initial context");

        for (int index = 1; index <= 8; index++) {
            state.recordToolResult("tool-" + index, "result-" + index);
        }

        assertThat(state.rollingHistory()).contains("initial context");
        assertThat(state.rollingHistory()).doesNotContain("tool-1", "result-1", "tool-2", "result-2");
        assertThat(state.rollingHistory()).contains("tool-3", "result-3", "tool-8", "result-8");
    }

    @Test
    void remembersSuccessfulToolResultBySignature() {
        AgentLoopState state = AgentLoopState.start("system prompt", "user prompt", "");

        assertThat(state.successfulToolResult("weather|city=Hangzhou")).isEmpty();

        state.rememberSuccessfulToolResult("weather|city=Hangzhou", "sunny");

        assertThat(state.successfulToolResult("weather|city=Hangzhou")).contains("sunny");
    }

    private static ImageGenerationResult image(String fileName) {
        return new ImageGenerationResult(
                "prompt",
                "https://example.com/" + fileName,
                "IMAGE".getBytes(),
                fileName,
                "image/png",
                512,
                512);
    }
}
