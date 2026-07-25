package com.example.spring.wechat.map.model;

import com.example.spring.wechat.image.generation.model.ImageGenerationResult;

import java.util.List;

public record MapMultiRouteResult(
        String title,
        List<MapPlace> orderedPlaces,
        List<MapRouteLeg> legs,
        Integer totalDistanceMeters,
        Integer totalDurationSeconds,
        String totalCostYuan,
        MapTransportMode mode,
        MapOrderPolicy orderPolicy,
        ImageGenerationResult image,
        List<String> notices) {

    public MapMultiRouteResult {
        title = title == null ? "" : title.strip();
        orderedPlaces = orderedPlaces == null ? List.of() : List.copyOf(orderedPlaces);
        legs = legs == null ? List.of() : List.copyOf(legs);
        totalCostYuan = totalCostYuan == null ? "" : totalCostYuan.strip();
        mode = mode == null ? MapTransportMode.DRIVING : mode;
        orderPolicy = orderPolicy == null ? MapOrderPolicy.PRESERVE : orderPolicy;
        notices = notices == null ? List.of() : List.copyOf(notices);
    }
}
