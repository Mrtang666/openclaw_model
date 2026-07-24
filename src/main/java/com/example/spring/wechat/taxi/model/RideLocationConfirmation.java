package com.example.spring.wechat.taxi.model;

import java.time.Instant;

public record RideLocationConfirmation(String confirmationId, String sessionKey, String city,
                                       String originName, String originAddress, String originLocation,
                                       String destinationName, String destinationAddress, String destinationLocation,
                                       Instant expiresAt) {
    public boolean expired() { return expiresAt == null || expiresAt.isBefore(Instant.now()); }
}
