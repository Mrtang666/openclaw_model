package com.example.spring.wechat.map.service;

import com.example.spring.wechat.map.client.MapClient;
import com.example.spring.wechat.map.model.MapLink;
import com.example.spring.wechat.map.model.MapNearbyCategory;
import com.example.spring.wechat.map.model.MapOperation;
import com.example.spring.wechat.map.model.MapPlace;
import com.example.spring.wechat.map.model.MapResult;
import com.example.spring.wechat.map.model.MapRouteOption;
import com.example.spring.wechat.map.model.MapServiceException;
import com.example.spring.wechat.map.model.MapTransportMode;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Service
public class MapService {

    private static final int DEFAULT_RESULT_LIMIT = 5;

    private final MapClient mapClient;

    public MapService(MapClient mapClient) {
        this.mapClient = mapClient;
    }

    public MapResult searchPlaces(String query, String city) {
        String keyword = requireText(query, "缺少要查询的地点");
        List<MapPlace> places = mapClient.searchPlaces(keyword, clean(city), DEFAULT_RESULT_LIMIT);
        if (places.isEmpty()) {
            throw new MapServiceException("未找到地点：" + keyword);
        }
        return new MapResult(
                MapOperation.PLACE_SEARCH,
                "地点搜索：" + keyword,
                places,
                List.of(),
                placeLinks(places, false),
                List.of("地点名称可能重名，规划路线前应结合城市和地址确认具体地点。"));
    }

    public MapResult describePlace(String query, String placeId, String city) {
        MapPlace place;
        if (placeId != null && !placeId.isBlank()) {
            place = mapClient.placeDetail(placeId.strip());
        } else {
            place = resolvePlace(query, city, "地点");
            if (!place.id().isBlank()) {
                place = mapClient.placeDetail(place.id());
            }
        }

        List<MapLink> links = new ArrayList<>();
        markerLink(place).ifPresent(links::add);
        if (place.isAttraction()) {
            links.add(ticketSearchLink(place));
        }
        List<String> notices = place.isAttraction()
                ? List.of("票务链接是第三方平台搜索入口，是否售票、实时价格、余票和退改规则以平台页面为准。")
                : List.of();
        return new MapResult(
                MapOperation.PLACE_DETAIL,
                "地点详情：" + place.name(),
                List.of(place),
                List.of(),
                links,
                notices);
    }

    public MapResult planRoute(
            String originQuery,
            String destinationQuery,
            String city,
            MapTransportMode mode) {
        MapPlace origin = resolvePlace(originQuery, city, "起点");
        MapPlace destination = resolvePlace(destinationQuery, city, "终点");
        List<MapRouteOption> routes = mapClient.planRoutes(origin, destination, mode);
        if (routes.isEmpty()) {
            throw new MapServiceException("未找到可用路线，请补充更准确的起点和终点");
        }

        List<MapLink> links = new ArrayList<>();
        navigationLink(origin, destination, "car", "打开高德驾车导航").ifPresent(links::add);
        navigationLink(origin, destination, "bus", "打开高德公交路线").ifPresent(links::add);
        List<String> notices = new ArrayList<>();
        notices.add("路线时间是地图服务估算值，会受实时路况、候车和换乘影响。");
        if (mode == MapTransportMode.ALL) {
            List<String> missingModes = List.of(
                            MapTransportMode.DRIVING,
                            MapTransportMode.TRANSIT,
                            MapTransportMode.WALKING)
                    .stream()
                    .filter(expected -> routes.stream().noneMatch(route -> route.mode() == expected))
                    .map(MapTransportMode::displayName)
                    .toList();
            if (!missingModes.isEmpty()) {
                notices.add("本次未返回" + String.join("、", missingModes) + "方案，已保留其他可用结果。");
            }
        }
        return new MapResult(
                MapOperation.ROUTE,
                origin.name() + " → " + destination.name(),
                List.of(origin, destination),
                routes,
                links,
                notices);
    }

    public MapResult searchNearby(
            String centerQuery,
            String city,
            MapNearbyCategory category,
            int radiusMeters) {
        MapPlace center = resolvePlace(centerQuery, city, "中心地点");
        MapNearbyCategory safeCategory = category == null ? MapNearbyCategory.ALL : category;
        List<MapPlace> places = mapClient.searchNearby(
                center,
                safeCategory,
                radiusMeters,
                DEFAULT_RESULT_LIMIT);
        if (places.isEmpty()) {
            throw new MapServiceException("在指定范围内没有找到" + safeCategory.displayName());
        }

        List<MapLink> links = new ArrayList<>(placeLinks(places, safeCategory == MapNearbyCategory.ATTRACTION));
        List<String> notices = safeCategory == MapNearbyCategory.ATTRACTION
                ? List.of("景点票务链接是第三方平台搜索入口，购票信息以平台页面为准。")
                : List.of("推荐顺序优先参考距离；评分和人均消费可能缺失或存在更新延迟。");
        return new MapResult(
                MapOperation.NEARBY_SEARCH,
                center.name() + "的" + safeCategory.displayName(),
                places,
                List.of(),
                links,
                notices);
    }

    private MapPlace resolvePlace(String query, String city, String label) {
        String keyword = requireText(query, "缺少" + label);
        List<MapPlace> places = mapClient.searchPlaces(keyword, clean(city), 3);
        if (places.isEmpty()) {
            throw new MapServiceException("未找到" + label + "：" + keyword);
        }
        return places.get(0);
    }

    private List<MapLink> placeLinks(List<MapPlace> places, boolean includeTicketLinks) {
        List<MapLink> links = new ArrayList<>();
        for (MapPlace place : places) {
            markerLink(place).ifPresent(links::add);
            if (includeTicketLinks && place.isAttraction()) {
                links.add(ticketSearchLink(place));
            }
        }
        return List.copyOf(links);
    }

    private java.util.Optional<MapLink> markerLink(MapPlace place) {
        if (place == null || !place.hasLocation()) {
            return java.util.Optional.empty();
        }
        String url = "https://uri.amap.com/marker?position="
                + encode(place.location())
                + "&name=" + encode(place.name())
                + "&src=openclaw&coordinate=gaode&callnative=0";
        return java.util.Optional.of(new MapLink("在高德地图查看 " + place.name(), url));
    }

    private java.util.Optional<MapLink> navigationLink(
            MapPlace origin,
            MapPlace destination,
            String mode,
            String label) {
        if (origin == null || destination == null || !origin.hasLocation() || !destination.hasLocation()) {
            return java.util.Optional.empty();
        }
        String url = "https://uri.amap.com/navigation?from="
                + encode(origin.location() + "," + origin.name())
                + "&to=" + encode(destination.location() + "," + destination.name())
                + "&mode=" + encode(mode)
                + "&policy=1&src=openclaw&coordinate=gaode&callnative=0";
        return java.util.Optional.of(new MapLink(label, url));
    }

    private MapLink ticketSearchLink(MapPlace place) {
        String query = firstNonBlank(place.name(), place.address());
        return new MapLink(
                "在携程搜索 " + query + " 门票",
                "https://you.ctrip.com/searchsite/?query=" + encode(query));
    }

    private String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new MapServiceException(message);
        }
        return value.strip();
    }

    private String clean(String value) {
        return value == null ? "" : value.strip();
    }

    private String encode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    private String firstNonBlank(String... values) {
        if (values != null) {
            for (String value : values) {
                if (value != null && !value.isBlank()) {
                    return value.strip();
                }
            }
        }
        return "";
    }
}
