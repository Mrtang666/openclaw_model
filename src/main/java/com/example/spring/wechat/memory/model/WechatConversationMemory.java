package com.example.spring.wechat.memory.model;

import com.example.spring.wechat.voice.style.model.VoiceProfile;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Optional;

/**
 * 当前微信会话的短期上下文与工具状态。
 *
 * <p>最近消息正文来自 {@code conversation_messages}，其余易变化的工具状态保存到
 * {@code conversation_states.state_json}。该对象不负责数据库访问，只负责内存中的状态语义。</p>
 */
public class WechatConversationMemory {

    private final int maxHistoryTurns;
    private final Deque<ConversationTurn> turns = new ArrayDeque<>();
    private String conversationSummary;
    private String lastImagePrompt;
    private String pendingImagePrompt;
    private String lastWeatherCity;
    private String pendingClarificationUserText;
    private String pendingClarificationQuestion;
    private int lastImagePromptTurnCount;
    private List<VoiceProfile> lastDisplayedVoiceCandidates = List.of();
    private String lastVoiceQuery = "";
    private int nextVoiceCandidatePage;
    private VoiceProfile recentVoicePreview;
    private Instant recentVoicePreviewAt;

    private WechatConversationMemory(int maxHistoryTurns) {
        this.maxHistoryTurns = Math.max(1, maxHistoryTurns);
    }

    public static WechatConversationMemory empty(int maxHistoryTurns) {
        return new WechatConversationMemory(maxHistoryTurns);
    }

    /**
     * 创建带有持久化摘要的会话记忆；摘要用于补足未放入最近轮次窗口的历史信息。
     */
    public static WechatConversationMemory empty(int maxHistoryTurns, String conversationSummary) {
        WechatConversationMemory memory = new WechatConversationMemory(maxHistoryTurns);
        memory.conversationSummary(conversationSummary);
        return memory;
    }

    public synchronized void record(String userText, String assistantText) {
        if (isBlank(userText) || isBlank(assistantText)) {
            return;
        }

        turns.addLast(new ConversationTurn(userText.strip(), assistantText.strip()));
        while (turns.size() > maxHistoryTurns) {
            turns.removeFirst();
        }
    }

    public synchronized void replaceTurns(List<ConversationTurn> values) {
        turns.clear();
        if (values == null) {
            return;
        }
        for (ConversationTurn value : values) {
            if (value != null) {
                record(value.userText(), value.assistantText());
            }
        }
    }

    public synchronized void recordImage(String userText, String imagePrompt) {
        record(userText, "已为你生成图片");
        if (!isBlank(imagePrompt)) {
            lastImagePrompt = imagePrompt.strip();
            pendingImagePrompt = null;
            lastImagePromptTurnCount = turns.size();
        }
    }

    public synchronized void recordPendingImagePrompt(String userText, String imagePrompt) {
        record(userText, "已优化图片提示词，等待你确认后生成图片");
        if (!isBlank(imagePrompt)) {
            pendingImagePrompt = imagePrompt.strip();
        }
    }

    public synchronized void recordPendingClarification(String userText, String clarificationQuestion) {
        if (isBlank(userText) || isBlank(clarificationQuestion)) {
            return;
        }
        record(userText, clarificationQuestion);
        pendingClarificationUserText = userText.strip();
        pendingClarificationQuestion = clarificationQuestion.strip();
    }

    public synchronized void recordUserImage(String userText, String imageDescription) {
        record(userText, imageDescription);
        if (!isBlank(imageDescription)) {
            lastImagePrompt = "用户上传图片的识别描述：" + imageDescription.strip();
            lastImagePromptTurnCount = turns.size();
        }
    }

    public synchronized void recordWeatherCity(String city) {
        if (!isBlank(city)) {
            lastWeatherCity = city.strip();
        }
    }

    public synchronized List<ConversationTurn> snapshot() {
        return List.copyOf(turns);
    }

    /**
     * 返回当前会话或上一已关闭会话的压缩摘要，供大模型理解较早的上下文。
     */
    public synchronized Optional<String> conversationSummary() {
        return optionalText(conversationSummary);
    }

    /**
     * 更新从数据库恢复的会话摘要，不把摘要写入短期状态 JSON。
     */
    public synchronized void conversationSummary(String summary) {
        conversationSummary = isBlank(summary) ? null : summary.strip();
    }

    public synchronized List<ConversationTurn> recentTurns(int maxTurns) {
        if (maxTurns <= 0 || turns.isEmpty()) {
            return List.of();
        }
        List<ConversationTurn> values = new ArrayList<>(turns);
        return List.copyOf(values.subList(Math.max(0, values.size() - maxTurns), values.size()));
    }

