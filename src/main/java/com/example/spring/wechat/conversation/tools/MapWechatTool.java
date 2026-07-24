package com.example.spring.wechat.conversation.tools;

import com.example.spring.wechat.bot.WechatReply;
import com.example.spring.wechat.map.model.MapMultiRouteResult;
import com.example.spring.wechat.map.model.MapLink;
import com.example.spring.wechat.map.model.MapNearbyCategory;
import com.example.spring.wechat.map.model.MapOperation;
import com.example.spring.wechat.map.model.MapOrderPolicy;
import com.example.spring.wechat.map.model.MapPlace;
import com.example.spring.wechat.map.model.MapResult;
import com.example.spring.wechat.map.model.MapRouteLeg;
import com.example.spring.wechat.map.model.MapRouteOption;
import com.example.spring.wechat.map.model.MapServiceException;
import com.example.spring.wechat.map.model.MapTransportMode;
import com.example.spring.wechat.map.service.MapService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Component
public class MapWechatTool implements WechatTool {

    private static final Logger log = LoggerFactory.getLogger(MapWechatTool.class);
    private static final int DEFAULT_RADIUS_METERS = 3000;

    private final MapService mapService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public MapWechatTool(MapService mapService) {
        this.mapService = mapService;
    }

    @Override
    public String name() {
        return "map_search";
    }

    @Override
    public String description() {
        return "查询地点详情、两地或多地点路线，生成完整路线图，并搜索周边美食、景点和商场";
    }

    @Override
    public List<String> arguments() {
        return List.of(
                "operation",
                "query",
                "place_id",
                "city",
                "origin",
                "destination",
                "locations",
                "transport_mode",
                "order_policy",
                "fixed_end",
                "round_trip",
                "include_map_image",
                "center",
                "category",
                "radius_meters");
    }

    @Override
    public List<WechatToolParameter> parameters() {
        return List.of(
                new WechatToolParameter(
                        "operation",
                        "string",
                        true,
                        "地图操作：地点搜索、地点详情、两地路线、多地点路线或周边搜索",
                        List.of("place_search", "place_detail", "route", "multi_route", "nearby_search"),
                        "route"),
                WechatToolParameter.optionalString(
                        "query",
                        "地点搜索或地点详情使用的明确地点名称",
                        "杭州西湖"),
                WechatToolParameter.optionalString(
                        "place_id",
                        "已知的高德地点 ID；仅 place_detail 可使用，通常留空",
                        "B023B0Z17H"),
                WechatToolParameter.optionalString(
                        "city",
                        "用于消除同名地点歧义的中国城市名；不确定地点时应填写",
                        "杭州"),
                WechatToolParameter.optionalString(
                        "origin",
                        "路线规划起点的地点名称或详细地址",
                        "杭州东站"),
                WechatToolParameter.optionalString(
                        "destination",
                        "路线规划终点的地点名称或详细地址",
                        "杭州西湖断桥"),
                WechatToolParameter.optionalStringArray(
                        "locations",
                        "多地点路线使用的地点数组，按用户表达顺序填写，至少两个地点",
                        "[\"杭州东站\",\"西湖断桥\",\"灵隐寺\"]"),
                WechatToolParameter.optionalEnum(
                        "transport_mode",
                        "路线交通方式；all 会同时比较驾车、公共交通和步行",
                        List.of("all", "driving", "transit", "walking"),
                        "all"),
                WechatToolParameter.optionalEnum(
                        "order_policy",
                        "多地点顺序策略：preserve 保持用户顺序，optimize 近似优化中间地点顺序",
                        List.of("preserve", "optimize"),
                        "preserve"),
                WechatToolParameter.optionalBoolean(
                        "fixed_end",
                        "优化顺序时是否固定最后一个地点为终点",
                        true),
                WechatToolParameter.optionalBoolean(
                        "round_trip",
                        "完成全部地点后是否返回起点",
                        false),
                WechatToolParameter.optionalBoolean(
                        "include_map_image",
                        "是否同时生成包含地点标记和路线折线的完整路线图",
                        true),
                WechatToolParameter.optionalString(
                        "center",
                        "周边搜索中心地点的名称或详细地址",
                        "杭州西湖"),
                WechatToolParameter.optionalEnum(
                        "category",
                        "周边分类：全部、美食、景点或商场",
                        List.of("all", "food", "attraction", "shopping"),
                        "food"),
                WechatToolParameter.optionalString(
                        "radius_meters",
                        "周边搜索半径，单位米，允许 100 到 50000，默认 3000",
                        "3000"));
    }

