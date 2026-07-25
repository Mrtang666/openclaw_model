package com.example.spring.wechat.map.model;

public record MapRouteLeg(
        MapPlace origin,
        MapPlace destination,
        MapRouteOption route) {

    public MapRouteLeg {
        if (origin == null || destination == null || route == null) {
            throw new IllegalArgumentException("路线分段必须包含起点、终点和路线");
        }
    }
}