    public synchronized List<ConversationTurn> recentImageContextTurns(int maxTurns) {
        if (maxTurns <= 0 || turns.isEmpty()) {
            return List.of();
        }
        List<ConversationTurn> values = new ArrayList<>(turns);
        int imageStart = Math.max(0, lastImagePromptTurnCount - 1);
        int recentStart = Math.max(0, values.size() - maxTurns);
        return List.copyOf(values.subList(Math.max(imageStart, recentStart), values.size()));
    }

    public synchronized Optional<String> lastImagePrompt() {
        return optionalText(lastImagePrompt);
    }

    public synchronized Optional<String> lastPendingImagePrompt() {
        return optionalText(pendingImagePrompt);
    }

    public synchronized Optional<String> lastWeatherCity() {
        return optionalText(lastWeatherCity);
    }

    public synchronized Optional<String> pendingClarificationUserText() {
        return optionalText(pendingClarificationUserText);
    }

    public synchronized Optional<String> pendingClarificationQuestion() {
        return optionalText(pendingClarificationQuestion);
    }

    public synchronized void clearPendingImagePrompt() {
        pendingImagePrompt = null;
    }

    public synchronized void clearPendingClarification() {
        pendingClarificationUserText = null;
        pendingClarificationQuestion = null;
    }

    public synchronized boolean lastAssistantInvitedImageRefinement() {
        ConversationTurn latest = turns.peekLast();
        if (latest == null || isBlank(latest.assistantText())) {
            return false;
        }
        String assistant = latest.assistantText();
        boolean invitation = containsAny(assistant,
                "重新生成", "再生成", "新图片", "新图", "告诉我偏好", "告诉我你的偏好", "随时告诉我", "我马上帮你");
        boolean imageTopic = containsAny(assistant, "图片", "图", "画面", "场景", "人物");
        return invitation && imageTopic;
    }

    public synchronized List<VoiceProfile> lastDisplayedVoiceCandidates() {
        return List.copyOf(lastDisplayedVoiceCandidates);
    }

    public synchronized void lastDisplayedVoiceCandidates(List<VoiceProfile> candidates) {
        lastDisplayedVoiceCandidates = candidates == null ? List.of() : List.copyOf(candidates);
    }

    public synchronized String lastVoiceQuery() {
        return lastVoiceQuery;
    }

    public synchronized void lastVoiceQuery(String query) {
        lastVoiceQuery = query == null ? "" : query.strip();
    }

    public synchronized int nextVoiceCandidatePage() {
        return nextVoiceCandidatePage;
    }

    public synchronized void nextVoiceCandidatePage(int page) {
        nextVoiceCandidatePage = Math.max(0, page);
    }

    public synchronized Optional<VoiceProfile> recentVoicePreview() {
        return Optional.ofNullable(recentVoicePreview);
    }

    public synchronized Optional<Instant> recentVoicePreviewAt() {
        return Optional.ofNullable(recentVoicePreviewAt);
    }

    public synchronized void rememberVoicePreview(VoiceProfile voice, Instant previewAt) {
        recentVoicePreview = voice;
        recentVoicePreviewAt = previewAt;
    }

    /**
     * 将可持久化的会话状态导出为简单数据结构，消息正文不放入该 JSON。
     */
    public synchronized State state() {
        return new State(
                lastImagePrompt,
                pendingImagePrompt,
                lastWeatherCity,
                pendingClarificationUserText,
                pendingClarificationQuestion,
                lastImagePromptTurnCount);
    }

    /**
     * 从数据库中的状态 JSON 恢复工具状态。
     */
    public synchronized void applyState(State state) {
        if (state == null) {
            return;
        }
        lastImagePrompt = state.lastImagePrompt();
        pendingImagePrompt = state.pendingImagePrompt();
        lastWeatherCity = state.lastWeatherCity();
        pendingClarificationUserText = state.pendingClarificationUserText();
        pendingClarificationQuestion = state.pendingClarificationQuestion();
        lastImagePromptTurnCount = Math.max(0, state.lastImagePromptTurnCount());
    }

    /**
     * 存储在 conversation_states.state_json 中的状态字段。
     */
    public record State(
            String lastImagePrompt,
            String pendingImagePrompt,
            String lastWeatherCity,
            String pendingClarificationUserText,
            String pendingClarificationQuestion,
            int lastImagePromptTurnCount) {
    }

    private Optional<String> optionalText(String value) {
        return isBlank(value) ? Optional.empty() : Optional.of(value);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private boolean containsAny(String value, String... candidates) {
        for (String candidate : candidates) {
            if (value.contains(candidate)) {
                return true;
            }
        }
        return false;
    }
}
