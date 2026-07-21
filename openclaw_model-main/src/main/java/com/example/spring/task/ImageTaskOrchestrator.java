package com.example.spring.task;

import com.example.spring.agent.AgentPlan;
import com.example.spring.agent.AgentRequest;
import com.example.spring.agent.AgentType;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class ImageTaskOrchestrator {
    private static final Logger log = LoggerFactory.getLogger(ImageTaskOrchestrator.class);
    private static final Pattern CANCEL = Pattern.compile(
        "(?i)^\\s*(取消|算了|不做了|停止|结束任务|重新开始|cancel|stop)\\s*[。.!！]?$" );
    private static final Pattern VAGUE_IMAGE_REQUEST = Pattern.compile(
        "(?i)^\\s*(帮我)?(画|画一张|画个|生成|生成一张|做一张)(图片|图|图像)?[。.!！]?$" );

    private final ImageTaskSessionService sessionService;
    private final ImageTaskPlanner planner;

    public ImageTaskOrchestrator(
        ImageTaskSessionService sessionService,
        ImageTaskPlanner planner) {
        this.sessionService = sessionService;
        this.planner = planner;
    }

    public boolean hasActiveSession(String userId) {
        try {
            return sessionService.loadActive(userId).isPresent();
        } catch (RuntimeException exception) {
            log.warn("检查图片任务状态失败，userId={}", userId, exception);
            return false;
        }
    }

    public boolean requiresSourceImage(String userId) {
        try {
            return sessionService.loadActive(userId)
                .map(ImageTaskSession::brief)
                .map(ImageTaskBrief::sourceMode)
                .filter("EDIT"::equalsIgnoreCase)
                .isPresent();
        } catch (RuntimeException exception) {
            log.warn("检查图片任务参考图状态失败，userId={}", userId, exception);
            return false;
        }
    }

    public boolean hasRecentImageContext(AgentRequest request) {
        if (request == null || request.history().isEmpty()) {
            return false;
        }
        int start = Math.max(0, request.history().size() - 4);
        return request.history().subList(start, request.history().size()).stream()
            .map(com.example.spring.memory.MemoryMessage::content)
            .anyMatch(content -> content != null && content.startsWith("[图片："));
    }

    public ImageTaskDecision decide(AgentRequest request, AgentPlan routedPlan) {
        Optional<ImageTaskSession> active = sessionService.loadActive(request.userId());
        if (active.isPresent() && CANCEL.matcher(request.text()).matches()) {
            sessionService.cancel(request.userId());
            return ImageTaskDecision.reply(
                "当前图片任务已取消。你可以直接告诉我新的需求。", request, routedPlan);
        }

        if (routedPlan.primaryType() == AgentType.DOCUMENT) {
            return ImageTaskDecision.passThrough(request, routedPlan);
        }

        boolean imageRequested = routedPlan.steps().contains(AgentType.IMAGE_GENERATION);
        boolean recentImageContext = hasRecentImageContext(request);
        if (active.isEmpty() && !imageRequested && !recentImageContext) {
            return ImageTaskDecision.passThrough(request, routedPlan);
        }
        if (active.isPresent() && (routedPlan.primaryType() == AgentType.WEATHER
            || routedPlan.primaryType() == AgentType.DOCUMENT)) {
            return ImageTaskDecision.passThrough(request, routedPlan);
        }

        ImageTaskBrief existing = active.map(ImageTaskSession::brief)
            .orElse(ImageTaskBrief.empty());
        try {
            ImageTaskPlanningResult planning = planner.plan(
                request.userId(),
                request.text(),
                existing,
                active.isPresent(),
                request.hasImages() || request.hasReferencedImages(),
                request.history());
            if (!planning.imageTask() && !imageRequested) {
                return ImageTaskDecision.passThrough(request, routedPlan);
            }
            ImageTaskBrief brief = existing.merge(planning.brief());
            if (!planning.ready()) {
                sessionService.save(
                    request.userId(), TaskStatus.COLLECTING_REQUIREMENTS, brief);
                String question = planning.question().isBlank()
                    ? defaultQuestion() : planning.question();
                return ImageTaskDecision.reply(question, request, routedPlan);
            }

            String finalPrompt = planning.finalPrompt().isBlank()
                ? brief.toGenerationPrompt(request.text()) : planning.finalPrompt();
            finalPrompt = limit(finalPrompt, 4_000);
            sessionService.save(request.userId(), TaskStatus.READY_TO_EXECUTE, brief);
            AgentPlan executionPlan = request.hasImages()
                ? new AgentPlan(List.of(AgentType.VISION, AgentType.IMAGE_GENERATION))
                : new AgentPlan(List.of(AgentType.IMAGE_GENERATION));
            return ImageTaskDecision.execute(request.withText(finalPrompt), executionPlan);
        } catch (Exception exception) {
            log.warn("文本 Agent 图片任务规划失败，将使用规则降级，userId={}",
                request.userId(), exception);
            return fallback(
                request, routedPlan, active, existing, imageRequested, recentImageContext);
        }
    }

    public void complete(String userId) {
        sessionService.complete(userId);
    }

    private ImageTaskDecision fallback(
        AgentRequest request,
        AgentPlan routedPlan,
        Optional<ImageTaskSession> active,
        ImageTaskBrief existing,
        boolean imageRequested,
        boolean recentImageContext) {
        if (active.isEmpty() && imageRequested
            && VAGUE_IMAGE_REQUEST.matcher(request.text()).matches()) {
            sessionService.save(
                request.userId(), TaskStatus.COLLECTING_REQUIREMENTS, existing);
            return ImageTaskDecision.reply(defaultQuestion(), request, routedPlan);
        }
        if (imageRequested) {
            ImageTaskBrief fallbackBrief = existing.merge(new ImageTaskBrief(
                request.text(), "", "", "", "", "", "", "", "",
                request.hasImages() || request.hasReferencedImages() ? "EDIT" : "GENERATE"));
            sessionService.save(
                request.userId(), TaskStatus.READY_TO_EXECUTE, fallbackBrief);
            return ImageTaskDecision.execute(
                request.withText(fallbackBrief.toGenerationPrompt(request.text())), routedPlan);
        }
        if (active.isEmpty() && recentImageContext) {
            return ImageTaskDecision.passThrough(request, routedPlan);
        }
        return ImageTaskDecision.reply(
            "我还在整理刚才的图片需求。请补充图片主体、用途或希望的风格；也可以回复“取消”结束任务。",
            request,
            routedPlan);
    }

    private static String defaultQuestion() {
        return "可以，请告诉我图片的主体、用途和希望的风格。已经确定的内容可以只补充其中一项。";
    }

    private static String limit(String value, int maxLength) {
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
