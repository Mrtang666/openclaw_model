package com.example.spring.wechat.commerce.logistics.model;

import java.time.Instant;
import java.util.List;

public record ShipmentTrace(
        String trackingNoMasked,
        Carrier carrier,
        ShipmentStatus status,
        String currentLocation,
        String estimatedDelivery,
        List<ShipmentEvent> events,
        Instant queriedAt) {

    public ShipmentTrace {
        trackingNoMasked = trackingNoMasked == null ? "" : trackingNoMasked.strip();
        carrier = carrier == null ? Carrier.OTHER : carrier;
        status = status == null ? ShipmentStatus.UNKNOWN : status;
        currentLocation = currentLocation == null ? "" : currentLocation.strip();
        estimatedDelivery = estimatedDelivery == null ? "" : estimatedDelivery.strip();
        events = events == null ? List.of() : List.copyOf(events);
        queriedAt = queriedAt == null ? Instant.now() : queriedAt;
    }
}
