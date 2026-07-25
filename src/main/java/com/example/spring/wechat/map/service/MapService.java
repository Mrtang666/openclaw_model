package com.example.spring.wechat.map.service;

import com.example.spring.wechat.map.client.MapClient;
import com.example.spring.wechat.map.client.MapStaticImageClient;
import com.example.spring.wechat.map.model.MapLink;
import com.example.spring.wechat.map.model.MapMultiRouteResult;
import com.example.spring.wechat.map.model.MapNearbyCategory;
import com.example.spring.wechat.map.model.MapOperation;
import com.example.spring.wechat.map.model.MapOrderPolicy;
import com.example.spring.wechat.map.model.MapPlace;
import com.example.spring.wechat.map.model.MapResult;
import com.example.spring.wechat.map.model.MapRouteLeg;
import com.example.spring.wechat.map.model.MapRouteOption;
import com.example.spring.wechat.map.model.MapServiceException;
import com.example.spring.wechat.map.model.MapTransportMode;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
public class MapService {

    private static final int DEFAULT_RESULT_LIMIT = 5;
    private static final int MAX_MULTI_ROUTE_PLACES = 12;

    private final MapClient mapClient;
    private final MapStaticImageClient mapStaticImageClient;

    public MapService(MapClient mapClient) {
        this(mapClient, null);
    }

