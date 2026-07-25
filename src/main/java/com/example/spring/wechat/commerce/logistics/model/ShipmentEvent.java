package com.example.spring.wechat.commerce.logistics.model;

import java.time.Instant;

public record ShipmentEvent(
        Instant occurredAt,
        String location,
        String description) {

    public ShipmentEvent {
        location = location == null ? "" : location.strip();
        description = description == null ? "" : description.strip();
    }
}
