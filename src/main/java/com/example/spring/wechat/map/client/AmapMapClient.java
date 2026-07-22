package com.example.spring.wechat.map.client;

import com.example.spring.wechat.map.model.MapNearbyCategory;
import com.example.spring.wechat.map.model.MapPlace;
import com.example.spring.wechat.map.model.MapRouteOption;
import com.example.spring.wechat.map.model.MapServiceException;
import com.example.spring.wechat.map.model.MapTransportMode;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Component
public class AmapMapClient implements MapClient {

    private static final int AMAP_MAX_PAGE_SIZE = 25;

    private final RestClient restClient;
    private final String key;

    public AmapMapClient(
            RestClient.Builder builder,
            @Value("${amap.map.key:}") String key,
            @Value("${amap.map.base-url:https://restapi.amap.com}") String baseUrl) {
        this.restClient = builder.baseUrl(baseUrl).build();
        this.key = key == null ? "" : key.strip();
    }

    @Override
    public List<MapPlace> searchPlaces(String query, String city, int limit) {
        validateConfiguration();
        String keyword = requireText(query, "缺少要查询的地点");
        int pageSize = clampLimit(limit);
        try {
            JsonNode response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/v3/place/text")
                            .queryParam("key", key)
                            .queryParam("keywords", keyword)
                            .queryParamIfPresent("city", optionalText(city))
                            .queryParam("citylimit", city == null || city.isBlank() ? "false" : "true")
                            .queryParam("extensions", "all")
                            .queryParam("offset", pageSize)
                            .queryParam("page", 1)
                            .queryParam("output", "JSON")
                            .build())
                    .retrieve()
                    .body(JsonNode.class);
            return parsePlaces(response, "地点搜索服务错误");
        } catch (MapServiceException exception) {
            throw exception;
        } catch (RestClientException exception) {
            throw new MapServiceException("地图地点搜索暂时不可用", exception);
        }
    }

    @Override
    public MapPlace placeDetail(String placeId) {
        validateConfiguration();
        String id = requireText(placeId, "缺少地点 ID");
        try {
            JsonNode response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/v3/place/detail")
                            .queryParam("key", key)
                            .queryParam("id", id)
                            .queryParam("extensions", "all")
                            .queryParam("output", "JSON")
                            .build())
                    .retrieve()
                    .body(JsonNode.class);
            List<MapPlace> places = parsePlaces(response, "地点详情服务错误");
            if (places.isEmpty()) {
                throw new MapServiceException("未找到地点详情");
            }
            return places.get(0);
        } catch (MapServiceException exception) {
            throw exception;
        } catch (RestClientException exception) {
            throw new MapServiceException("地图地点详情暂时不可用", exception);
        }
    }

    @Override
    public List<MapPlace> searchNearby(
            MapPlace center,
            MapNearbyCategory category,
            int radiusMeters,
            int limit) {
        validateConfiguration();
        if (center == null || !center.hasLocation()) {
            throw new MapServiceException("中心地点缺少有效坐标");
        }
        MapNearbyCategory safeCategory = category == null ? MapNearbyCategory.ALL : category;
        int safeRadius = Math.max(100, Math.min(radiusMeters, 50000));
        try {
            JsonNode response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/v3/place/around")
                            .queryParam("key", key)
                            .queryParam("location", center.location())
                            .queryParamIfPresent("types", optionalText(safeCategory.amapType()))
                            .queryParam("radius", safeRadius)
                            .queryParam("sortrule", "distance")
                            .queryParam("extensions", "all")
                            .queryParam("offset", clampLimit(limit))
                            .queryParam("page", 1)
                            .queryParam("output", "JSON")
                            .build())
                    .retrieve()
                    .body(JsonNode.class);
            return parsePlaces(response, "周边搜索服务错误");
        } catch (MapServiceException exception) {
            throw exception;
        } catch (RestClientException exception) {
            throw new MapServiceException("地图周边搜索暂时不可用", exception);
        }
    }

    @Override
    public List<MapRouteOption> planRoutes(
            MapPlace origin,
            MapPlace destination,
            MapTransportMode mode) {
        validateRoutePlace(origin, "起点");
        validateRoutePlace(destination, "终点");
        MapTransportMode safeMode = mode == null ? MapTransportMode.ALL : mode;
        try {
            return safeMode == MapTransportMode.ALL
                    ? queryAllRoutes(origin, destination)
                    : switch (safeMode) {
                        case DRIVING -> queryDriving(origin, destination);
                        case TRANSIT -> queryTransit(origin, destination);
                        case WALKING -> queryWalking(origin, destination);
                        case ALL -> throw new IllegalStateException("unexpected route mode");
                    };
        } catch (MapServiceException exception) {
            throw exception;
        } catch (RestClientException exception) {
            throw new MapServiceException("地图路线规划暂时不可用", exception);
        }
    }

    private List<MapRouteOption> queryAllRoutes(MapPlace origin, MapPlace destination) {
        List<MapRouteOption> routes = new ArrayList<>();
        MapServiceException firstFailure = null;
        try {
            routes.addAll(queryDriving(origin, destination));
        } catch (MapServiceException exception) {
            firstFailure = exception;
        }
        try {
            routes.addAll(queryTransit(origin, destination));
        } catch (MapServiceException exception) {
            if (firstFailure == null) {
                firstFailure = exception;
            }
        }
        try {
            routes.addAll(queryWalking(origin, destination));
        } catch (MapServiceException exception) {
            if (firstFailure == null) {
                firstFailure = exception;
            }
        }
        if (routes.isEmpty() && firstFailure != null) {
            throw firstFailure;
        }
        return List.copyOf(routes);
    }

    private List<MapRouteOption> queryDriving(MapPlace origin, MapPlace destination) {
        JsonNode response = restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/v3/direction/driving")
                        .queryParam("key", key)
                        .queryParam("origin", origin.location())
                        .queryParam("destination", destination.location())
                        .queryParam("strategy", 0)
                        .queryParam("extensions", "base")
                        .queryParam("output", "JSON")
                        .build())
                .retrieve()
                .body(JsonNode.class);
        validateResponse(response, "驾车路线服务错误");

        List<MapRouteOption> routes = new ArrayList<>();
        JsonNode paths = response.path("route").path("paths");
        if (paths.isArray()) {
            for (JsonNode path : paths) {
                routes.add(new MapRouteOption(
                        MapTransportMode.DRIVING,
                        integerValue(path, "distance"),
                        integerValue(path, "duration"),
                        "",
                        text(path, "tolls"),
                        null,
                        List.of(),
                        firstNonBlank(text(path, "strategy"), "驾车方案")));
                if (routes.size() >= 2) {
                    break;
                }
            }
        }
        return routes;
    }

    private List<MapRouteOption> queryTransit(MapPlace origin, MapPlace destination) {
        String originCity = firstNonBlank(origin.adcode(), origin.city());
        String destinationCity = firstNonBlank(destination.adcode(), destination.city(), originCity);
        if (originCity.isBlank()) {
            throw new MapServiceException("公共交通规划需要明确起点所在城市");
        }

        JsonNode response = restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/v3/direction/transit/integrated")
                        .queryParam("key", key)
                        .queryParam("origin", origin.location())
                        .queryParam("destination", destination.location())
                        .queryParam("city", originCity)
                        .queryParam("cityd", destinationCity)
                        .queryParam("strategy", 0)
                        .queryParam("extensions", "base")
                        .queryParam("output", "JSON")
                        .build())
                .retrieve()
                .body(JsonNode.class);
        validateResponse(response, "公共交通路线服务错误");

        List<MapRouteOption> routes = new ArrayList<>();
        JsonNode transits = response.path("route").path("transits");
        if (transits.isArray()) {
            for (JsonNode transit : transits) {
                List<String> lines = transitLines(transit.path("segments"));
                routes.add(new MapRouteOption(
                        MapTransportMode.TRANSIT,
                        integerValue(transit, "distance"),
                        integerValue(transit, "duration"),
                        text(transit, "cost"),
                        "",
                        integerValue(transit, "walking_distance"),
                        lines,
                        lines.isEmpty() ? "公共交通方案" : String.join(" → ", lines)));
                if (routes.size() >= 3) {
                    break;
                }
            }
        }
        return routes;
    }

    private List<MapRouteOption> queryWalking(MapPlace origin, MapPlace destination) {
        JsonNode response = restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/v3/direction/walking")
                        .queryParam("key", key)
                        .queryParam("origin", origin.location())
                        .queryParam("destination", destination.location())
                        .queryParam("output", "JSON")
                        .build())
                .retrieve()
                .body(JsonNode.class);
        validateResponse(response, "步行路线服务错误");

        List<MapRouteOption> routes = new ArrayList<>();
        JsonNode paths = response.path("route").path("paths");
        if (paths.isArray() && !paths.isEmpty()) {
            JsonNode path = paths.get(0);
            routes.add(new MapRouteOption(
                    MapTransportMode.WALKING,
                    integerValue(path, "distance"),
                    integerValue(path, "duration"),
                    "",
                    "",
                    integerValue(path, "distance"),
                    List.of(),
                    "步行方案"));
        }
        return routes;
    }

    private List<MapPlace> parsePlaces(JsonNode response, String errorPrefix) {
        validateResponse(response, errorPrefix);
        List<MapPlace> places = new ArrayList<>();
        JsonNode pois = response.path("pois");
        if (!pois.isArray()) {
            return List.of();
        }
        for (JsonNode poi : pois) {
            JsonNode business = poi.path("biz_ext");
            List<String> photos = new ArrayList<>();
            JsonNode photoNodes = poi.path("photos");
            if (photoNodes.isArray()) {
                for (JsonNode photo : photoNodes) {
                    String url = text(photo, "url");
                    if (!url.isBlank()) {
                        photos.add(url);
                    }
                    if (photos.size() >= 3) {
                        break;
                    }
                }
            }
            places.add(new MapPlace(
                    text(poi, "id"),
                    text(poi, "name"),
                    text(poi, "type"),
                    text(poi, "address"),
                    text(poi, "location"),
                    text(poi, "pname"),
                    text(poi, "cityname"),
                    text(poi, "adname"),
                    text(poi, "adcode"),
                    text(poi, "tel"),
                    integerValue(poi, "distance"),
                    text(business, "rating"),
                    text(business, "cost"),
                    firstNonBlank(text(business, "open_time"), text(business, "opentime2")),
                    photos));
        }
        return List.copyOf(places);
    }

    private List<String> transitLines(JsonNode segments) {
        Set<String> lines = new LinkedHashSet<>();
        if (!segments.isArray()) {
            return List.of();
        }
        for (JsonNode segment : segments) {
            JsonNode busLines = segment.path("bus").path("buslines");
            if (busLines.isArray()) {
                for (JsonNode busLine : busLines) {
                    addIfPresent(lines, text(busLine, "name"));
                }
            }
            JsonNode railway = segment.path("railway");
            if (railway.isObject()) {
                addIfPresent(lines, text(railway, "name"));
            }
        }
        return List.copyOf(lines);
    }

    private void validateConfiguration() {
        if (key.isBlank()) {
            throw new MapServiceException("未配置高德地图 Web 服务 KEY");
        }
    }

    private void validateRoutePlace(MapPlace place, String label) {
        validateConfiguration();
        if (place == null || !place.hasLocation()) {
            throw new MapServiceException(label + "缺少有效坐标");
        }
    }

    private void validateResponse(JsonNode response, String prefix) {
        if (response == null || response.isNull()) {
            throw new MapServiceException(prefix + "：服务未返回数据");
        }
        if (!"1".equals(text(response, "status"))) {
            throw new MapServiceException(prefix + "：" + firstNonBlank(text(response, "info"), "未知错误"));
        }
    }

    private int clampLimit(int limit) {
        return Math.max(1, Math.min(limit, AMAP_MAX_PAGE_SIZE));
    }

    private Optional<String> optionalText(String value) {
        return value == null || value.isBlank() ? Optional.empty() : Optional.of(value.strip());
    }

    private String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new MapServiceException(message);
        }
        return value.strip();
    }

    private static String text(JsonNode node, String field) {
        if (node == null || !node.isObject()) {
            return "";
        }
        JsonNode value = node.path(field);
        if (!value.isValueNode()) {
            return "";
        }
        return value.asText("").strip();
    }

    private static Integer integerValue(JsonNode node, String field) {
        String value = text(node, field);
        if (value.isBlank()) {
            return null;
        }
        try {
            return (int) Math.round(Double.parseDouble(value));
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.strip();
            }
        }
        return "";
    }

    private static void addIfPresent(Set<String> values, String value) {
        if (value != null && !value.isBlank()) {
            values.add(value.strip());
        }
    }
}