    @Override
    public WechatToolCapability capability() {
        return new WechatToolCapability(
                "查询中国境内地点、地点详情、两地或多地点驾车/公共交通/步行方案，生成完整路线图，以及搜索指定地点周边的美食、景点和商场。",
                List.of(
                        "缺少 operation 或该操作需要的地点信息时必须追问，不能猜测起点、终点或城市",
                        "同名地点应使用 city 消除歧义；结果仍有歧义时先向用户确认再规划路线",
                        "多地点路线默认保持用户给出的顺序；只有用户明确要求顺路或优化时才使用 optimize",
                        "路线时间和距离来自地图服务估算，不承诺实时交通、班次或可达性",
                        "票务只提供第三方平台搜索入口，不能声称实时有票、价格准确或链接是景区官方渠道",
                        "不支持海外地点、实时公交到站、网约车下单、酒店餐厅预订和实际购票"),
                List.of(
                        "place_search/place_detail：query，已知地点 ID 时可用 place_id",
                        "route：origin 和 destination，建议提供 city",
                        "multi_route：locations，建议提供 city；可选 order_policy、round_trip 和 include_map_image",
                        "nearby_search：center，可选 category 和 radius_meters"),
                List.of(
                        "地点名称、类型、地址、电话、营业信息和评分等可用资料",
                        "路线距离、预计耗时、费用、公共交通线路及方案数量",
                        "多地点路线顺序、逐段方案、总距离、总耗时和完整路线图",
                        "附近地点推荐、地图查看/导航链接和景点票务平台搜索链接"));
    }

    @Override
    public WechatReply execute(WechatToolRequest request) {
        String operationValue = argument(request, "operation");
        if (operationValue.isBlank()) {
            return WechatReply.text("你想查询地点、地点详情、两地路线，还是某个地点的周边推荐？");
        }

        try {
            MapOperation operation = MapOperation.from(operationValue);
            if (operation == MapOperation.MULTI_ROUTE) {
                return executeMultiRoute(request);
            }
            MapResult result = switch (operation) {
                case PLACE_SEARCH -> executePlaceSearch(request);
                case PLACE_DETAIL -> executePlaceDetail(request);
                case ROUTE -> executeRoute(request);
                case MULTI_ROUTE -> throw new IllegalStateException("unexpected multi-route dispatch");
                case NEARBY_SEARCH -> executeNearbySearch(request);
            };
            return WechatReply.text(format(result));
        } catch (MapServiceException exception) {
            log.warn("微信地图工具执行失败，operation={}, error={}", operationValue, exception.getMessage());
            return WechatReply.text("地图查询失败：" + exception.getMessage());
        }
    }

    private MapResult executePlaceSearch(WechatToolRequest request) {
        String query = argument(request, "query");
        if (query.isBlank()) {
            throw new MapServiceException("请告诉我要查询的地点名称");
        }
        return mapService.searchPlaces(query, argument(request, "city"));
    }

    private MapResult executePlaceDetail(WechatToolRequest request) {
        String query = argument(request, "query");
        String placeId = argument(request, "place_id");
        if (query.isBlank() && placeId.isBlank()) {
            throw new MapServiceException("请告诉我要介绍的地点名称");
        }
        return mapService.describePlace(query, placeId, argument(request, "city"));
    }

    private MapResult executeRoute(WechatToolRequest request) {
        String origin = argument(request, "origin");
        String destination = argument(request, "destination");
        if (origin.isBlank() || destination.isBlank()) {
            throw new MapServiceException("路线规划需要同时提供起点和终点");
        }
        return mapService.planRoute(
                origin,
                destination,
                argument(request, "city"),
                MapTransportMode.from(argument(request, "transport_mode")));
    }

