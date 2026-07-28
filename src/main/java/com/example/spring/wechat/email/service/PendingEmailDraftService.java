package com.example.spring.wechat.email.service;

import com.example.spring.wechat.email.model.EmailMessage;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

@Service
public class PendingEmailDraftService {

    private final Clock clock;
    private final Supplier<String> tokenSupplier;
    private final Map<String, PendingEmailDraft> drafts = new ConcurrentHashMap<>();

    public PendingEmailDraftService() {
        this(Clock.systemUTC(), () -> UUID.randomUUID().toString());
    }

    public PendingEmailDraftService(Clock clock, Supplier<String> tokenSupplier) {
        this.clock = clock == null ? Clock.systemUTC() : clock;
        this.tokenSupplier = tokenSupplier == null ? () -> UUID.randomUUID().toString() : tokenSupplier;
    }

    public PendingEmailDraft create(String sessionKey, EmailMessage message, int ttlMinutes) {
        String token = tokenSupplier.get();
        PendingEmailDraft draft = new PendingEmailDraft(
                token,
                safe(sessionKey),
                message,
                clock.instant().plusSeconds(Math.max(1, ttlMinutes) * 60L));
        drafts.put(token, draft);
        return draft;
    }

    public DraftLookupResult consume(String sessionKey, String token) {
        String normalizedToken = safe(token);
        PendingEmailDraft draft = drafts.get(normalizedToken);
        if (draft == null) {
            return DraftLookupResult.notFound();
        }
        if (!draft.sessionKey().equals(safe(sessionKey))) {
            return DraftLookupResult.wrongSession();
        }
        drafts.remove(normalizedToken);
        if (draft.expiresAt().isBefore(clock.instant()) || draft.expiresAt().equals(clock.instant())) {
            return DraftLookupResult.expired();
        }
        return DraftLookupResult.found(draft);
    }

    private static String safe(String value) {
        return value == null ? "" : value.strip();
    }

    public record PendingEmailDraft(
            String token,
            String sessionKey,
            EmailMessage message,
            Instant expiresAt) {
    }

    public record DraftLookupResult(
            Status status,
            Optional<PendingEmailDraft> draft) {

        public static DraftLookupResult found(PendingEmailDraft draft) {
            return new DraftLookupResult(Status.FOUND, Optional.of(draft));
        }

        public static DraftLookupResult notFound() {
            return new DraftLookupResult(Status.NOT_FOUND, Optional.empty());
        }

        public static DraftLookupResult wrongSession() {
            return new DraftLookupResult(Status.WRONG_SESSION, Optional.empty());
        }

        public static DraftLookupResult expired() {
            return new DraftLookupResult(Status.EXPIRED, Optional.empty());
        }

        public enum Status {
            FOUND,
            NOT_FOUND,
            WRONG_SESSION,
            EXPIRED
        }
    }
}
