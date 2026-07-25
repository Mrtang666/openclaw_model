package com.example.spring.wechat.taxi.model;

import java.time.Instant;
import java.util.List;

public record RideQuote(
        String quoteId,
        String sessionKey,
        String originName,
        String originLocation,
        String destinationName,
        String destinationLocation,
        String estimateTraceId,
        List<RideQuoteOption> options,
        Instant expiresAt) {

    public RideQuote {
        quoteId = clean(quoteId);
        sessionKey = clean(sessionKey);
        originName = clean(originName);
        originLocation = clean(originLocation);
        destinationName = clean(destinationName);
        destinationLocation = clean(destinationLocation);
        estimateTraceId = clean(estimateTraceId);
        options = options == null ? List.of() : List.copyOf(options);
    }

    public boolean expired(Instant now) {
        return expiresAt != null && now != null && expiresAt.isBefore(now);
    }

    private static String clean(String value) {
        return value == null ? "" : value.strip();
    }
}
