package com.example.spring.speech;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class PendingVoiceModeOfferService {
    private static final Duration EXPIRY = Duration.ofMinutes(10);
    private static final Set<String> ACCEPT = Set.of(
        "需要", "需要开启", "要", "是", "开启", "打开", "可以", "好", "好的", "确认",
        "开启语音", "打开语音模式");
    private static final Set<String> DECLINE = Set.of(
        "不需要", "不要", "不用", "不开启", "算了", "否", "暂时不用", "保持文字");

    private final Map<String, Instant> offers = new HashMap<>();

    public synchronized void offer(String userId) {
        offers.put(userId, Instant.now().plus(EXPIRY));
    }

    public synchronized void cancel(String userId) {
        offers.remove(userId);
    }

    public synchronized OfferDecision consume(String userId, String text) {
        Instant expiry = offers.get(userId);
        if (expiry == null) {
            return OfferDecision.NONE;
        }
        if (Instant.now().isAfter(expiry)) {
            offers.remove(userId);
            return OfferDecision.NONE;
        }
        String normalized = normalize(text);
        if (ACCEPT.contains(normalized)) {
            offers.remove(userId);
            return OfferDecision.ACCEPT;
        }
        if (normalized.matches("^(可以|好|好的)?(开启|打开)(语音|语音模式|语音对话)?$")) {
            offers.remove(userId);
            return OfferDecision.ACCEPT;
        }
        if (normalized.matches("^(需要|要)(开启|打开)(语音|语音模式|语音对话)?$")) {
            offers.remove(userId);
            return OfferDecision.ACCEPT;
        }
        if (DECLINE.contains(normalized)) {
            offers.remove(userId);
            return OfferDecision.DECLINE;
        }
        offers.remove(userId);
        return OfferDecision.NEW_REQUEST;
    }

    public synchronized boolean hasPendingOffer(String userId) {
        Instant expiry = offers.get(userId);
        if (expiry == null) {
            return false;
        }
        if (Instant.now().isAfter(expiry)) {
            offers.remove(userId);
            return false;
        }
        return true;
    }

    public enum OfferDecision {
        NONE,
        ACCEPT,
        DECLINE,
        NEW_REQUEST
    }

    private static String normalize(String text) {
        return text == null ? "" : text.trim()
            .replaceAll("[，。！？,.!?\\s]", "")
            .replaceAll("[了吧啦呀啊呢]+$", "")
            .toLowerCase(Locale.ROOT);
    }
}