    private WechatReply executeMultiRoute(WechatToolRequest request) {
        List<String> locations = parseLocations(argument(request, "locations"));
        if (locations.size() < 2) {
            throw new MapServiceException("多地点路线需要提供至少两个地点");
        }
        MapMultiRouteResult result = mapService.planMultiRoute(
                locations,
                argument(request, "city"),
                multiRouteMode(argument(request, "transport_mode")),
                MapOrderPolicy.from(argument(request, "order_policy")),
                booleanArgument(request, "fixed_end", true),
                booleanArgument(request, "round_trip", false),
                booleanArgument(request, "include_map_image", true));
        String text = formatMultiRoute(result);
        if (result.image() == null || result.image().imageBytes() == null || result.image().imageBytes().length == 0) {
            return WechatReply.text(text);
        }
        return WechatReply.ordered(List.of(
                WechatReply.Part.text(text),
                WechatReply.Part.image("完整路线图", result.image())));
    }

    private MapResult executeNearbySearch(WechatToolRequest request) {
        String center = argument(request, "center");
        if (center.isBlank()) {
            throw new MapServiceException("请告诉我从哪个地点开始搜索周边");
        }
        return mapService.searchNearby(
                center,
                argument(request, "city"),
                MapNearbyCategory.from(argument(request, "category")),
                radiusMeters(argument(request, "radius_meters")));
    }

    private String format(MapResult result) {
        StringBuilder text = new StringBuilder(result.title());
        if (result.operation() == MapOperation.ROUTE) {
            appendRoutes(text, result.routes());
        } else {
            appendPlaces(text, result.places(), result.operation() == MapOperation.NEARBY_SEARCH);
        }
        appendLinks(text, result.links());
        for (String notice : result.notices()) {
            text.append("\n提示：").append(notice.strip());
        }
        return text.toString().strip();
    }

    private String formatMultiRoute(MapMultiRouteResult result) {
        StringBuilder text = new StringBuilder();
        text.append("推荐路线：")
                .append(result.orderedPlaces().stream().map(MapPlace::name).reduce((left, right) -> left + " → " + right).orElse(""));
        text.append("\n交通方式：").append(result.mode().displayName());
        text.append("\n全程：").append(formatDistance(result.totalDistanceMeters()))
                .append("，预计 ").append(formatDuration(result.totalDurationSeconds()));
        if (!result.totalCostYuan().isBlank()) {
            text.append("，费用约 ").append(money(result.totalCostYuan()));
        }

        for (int index = 0; index < result.legs().size(); index++) {
            MapRouteLeg leg = result.legs().get(index);
            MapRouteOption route = leg.route();
            text.append("\n\n").append(index + 1).append(". ")
                    .append(leg.origin().name()).append(" → ").append(leg.destination().name());
            appendField(text, "预计耗时", formatDuration(route.durationSeconds()));
            appendField(text, "距离", formatDistance(route.distanceMeters()));
            appendField(text, "方案", route.summary());
            appendField(text, "线路", String.join(" → ", route.transitLines()));
            appendField(text, "步行", formatDistance(route.walkingDistanceMeters()));
            appendField(text, "费用", money(route.costYuan()));
            if (!route.instructions().isEmpty()) {
                appendField(text, "主要经过", String.join("；", route.instructions().stream().limit(3).toList()));
            }
        }
        for (String notice : result.notices()) {
            text.append("\n提示：").append(notice.strip());
        }
        return text.toString().strip();
    }

    private void appendPlaces(StringBuilder text, List<MapPlace> places, boolean showDistance) {
        for (int index = 0; index < places.size(); index++) {
            MapPlace place = places.get(index);
            text.append("\n\n").append(index + 1).append(". ").append(value(place.name(), "未知地点"));
            appendField(text, "类型", place.type());
            appendField(text, "地址", fullAddress(place));
            if (showDistance && place.distanceMeters() != null) {
                appendField(text, "距离", formatDistance(place.distanceMeters()));
            }
            appendField(text, "评分", place.rating());
            appendField(text, "人均", money(place.averageCost()));
            appendField(text, "营业时间", place.openingHours());
            appendField(text, "电话", place.telephone());
        }
    }

