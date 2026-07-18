package com.example.spring.task;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.spring.agent.AgentPlan;
import com.example.spring.agent.AgentRequest;
import com.example.spring.agent.AgentType;
import com.example.spring.memory.MemoryProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ImageTaskOrchestratorTests {
    @TempDir
    Path tempDirectory;

    @Test
    void collectsRequirementsThenExecutesWithTextAgentPrompt() throws Exception {
        ImageTaskSessionService sessions = sessions();
        AtomicInteger calls = new AtomicInteger();
        ImageTaskPlanner planner = (userId, message, existing, active, hasImage, history) -> {
            if (calls.getAndIncrement() == 0) {
                return new ImageTaskPlanningResult(
                    true,
                    false,
                    "这张图用于什么场景，希望什么风格？",
                    "",
                    new ImageTaskBrief("智能水杯", "", "", "", "", "", "", "", "", "GENERATE"));
            }
            assertThat(active).isTrue();
            assertThat(existing.subject()).isEqualTo("智能水杯");
            return new ImageTaskPlanningResult(
                true,
                true,
                "",
                "生成方形科技感智能水杯宣传图，使用蓝白配色。",
                new ImageTaskBrief("", "宣传图", "科技感", "", "", "蓝白配色", "方形", "", "", "GENERATE"));
        };
        ImageTaskOrchestrator orchestrator = new ImageTaskOrchestrator(sessions, planner);

        ImageTaskDecision first = orchestrator.decide(
            request(1L, "帮我生成一张智能水杯图片"),
            new AgentPlan(List.of(AgentType.IMAGE_GENERATION)));
        assertThat(first.action()).isEqualTo(TaskDecisionAction.REPLY);
        assertThat(first.reply()).contains("什么场景");
        assertThat(sessions.loadActive("user-1")).isPresent();

        ImageTaskDecision second = orchestrator.decide(
            request(2L, "用作宣传图，科技感，蓝白配色，方形"),
            new AgentPlan(List.of(AgentType.CHAT)));
        assertThat(second.action()).isEqualTo(TaskDecisionAction.EXECUTE);
        assertThat(second.request().text()).contains("方形科技感智能水杯宣传图");
        assertThat(second.plan().steps()).containsExactly(AgentType.IMAGE_GENERATION);

        orchestrator.complete("user-1");
        assertThat(sessions.loadActive("user-1")).isEmpty();
    }

    @Test
    void cancelsActiveTaskAndLetsWeatherPassThrough() throws Exception {
        ImageTaskSessionService sessions = sessions();
        sessions.save("user-1", TaskStatus.COLLECTING_REQUIREMENTS, ImageTaskBrief.empty());
        ImageTaskPlanner planner = (userId, message, existing, active, hasImage, history) ->
            new ImageTaskPlanningResult(true, false, "继续补充", "", existing);
        ImageTaskOrchestrator orchestrator = new ImageTaskOrchestrator(sessions, planner);

        ImageTaskDecision weather = orchestrator.decide(
            request(3L, "查询无锡天气"),
            new AgentPlan(List.of(AgentType.WEATHER)));
        assertThat(weather.action()).isEqualTo(TaskDecisionAction.PASS_THROUGH);
        assertThat(sessions.loadActive("user-1")).isPresent();

        ImageTaskDecision cancelled = orchestrator.decide(
            request(4L, "取消"),
            new AgentPlan(List.of(AgentType.CHAT)));
        assertThat(cancelled.action()).isEqualTo(TaskDecisionAction.REPLY);
        assertThat(cancelled.reply()).contains("已取消");
        assertThat(sessions.loadActive("user-1")).isEmpty();
    }

    @Test
    void usesRecentImageHistoryToUnderstandAnImplicitEditRequest() throws Exception {
        ImageTaskSessionService sessions = sessions();
        ImageTaskPlanner planner = (userId, message, existing, active, hasImage, history) -> {
            assertThat(active).isFalse();
            assertThat(hasImage).isTrue();
            assertThat(history).extracting(com.example.spring.memory.MemoryMessage::content)
                .anyMatch(content -> content.startsWith("[图片："));
            return new ImageTaskPlanningResult(
                true,
                true,
                "",
                "编辑参考图片，移除画面中的全部人群，保持建筑和光影不变。",
                new ImageTaskBrief("", "", "", "", "", "", "", "", "移除人群", "EDIT"));
        };
        ImageTaskOrchestrator orchestrator = new ImageTaskOrchestrator(sessions, planner);
        AgentRequest request = new AgentRequest(
            "user-1",
            5L,
            "去除里面的人群",
            List.of(),
            0,
            List.of(
                new com.example.spring.memory.MemoryMessage("assistant", "图片已生成。"),
                new com.example.spring.memory.MemoryMessage(
                    "assistant", "[图片：卢浮宫广场，画面中有人群]")),
            List.of(new com.example.spring.agent.ImageAsset(
                new byte[] {1}, "image/png", "louvre.png")));

        assertThat(orchestrator.hasRecentImageContext(request)).isTrue();
        ImageTaskDecision decision = orchestrator.decide(
            request, new AgentPlan(List.of(AgentType.CHAT)));

        assertThat(decision.action()).isEqualTo(TaskDecisionAction.EXECUTE);
        assertThat(decision.request().text()).contains("移除画面中的全部人群");
        assertThat(decision.plan().steps()).containsExactly(AgentType.IMAGE_GENERATION);
    }

    @Test
    void keepsOrdinaryConversationAfterAnImageWhenPlannerReturnsOther() throws Exception {
        ImageTaskSessionService sessions = sessions();
        ImageTaskPlanner planner = (userId, message, existing, active, hasImage, history) ->
            new ImageTaskPlanningResult(false, false, "", "", existing);
        ImageTaskOrchestrator orchestrator = new ImageTaskOrchestrator(sessions, planner);
        AgentRequest request = new AgentRequest(
            "user-1",
            6L,
            "谢谢，效果很好",
            List.of(),
            0,
            List.of(new com.example.spring.memory.MemoryMessage(
                "assistant", "[图片：卢浮宫广场]")),
            List.of(new com.example.spring.agent.ImageAsset(
                new byte[] {1}, "image/png", "louvre.png")));

        ImageTaskDecision decision = orchestrator.decide(
            request, new AgentPlan(List.of(AgentType.CHAT)));

        assertThat(decision.action()).isEqualTo(TaskDecisionAction.PASS_THROUGH);
        assertThat(sessions.loadActive("user-1")).isEmpty();
    }

    private AgentRequest request(long messageId, String text) {
        return new AgentRequest("user-1", messageId, text, List.of(), 0);
    }

    private ImageTaskSessionService sessions() throws Exception {
        MemoryProperties properties = new MemoryProperties();
        properties.setDataDirectory(tempDirectory);
        ImageTaskSessionService service = new ImageTaskSessionService(
            properties, new ObjectMapper());
        service.afterPropertiesSet();
        return service;
    }
}
