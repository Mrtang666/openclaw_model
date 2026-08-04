package com.example.spring.agent.interrupts;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AgentInterruptService {

    private final Map<String, InterruptSignal> signals = new ConcurrentHashMap<>();
    private final Map<String, Instant> activeRuns = new ConcurrentHashMap<>();

    public void markRunStarted(String sessionKey) {
        String key = normalize(sessionKey);
        if (key.isBlank()) {
            return;
        }
        signals.remove(key);
        activeRuns.put(key, Instant.now());
    }

    public void markRunFinished(String sessionKey) {
        String key = normalize(sessionKey);
        if (key.isBlank()) {
            return;
        }
        activeRuns.remove(key);
        signals.remove(key);
    }

    public boolean requestInterrupt(String sessionKey, String reason) {
        String key = normalize(sessionKey);
        if (key.isBlank() || !activeRuns.containsKey(key)) {
            return false;
        }
        signals.put(key, new InterruptSignal(key, normalize(reason), Instant.now()));
        return true;
    }

    public boolean isInterrupted(String sessionKey) {
        String key = normalize(sessionKey);
        return !key.isBlank() && activeRuns.containsKey(key) && signals.containsKey(key);
    }

    public Optional<InterruptSignal> signal(String sessionKey) {
        String key = normalize(sessionKey);
        if (key.isBlank() || !activeRuns.containsKey(key)) {
            return Optional.empty();
        }
        return Optional.ofNullable(signals.get(key));
    }

    public boolean looksLikeInterrupt(String text) {
        String value = normalize(text).toLowerCase(Locale.ROOT);
        if (value.isBlank()) {
            return false;
        }
        return value.equals("取消")
                || value.equals("停止")
                || value.equals("停")
                || value.equals("cancel")
                || value.equals("stop")
                || value.contains("取消任务")
                || value.contains("停止执行")
                || value.contains("别执行")
                || value.contains("不用执行")
                || value.contains("别发")
                || value.contains("不要发")
                || value.contains("先别")
                || value.contains("中止")
                || value.contains("终止");
    }

    private String normalize(String value) {
        return value == null ? "" : value.strip();
    }

    public record InterruptSignal(String sessionKey, String reason, Instant requestedAt) {
        public InterruptSignal {
            sessionKey = sessionKey == null ? "" : sessionKey.strip();
            reason = reason == null ? "" : reason.strip();
            requestedAt = requestedAt == null ? Instant.now() : requestedAt;
        }
    }
}
