package com.example.spring.wechat.voice.style.service;

import com.example.spring.wechat.memory.service.WechatMemoryService;
import com.example.spring.wechat.voice.style.model.VoiceCandidatePage;
import com.example.spring.wechat.voice.style.model.VoiceProfile;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 微信用户音色偏好内存存储。
 * 当前项目未接数据库，所以偏好只在程序运行期间有效；后续持久化时可替换为数据库实现。
 */
@Service
public class VoicePreferenceService {

    public static final String DEFAULT_VOICE = "Cherry";
    private static final int PAGE_SIZE = 5;
    private static final Duration RECENT_PREVIEW_TTL = Duration.ofMinutes(5);

    private final VoiceCatalog catalog;
    private final Clock clock;
    private final WechatMemoryService wechatMemoryService;
    private final ObjectMapper objectMapper;
    private final ConcurrentMap<String, VoiceSessionState> states = new ConcurrentHashMap<>();

    @Autowired
    public VoicePreferenceService(
            VoiceCatalog catalog,
            ObjectProvider<WechatMemoryService> wechatMemoryServiceProvider,
            ObjectProvider<ObjectMapper> objectMapperProvider) {
        this(
                catalog,
                Clock.systemDefaultZone(),
                wechatMemoryServiceProvider.getIfAvailable(),
                objectMapperProvider.getIfAvailable());
    }

    public VoicePreferenceService(VoiceCatalog catalog) {
        this(catalog, Clock.systemDefaultZone(), null, null);
    }

    public VoicePreferenceService(VoiceCatalog catalog, Clock clock) {
        this(catalog, clock, null, null);
    }

    private VoicePreferenceService(
            VoiceCatalog catalog,
            Clock clock,
            WechatMemoryService wechatMemoryService,
            ObjectMapper objectMapper) {
        this.catalog = catalog;
        this.clock = clock == null ? Clock.systemDefaultZone() : clock;
        this.wechatMemoryService = wechatMemoryService;
        this.objectMapper = objectMapper;
    }

    public VoiceCatalog catalog() {
        return catalog;
    }

    public String effectiveVoice(String sessionKey) {
        return currentPreference(sessionKey)
                .map(VoiceProfile::voice)
                .orElse(DEFAULT_VOICE);
    }

    public Optional<VoiceProfile> currentPreference(String sessionKey) {
        Optional<VoiceProfile> persistedPreference = loadPersistedPreference(sessionKey);
        if (persistedPreference.isPresent()) {
            return persistedPreference;
        }
        VoiceSessionState state = states.get(safeKey(sessionKey));
        return state == null ? Optional.empty() : Optional.ofNullable(state.selectedVoice());
    }

    public void savePreference(String sessionKey, VoiceProfile profile) {
        if (profile == null || profile.voice().isBlank()) {
            return;
        }
        stateFor(sessionKey).selectedVoice(profile);
        savePersistedPreference(sessionKey, profile);
    }

    public VoiceCandidatePage searchAndRememberCandidates(String sessionKey, String query) {
        return searchAndRememberCandidates(sessionKey, query, 0);
    }

    public VoiceCandidatePage nextCandidatePage(String sessionKey) {
        VoiceSessionState state = stateFor(sessionKey);
        return pageAndRemember(sessionKey, state.lastQuery(), state.nextPage());
    }

    public VoiceCandidatePage searchAndRememberCandidates(String sessionKey, String query, int page) {
        return pageAndRemember(sessionKey, query, page);
    }

    public Optional<VoiceProfile> candidateByOrdinal(String sessionKey, int ordinal) {
        if (ordinal <= 0) {
            return Optional.empty();
        }
        VoiceSessionState state = states.get(safeKey(sessionKey));
        if (state == null || ordinal > state.lastDisplayedCandidates().size()) {
            return Optional.empty();
        }
        return Optional.of(state.lastDisplayedCandidates().get(ordinal - 1));
    }

    public boolean hasDisplayedCandidates(String sessionKey) {
        VoiceSessionState state = states.get(safeKey(sessionKey));
        return state != null && !state.lastDisplayedCandidates().isEmpty();
    }

    public Optional<String> lastQuery(String sessionKey) {
        VoiceSessionState state = states.get(safeKey(sessionKey));
        if (state == null || state.lastQuery().isBlank()) {
            return Optional.empty();
        }
        return Optional.of(state.lastQuery());
    }

    public Optional<VoiceProfile> findByVoice(String voice) {
        return catalog.findByVoice(voice);
    }

