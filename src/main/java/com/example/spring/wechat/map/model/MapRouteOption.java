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
        String summary,
        List<String> instructions,
        List<String> polyline) {

    public MapRouteOption {
        mode = mode == null ? MapTransportMode.ALL : mode;
        costYuan = clean(costYuan);
        tollsYuan = clean(tollsYuan);
        transitLines = transitLines == null ? List.of() : List.copyOf(transitLines);
        summary = clean(summary);
        instructions = cleanList(instructions);
        polyline = cleanList(polyline);
    }

    public MapRouteOption(
            MapTransportMode mode,
            Integer distanceMeters,
            Integer durationSeconds,
            String costYuan,
            String tollsYuan,
            Integer walkingDistanceMeters,
            List<String> transitLines,
            String summary) {
        this(mode, distanceMeters, durationSeconds, costYuan, tollsYuan,
                walkingDistanceMeters, transitLines, summary, List.of(), List.of());
    }

    private static String clean(String value) {
        return value == null ? "" : value.strip();
    }

    private static List<String> cleanList(List<String> values) {
        return values == null
                ? List.of()
                : values.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(String::strip)
                .toList();
    }
}
