package com.example.spring.wechat.map.model;

import java.util.List;

public record MapRouteOption(
        MapTransportMode mode,
        Integer distanceMeters,
        Integer durationSeconds,
        String costYuan,
        String tollsYuan,
        Integer walkingDistanceMeters,
        List<String> transitLines,
        String summary) {

    public MapRouteOption {
        mode = mode == null ? MapTransportMode.ALL : mode;
        costYuan = clean(costYuan);
        tollsYuan = clean(tollsYuan);
        transitLines = transitLines == null ? List.of() : List.copyOf(transitLines);
        summary = clean(summary);
    }

    private static String clean(String value) {
        return value == null ? "" : value.strip();
    }
}