    private void appendRoutes(StringBuilder text, List<MapRouteOption> routes) {
        long transitCount = routes.stream()
                .filter(route -> route.mode() == MapTransportMode.TRANSIT)
                .count();
        if (transitCount > 0) {
            text.append("\n公共交通共返回 ").append(transitCount).append(" 种方案。");
        }
        for (int index = 0; index < routes.size(); index++) {
            MapRouteOption route = routes.get(index);
            text.append("\n\n").append(index + 1).append(". ").append(route.mode().displayName());
            appendField(text, "预计耗时", formatDuration(route.durationSeconds()));
            appendField(text, "距离", formatDistance(route.distanceMeters()));
            appendField(text, "方案", route.summary());
            appendField(text, "线路", String.join(" → ", route.transitLines()));
            appendField(text, "步行", formatDistance(route.walkingDistanceMeters()));
            appendField(text, "费用", money(route.costYuan()));
            appendField(text, "过路费", money(route.tollsYuan()));
        }
    }

    private void appendLinks(StringBuilder text, List<MapLink> links) {
        if (links.isEmpty()) {
            return;
        }
        text.append("\n\n相关链接：");
        for (MapLink link : links) {
            text.append("\n- ").append(link.label()).append("：").append(link.url());
        }
    }

    private void appendField(StringBuilder text, String label, String value) {
        if (value != null && !value.isBlank()) {
            text.append("\n   ").append(label).append("：").append(value.strip());
        }
    }

    private String fullAddress(MapPlace place) {
        Set<String> parts = new LinkedHashSet<>();
        add(parts, place.province());
        add(parts, place.city());
        add(parts, place.district());
        add(parts, place.address());
        return String.join("", parts);
    }

    private int radiusMeters(String value) {
        if (value == null || value.isBlank()) {
            return DEFAULT_RADIUS_METERS;
        }
        try {
            return Math.max(100, Math.min(Integer.parseInt(value.strip()), 50000));
        } catch (NumberFormatException exception) {
            throw new MapServiceException("radius_meters 必须是 100 到 50000 之间的整数");
        }
    }

    private String formatDuration(Integer seconds) {
        if (seconds == null || seconds < 0) {
            return "";
        }
        long minutes = Math.max(1, Math.round(seconds / 60.0));
        if (minutes < 60) {
            return minutes + " 分钟";
        }
        long hours = minutes / 60;
        long remainingMinutes = minutes % 60;
        return remainingMinutes == 0
                ? hours + " 小时"
                : hours + " 小时 " + remainingMinutes + " 分钟";
    }

    private String formatDistance(Integer meters) {
        if (meters == null || meters < 0) {
            return "";
        }
        if (meters < 1000) {
            return meters + " 米";
        }
        return String.format(Locale.ROOT, "%.1f 公里", meters / 1000.0);
    }

    private String money(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return value.contains("元") ? value : value + " 元";
    }

    private String argument(WechatToolRequest request, String name) {
        if (request == null) {
            return "";
        }
        String value = request.argument(name);
        return value == null ? "" : value.strip();
    }

    private List<String> parseLocations(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        try {
            JsonNode node = objectMapper.readTree(value);
            if (node.isArray()) {
                List<String> locations = new ArrayList<>();
                for (JsonNode item : node) {
                    String location = item.asText("").strip();
                    if (!location.isBlank()) {
                        locations.add(location);
                    }
                }
                return List.copyOf(locations);
            }
        } catch (Exception ignored) {
            // Accept a human-readable separated list as a compatibility fallback.
        }
        return java.util.Arrays.stream(value.split("[，,、;；\\n]+"))
                .map(String::strip)
                .filter(location -> !location.isBlank())
                .toList();
    }

    private MapTransportMode multiRouteMode(String value) {
        return value == null || value.isBlank() ? MapTransportMode.DRIVING : MapTransportMode.from(value);
    }

    private boolean booleanArgument(WechatToolRequest request, String name, boolean defaultValue) {
        String value = argument(request, name).toLowerCase(Locale.ROOT);
        if (value.isBlank()) {
            return defaultValue;
        }
        if (List.of("true", "yes", "1", "是", "需要").contains(value)) {
            return true;
        }
        if (List.of("false", "no", "0", "否", "不需要").contains(value)) {
            return false;
        }
        throw new MapServiceException(name + " 必须是 true 或 false");
    }

    private String value(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.strip();
    }

    private void add(Set<String> values, String value) {
        if (value != null && !value.isBlank()) {
            values.add(value.strip());
        }
    }

}
