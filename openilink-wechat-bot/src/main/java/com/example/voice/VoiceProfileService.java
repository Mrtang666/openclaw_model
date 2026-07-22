package com.example.voice;

import com.example.LocalLLMService;
import com.example.intent.BotIntent;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/** Stores per-user TTS voice selection and handles the interactive menu. */
public class VoiceProfileService {

    private static final Pattern SELECTION_REQUEST = Pattern.compile(
            ".*(切换|更换|选择|换一个|设置).*(音色|声音|声线).*|.*(音色|声线).*");

    private final Map<String, VoiceProfile> profiles = new LinkedHashMap<>();
    private final Map<String, Boolean> awaitingSelection = new ConcurrentHashMap<>();
    private final Map<String, String> userVoices = new ConcurrentHashMap<>();
    private final String model;
    private final String defaultVoice;

    public VoiceProfileService() {
        LocalLLMService.Config cfg = LocalLLMService.getConfig();
        this.model = cfg.getTtsModel();
        this.defaultVoice = cfg.getTtsVoice();
        profiles.put("1", new VoiceProfile("1", "温柔自然", model + ":alex", "适合日常聊天"));
        profiles.put("2", new VoiceProfile("2", "沉稳男声", model + ":david", "语气稳重清晰"));
        profiles.put("3", new VoiceProfile("3", "温柔女声", model + ":anna", "语气柔和亲切"));
        profiles.put("4", new VoiceProfile("4", "活泼明快", model + ":bella", "语气轻快有活力"));
        profiles.put("5", new VoiceProfile("5", "播音主持", model + ":michael", "发音清晰正式"));
    }

    public SelectionResult handle(String userId, String text) {
        String value = text == null ? "" : text.trim();
        if (awaitingSelection.containsKey(userId)) {
            VoiceProfile profile = findProfile(value);
            if (profile == null) {
                return SelectionResult.ask(buildMenu());
            }
            awaitingSelection.remove(userId);
            userVoices.put(userId, profile.voice);
            return SelectionResult.selected("已切换为“" + profile.label + "”音色。" + profile.description + "。", profile.voice);
        }

        if (value.isBlank() || !SELECTION_REQUEST.matcher(value).matches()) {
            return SelectionResult.notHandled();
        }
        awaitingSelection.put(userId, true);
        return SelectionResult.ask(buildMenu());
    }

    public boolean isAwaitingSelection(String userId) {
        return userId != null && awaitingSelection.containsKey(userId);
    }

    public boolean shouldContinueSelection(String userId, BotIntent intent) {
        return isAwaitingSelection(userId) && intent == BotIntent.CHAT;
    }

    public void cancelSelection(String userId) {
        if (userId != null) awaitingSelection.remove(userId);
    }

    public String getVoice(String userId) {
        return userVoices.getOrDefault(userId, defaultVoice);
    }

    public String buildMenu() {
        StringBuilder menu = new StringBuilder("可以，请选择你喜欢的音色，回复编号或名称：\n");
        for (VoiceProfile profile : profiles.values()) {
            menu.append(profile.number).append(". ").append(profile.label)
                    .append("：").append(profile.description).append("\n");
        }
        return menu.toString().trim();
    }

    private VoiceProfile findProfile(String input) {
        String value = input.toLowerCase(Locale.ROOT).replaceAll("[\\s，。！？、,.?；;：:]+", "");
        for (VoiceProfile profile : profiles.values()) {
            if (profile.number.equals(value) || value.contains(profile.label.toLowerCase(Locale.ROOT))) {
                return profile;
            }
        }
        if (value.contains("温柔") || value.contains("自然")) return profiles.get("1");
        if (value.contains("沉稳") || value.contains("男声")) return profiles.get("2");
        if (value.contains("女声")) return profiles.get("3");
        if (value.contains("活泼") || value.contains("明快")) return profiles.get("4");
        if (value.contains("播音") || value.contains("主持")) return profiles.get("5");
        return null;
    }

    private static class VoiceProfile {
        private final String number;
        private final String label;
        private final String voice;
        private final String description;

        private VoiceProfile(String number, String label, String voice, String description) {
            this.number = number;
            this.label = label;
            this.voice = voice;
            this.description = description;
        }
    }

    public enum Action { NONE, ASK, SELECTED }

    public static class SelectionResult {
        private final Action action;
        private final String message;
        private final String voice;

        private SelectionResult(Action action, String message, String voice) {
            this.action = action;
            this.message = message;
            this.voice = voice;
        }

        public static SelectionResult notHandled() { return new SelectionResult(Action.NONE, "", null); }
        public static SelectionResult ask(String message) { return new SelectionResult(Action.ASK, message, null); }
        public static SelectionResult selected(String message, String voice) { return new SelectionResult(Action.SELECTED, message, voice); }

        public Action getAction() { return action; }
        public String getMessage() { return message; }
        public String getVoice() { return voice; }
    }
}