    @Autowired
    public MapService(MapClient mapClient, MapStaticImageClient mapStaticImageClient) {
        this.mapClient = mapClient;
        this.mapStaticImageClient = mapStaticImageClient;
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

    public MapMultiRouteResult planMultiRoute(
            List<String> locationQueries,
            String city,
            MapTransportMode mode,
            MapOrderPolicy orderPolicy,
            boolean fixedEnd,
            boolean roundTrip,
            boolean includeMapImage) {
        List<String> queries = cleanLocations(locationQueries);
        if (queries.size() < 2) {
            throw new MapServiceException("多地点路线至少需要两个地点");
        }
        if (queries.size() > MAX_MULTI_ROUTE_PLACES) {
            throw new MapServiceException("一次最多规划 " + MAX_MULTI_ROUTE_PLACES + " 个地点");
        }

        List<MapPlace> places = queries.stream()
                .map(query -> resolvePlace(query, city, "地点"))
                .toList();
        MapOrderPolicy safeOrderPolicy = orderPolicy == null ? MapOrderPolicy.PRESERVE : orderPolicy;
        List<MapPlace> orderedPlaces = safeOrderPolicy == MapOrderPolicy.OPTIMIZE
                ? optimizePlaces(places, fixedEnd)
                : new ArrayList<>(places);
        if (roundTrip) {
            orderedPlaces = new ArrayList<>(orderedPlaces);
            orderedPlaces.add(orderedPlaces.get(0));
        }

        MapTransportMode safeMode = mode == null || mode == MapTransportMode.ALL
                ? MapTransportMode.DRIVING
                : mode;
        List<MapRouteLeg> legs = new ArrayList<>();
        for (int index = 0; index < orderedPlaces.size() - 1; index++) {
            MapPlace origin = orderedPlaces.get(index);
            MapPlace destination = orderedPlaces.get(index + 1);
            List<MapRouteOption> routes = mapClient.planRoutes(origin, destination, safeMode);
            MapRouteOption selected = routes.stream()
                    .min(Comparator.comparingInt(route -> safeInteger(route.durationSeconds())))
                    .orElseThrow(() -> new MapServiceException(
                            "未找到可用路线：" + origin.name() + " → " + destination.name()));
            legs.add(new MapRouteLeg(origin, destination, selected));
        }

        int totalDistance = legs.stream()
                .map(MapRouteLeg::route)
                .mapToInt(route -> safeInteger(route.distanceMeters()))
                .sum();
        int totalDuration = legs.stream()
                .map(MapRouteLeg::route)
                .mapToInt(route -> safeInteger(route.durationSeconds()))
                .sum();
        String totalCost = totalCost(legs);
        String title = orderedPlaces.stream().map(MapPlace::name).reduce((left, right) -> left + " → " + right).orElse("多地点路线");

        List<String> notices = new ArrayList<>();
        notices.add("路线时间是地图服务估算值，实际情况会受实时路况、候车和换乘影响。");
        if (safeOrderPolicy == MapOrderPolicy.OPTIMIZE) {
            notices.add("地点顺序按坐标距离进行近似优化，不代表实时路况下的全局最优路线。");
        }
        if (mode == MapTransportMode.ALL) {
            notices.add("多地点路线未指定交通方式时默认按驾车规划。");
        }

        var image = includeMapImage && mapStaticImageClient != null
                ? mapStaticImageClient.renderRoute(title, orderedPlaces, legs).orElse(null)
                : null;
        if (includeMapImage && image == null) {
            notices.add("本次路线图生成失败，已保留完整文本路线方案。");
        }

        return new MapMultiRouteResult(
                title,
                orderedPlaces,
                legs,
                totalDistance,
                totalDuration,
                totalCost,
                safeMode,
                safeOrderPolicy,
                image,
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
        List<MapPlace> ranked = places.stream()
                .sorted(Comparator.comparingInt((MapPlace place) -> placeScore(place, keyword, city)).reversed())
                .toList();
        if (ranked.size() > 1) {
            int firstScore = placeScore(ranked.get(0), keyword, city);
            int secondScore = placeScore(ranked.get(1), keyword, city);
            if (firstScore < 900
                    && firstScore - secondScore <= 5
                    && !ranked.get(0).name().equals(ranked.get(1).name())) {
                throw new MapServiceException(label + "存在歧义，请补充城市或详细地址："
                        + ranked.get(0).name() + "、" + ranked.get(1).name());
            }
        }
        return ranked.get(0);
    }

    private List<String> cleanLocations(List<String> locations) {
        if (locations == null) {
            return List.of();
        }
        Set<String> unique = new LinkedHashSet<>();
        for (String location : locations) {
            if (location != null && !location.isBlank()) {
                unique.add(location.strip());
            }
        }
        return List.copyOf(unique);
    }

    private int placeScore(MapPlace place, String query, String city) {
        String normalizedQuery = normalize(query);
        String normalizedName = normalize(place.name());
        int lengthDifference = Math.abs(normalizedName.length() - normalizedQuery.length());
        int score;
        if (normalizedName.equals(normalizedQuery)) {
            score = 1000;
        } else if (normalizedName.endsWith(normalizedQuery)) {
            score = 760 - Math.min(lengthDifference, 200);
        } else if (normalizedName.startsWith(normalizedQuery)) {
            score = 720 - Math.min(lengthDifference, 200);
        } else if (normalizedName.contains(normalizedQuery)) {
            score = 560 - Math.min(lengthDifference, 200);
        } else if (normalizedQuery.contains(normalizedName)) {
            score = 420 - Math.min(lengthDifference, 200);
        } else {
            score = 0;
        }
        if (!normalize(city).isBlank() && normalize(place.city()).contains(normalize(city))) {
            score += 50;
        }
        if (normalize(place.address()).contains(normalizedQuery)) {
            score += 20;
        }
        boolean queryLooksLikeStation = normalizedQuery.contains("站")
                || normalizedQuery.contains("机场")
                || normalizedQuery.contains("码头");
        boolean placeLooksLikeStation = normalize(place.type()).contains("车站")
                || normalize(place.type()).contains("地铁站")
                || normalize(place.type()).contains("机场")
                || normalize(place.type()).contains("港口码头");
        if (queryLooksLikeStation == placeLooksLikeStation) {
            score += 35;
        }
        if (!queryLooksLikeStation && place.isAttraction()) {
            score += 30;
        }
        return score;
    }

    private List<MapPlace> optimizePlaces(List<MapPlace> places, boolean fixedEnd) {
        if (places.size() <= 2) {
            return new ArrayList<>(places);
        }
        MapPlace start = places.get(0);
        MapPlace end = fixedEnd ? places.get(places.size() - 1) : null;
        List<MapPlace> remaining = new ArrayList<>(places.subList(1, fixedEnd ? places.size() - 1 : places.size()));
        List<MapPlace> ordered = new ArrayList<>();
        ordered.add(start);
        MapPlace current = start;
        while (!remaining.isEmpty()) {
            MapPlace from = current;
            MapPlace nearest = remaining.stream()
                    .min(Comparator.comparingDouble(candidate -> aerialDistance(from, candidate)))
                    .orElseThrow();
            ordered.add(nearest);
            remaining.remove(nearest);
            current = nearest;
        }
        if (end != null) {
            ordered.add(end);
        }
        improveWithTwoOpt(ordered, fixedEnd);
        return ordered;
    }

    private void improveWithTwoOpt(List<MapPlace> places, boolean fixedEnd) {
        if (places.size() < 4) {
            return;
        }
        int lastMutable = fixedEnd ? places.size() - 2 : places.size() - 1;
        boolean improved = true;
        while (improved) {
            improved = false;
            for (int left = 1; left < lastMutable; left++) {
                for (int right = left + 1; right <= lastMutable; right++) {
                    MapPlace before = places.get(left - 1);
                    MapPlace leftPlace = places.get(left);
                    MapPlace rightPlace = places.get(right);
                    MapPlace after = right + 1 < places.size() ? places.get(right + 1) : null;
                    double current = aerialDistance(before, leftPlace)
                            + (after == null ? 0 : aerialDistance(rightPlace, after));
                    double swapped = aerialDistance(before, rightPlace)
                            + (after == null ? 0 : aerialDistance(leftPlace, after));
                    if (swapped + 0.001 < current) {
                        java.util.Collections.reverse(places.subList(left, right + 1));
                        improved = true;
                    }
                }
            }
        }
    }

    private double aerialDistance(MapPlace left, MapPlace right) {
        try {
            double lat1 = Math.toRadians(Double.parseDouble(left.latitude()));
            double lat2 = Math.toRadians(Double.parseDouble(right.latitude()));
            double deltaLat = lat2 - lat1;
            double deltaLon = Math.toRadians(Double.parseDouble(right.longitude()) - Double.parseDouble(left.longitude()));
            double a = Math.sin(deltaLat / 2) * Math.sin(deltaLat / 2)
                    + Math.cos(lat1) * Math.cos(lat2)
                    * Math.sin(deltaLon / 2) * Math.sin(deltaLon / 2);
            return 6371.0 * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        } catch (NumberFormatException exception) {
            return Double.MAX_VALUE;
        }
    }

    private String totalCost(List<MapRouteLeg> legs) {
        BigDecimal total = BigDecimal.ZERO;
        boolean found = false;
        for (MapRouteLeg leg : legs) {
            String cost = leg.route().costYuan();
            if (cost == null || cost.isBlank()) {
                continue;
            }
            try {
                total = total.add(new BigDecimal(cost.replace("元", "").strip()));
                found = true;
            } catch (NumberFormatException ignored) {
                // Some route providers may return a descriptive cost instead of a number.
            }
        }
        return found ? total.stripTrailingZeros().toPlainString() : "";
    }

    private int safeInteger(Integer value) {
        return value == null || value < 0 ? 0 : value;
    }

    private String normalize(String value) {
        return value == null ? "" : value.replaceAll("\\s+", "").strip().toLowerCase(java.util.Locale.ROOT);
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
