package com.example.spring.wechat.map.model;

import java.util.List;

public record MapResult(
        MapOperation operation,
        String title,
        List<MapPlace> places,
        List<MapRouteOption> routes,
        List<MapLink> links,
        List<String> notices) {

    public MapResult {
        title = title == null ? "" : title.strip();
        places = places == null ? List.of() : List.copyOf(places);
        routes = routes == null ? List.of() : List.copyOf(routes);
        links = links == null ? List.of() : List.copyOf(links);
        notices = notices == null ? List.of() : List.copyOf(notices);
    }
}