    public void rememberPreview(String sessionKey, VoiceProfile profile) {
        if (profile == null) {
            return;
        }
        VoiceSessionState state = stateFor(sessionKey);
        state.recentPreview(profile);
        state.recentPreviewAt(Instant.now(clock));
    }

    public Optional<VoiceProfile> recentPreview(String sessionKey) {
        VoiceSessionState state = states.get(safeKey(sessionKey));
        if (state == null || state.recentPreview() == null || state.recentPreviewAt() == null) {
            return Optional.empty();
        }
        Duration age = Duration.between(state.recentPreviewAt(), Instant.now(clock));
        if (age.compareTo(RECENT_PREVIEW_TTL) > 0) {
            return Optional.empty();
        }
        return Optional.of(state.recentPreview());
    }

    private VoiceCandidatePage pageAndRemember(String sessionKey, String query, int page) {
        List<VoiceProfile> matches = catalog.search(query);
        if (matches.isEmpty()) {
            matches = catalog.all();
        }
        int safePage = Math.max(0, page);
        int from = Math.min(matches.size(), safePage * PAGE_SIZE);
        int to = Math.min(matches.size(), from + PAGE_SIZE);
        List<VoiceProfile> candidates = matches.subList(from, to);
        boolean hasMore = to < matches.size();

        VoiceSessionState state = stateFor(sessionKey);
        state.lastQuery(query);
        state.nextPage(hasMore ? safePage + 1 : 0);
        state.lastDisplayedCandidates(candidates);
        return new VoiceCandidatePage(candidates, safePage, hasMore, query);
    }

    private VoiceSessionState stateFor(String sessionKey) {
        return states.computeIfAbsent(safeKey(sessionKey), ignored -> new VoiceSessionState());
    }

    private String safeKey(String sessionKey) {
        return sessionKey == null || sessionKey.isBlank() ? "default" : sessionKey.strip();
    }

    private Optional<VoiceProfile> loadPersistedPreference(String sessionKey) {
        if (wechatMemoryService == null || objectMapper == null || "default".equals(safeKey(sessionKey))) {
            return Optional.empty();
        }
        try {
            return wechatMemoryService.explicitPreference(sessionKey, "voice")
                    .flatMap(value -> {
                        try {
                            return Optional.of(objectMapper.readValue(value, VoiceProfile.class));
                        } catch (JsonProcessingException exception) {
                            return Optional.empty();
                        }
                    });
        } catch (RuntimeException exception) {
            return Optional.empty();
        }
    }

    private void savePersistedPreference(String sessionKey, VoiceProfile profile) {
        if (wechatMemoryService == null || objectMapper == null || "default".equals(safeKey(sessionKey))) {
            return;
        }
        try {
            wechatMemoryService.saveExplicitPreference(
                    sessionKey,
                    "voice",
                    objectMapper.writeValueAsString(profile),
                    "EXPLICIT_ACTION",
                    Instant.now(clock));
        } catch (JsonProcessingException ignored) {
            // 音色偏好持久化失败时仍保留当前进程内选择。
        }
    }

    private static final class VoiceSessionState {

        private VoiceProfile selectedVoice;
        private List<VoiceProfile> lastDisplayedCandidates = List.of();
        private String lastQuery = "";
        private int nextPage;
        private VoiceProfile recentPreview;
        private Instant recentPreviewAt;

        VoiceProfile selectedVoice() {
            return selectedVoice;
        }

        void selectedVoice(VoiceProfile selectedVoice) {
            this.selectedVoice = selectedVoice;
        }

        List<VoiceProfile> lastDisplayedCandidates() {
            return lastDisplayedCandidates;
        }

        void lastDisplayedCandidates(List<VoiceProfile> lastDisplayedCandidates) {
            this.lastDisplayedCandidates = lastDisplayedCandidates == null ? List.of() : List.copyOf(lastDisplayedCandidates);
        }

        String lastQuery() {
            return lastQuery;
        }

        void lastQuery(String lastQuery) {
            this.lastQuery = lastQuery == null ? "" : lastQuery.strip();
        }

        int nextPage() {
            return nextPage;
        }

        void nextPage(int nextPage) {
            this.nextPage = Math.max(0, nextPage);
        }

        VoiceProfile recentPreview() {
            return recentPreview;
        }

        void recentPreview(VoiceProfile recentPreview) {
            this.recentPreview = recentPreview;
        }

        Instant recentPreviewAt() {
            return recentPreviewAt;
        }

        void recentPreviewAt(Instant recentPreviewAt) {
            this.recentPreviewAt = recentPreviewAt;
        }
    }
}
