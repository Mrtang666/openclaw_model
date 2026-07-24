package com.example.spring.wechat.taxi.service;

import com.example.spring.wechat.taxi.client.DidiTaxiGateway;
import com.example.spring.wechat.taxi.model.RideOrder;
import com.example.spring.wechat.taxi.model.RideOrderStatus;
import com.example.spring.wechat.taxi.model.RideQuote;
import com.example.spring.wechat.taxi.model.RideQuoteOption;
import com.example.spring.wechat.taxi.repository.RideRepository;
import com.example.spring.wechat.taxi.model.RideLocationConfirmation;
import com.example.spring.wechat.taxi.model.RideAppLink;
import com.example.spring.wechat.map.client.MapClient;
import com.example.spring.wechat.map.model.MapPlace;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;

@Service
public class RideOrchestrationService {

    private final DidiTaxiGateway didi;
    private final RideRepository repository;
    private final MapClient mapClient;

    public RideOrchestrationService(DidiTaxiGateway didi, RideRepository repository, MapClient mapClient) {
        this.didi = didi;
        this.repository = repository;
        this.mapClient = mapClient;
    }

    public RideLocationConfirmation prepareLocationConfirmation(String sessionKey, String origin, String destination, String city) {
        if (origin == null || origin.isBlank()) throw new IllegalArgumentException("请补充具体起点，例如道路、园区、商场或小区名称");
        if (destination == null || destination.isBlank()) throw new IllegalArgumentException("请补充具体终点，例如景区入口、道路或建筑名称");
        if (city == null || city.isBlank()) throw new IllegalArgumentException("请补充起终点所在城市");
        MapPlace from = resolveCandidate(origin, city, "起点");
        MapPlace to = resolveDestinationCandidate(destination, city);
        RideLocationConfirmation confirmation = new RideLocationConfirmation(UUID.randomUUID().toString(), sessionKey, city,
                from.name(), from.address(), from.location(), to.name(), to.address(), to.location(),
                Instant.now().plus(10, ChronoUnit.MINUTES));
        repository.saveLocationConfirmation(confirmation);
        return confirmation;
    }

    public RideQuote estimateConfirmed(String sessionKey, String confirmationId) {
        RideLocationConfirmation c = confirmationId == null || confirmationId.isBlank()
                ? repository.findLatestLocationConfirmation(sessionKey)
                : repository.findLocationConfirmation(confirmationId);
        if (c == null || !c.sessionKey().equals(sessionKey)) throw new IllegalArgumentException("没有找到当前会话待确认的起终点，请重新提供地点");
        if (c.expired()) throw new IllegalArgumentException("地点确认已过期，请重新提供起终点");
        return estimateResolved(sessionKey, c.originName(), c.originLocation(), c.destinationName(), c.destinationLocation());
    }

    public RideQuote estimate(String sessionKey, String origin, String destination, String city) {
        JsonNode from = didi.textSearch(origin, city);
        JsonNode to = didi.textSearch(destination, city);
        String fromLocation = location(from);
        String toLocation = location(to);
        if (fromLocation.isBlank() || toLocation.isBlank()) {
            throw new IllegalArgumentException("没有解析到起点或终点坐标，请补充城市和更完整的地点名称");
        }
        return estimateResolved(sessionKey, origin, fromLocation, destination, toLocation);
    }

    private RideQuote estimateResolved(String sessionKey, String origin, String fromLocation, String destination, String toLocation) {
        Map<String, Object> args = Map.of(
                "from_lng", coordinate(fromLocation, 0),
                "from_lat", coordinate(fromLocation, 1),
                "from_name", origin,
                "to_lng", coordinate(toLocation, 0),
                "to_lat", coordinate(toLocation, 1),
                "to_name", destination);
        JsonNode response = didi.estimate(args);
        String traceId = firstText(response, "estimate_trace_id", "estimateTraceId", "trace_id", "traceId");
        if (traceId.isBlank()) {
            throw new IllegalStateException("滴滴未返回 estimate_trace_id，暂时无法安全下单");
        }
        String raw = didi.raw(response);
        RideQuote quote = new RideQuote(
                UUID.randomUUID().toString(), sessionKey, origin, fromLocation,
                destination, toLocation, traceId,
                didi.parseOptions(response, raw), Instant.now().plus(5, ChronoUnit.MINUTES));
        repository.saveQuote(quote, raw);
        return quote;
    }

    private MapPlace resolveCandidate(String query, String city, String label) {
        java.util.List<MapPlace> candidates = mapClient.searchPlaces(query.strip(), city.strip(), 5).stream()
                .filter(MapPlace::hasLocation).toList();
        if (candidates.isEmpty()) throw new IllegalArgumentException("无法确定" + label + "“" + query + "”。请补充所在区、道路、附近建筑或更完整的地点名称");
        return candidates.get(0);
    }

    private MapPlace resolveDestinationCandidate(String query, String city) {
        java.util.List<MapPlace> candidates = mapClient.searchPlaces(query.strip(), city.strip(), 8).stream()
                .filter(MapPlace::hasLocation).toList();
        if (candidates.isEmpty()) throw new IllegalArgumentException("无法确定终点“" + query + "”。请补充所在区、道路、入口或附近建筑");
        MapPlace first = candidates.get(0);
        if (isBroadDestination(query, first)) {
            java.util.List<MapPlace> dropOffs = candidates.stream().skip(1)
                    .filter(place -> isUsefulDropOff(place.name()))
                    .limit(4).toList();
            if (dropOffs.isEmpty()) dropOffs = candidates.stream().skip(1).limit(4).toList();
            StringBuilder message = new StringBuilder("终点“").append(first.name())
                    .append("”范围较大，景区代表坐标可能落在内部景点，不能直接用于下车。请指定入口或具体下车点");
            if (!dropOffs.isEmpty()) {
                message.append("，例如：");
                for (MapPlace place : dropOffs) message.append("\n- ").append(place.name()).append(addressSuffix(place.address()));
            }
            message.append("\n请回复“终点改为 + 具体地点”。");
            throw new IllegalArgumentException(message.toString());
        }
        return first;
    }

    private boolean isBroadDestination(String query, MapPlace place) {
        String text = (query == null ? "" : query) + " " + (place.type() == null ? "" : place.type());
        boolean broadType = text.contains("风景名胜") || text.contains("景区") || text.contains("产业园区") || text.contains("旅游景点");
        boolean specific = query != null && query.matches(".*(入口|出口|[东南西北]门|停车场|游客中心|码头|公园|广场|地铁站|公交站|酒店|博物馆|路|街|号).*" );
        return broadType && !specific;
    }

    private boolean isUsefulDropOff(String name) {
        return name != null && name.matches(".*(入口|出口|门|停车场|游客中心|码头|公园|广场|白堤|苏堤).*" );
    }

    private String addressSuffix(String address) {
        return address == null || address.isBlank() ? "" : "（" + address + "）";
    }

    public RideOrder confirm(String sessionKey, String quoteId, int optionIndex, String phone) {
        RideQuote quote = quoteId == null || quoteId.isBlank()
                ? repository.findLatestQuote(sessionKey) : repository.findQuote(quoteId);
        if (quote == null || !quote.sessionKey().equals(sessionKey)) {
            throw new IllegalArgumentException("报价不存在或不属于当前会话");
        }
        if (quote.expired(Instant.now())) {
            throw new IllegalArgumentException("报价已过期，请重新询价");
        }
        if (optionIndex < 1 || optionIndex > quote.options().size()) {
            throw new IllegalArgumentException("车型编号无效，请回复报价列表中的编号");
        }
        if (phone == null || !phone.matches("^1\\d{10}$")) {
            throw new IllegalArgumentException("直连滴滴下单需要提供与滴滴 App 登录账号一致的 11 位手机号");
        }
        RideQuoteOption option = quote.options().get(optionIndex - 1);
        JsonNode response = didi.createOrder(quote.estimateTraceId(), option.productCategory(), phone);
        String orderId = firstText(response, "order_id", "orderId", "id");
        if (orderId.isBlank()) {
            throw new IllegalStateException("滴滴未返回真实订单号，未创建本地订单");
        }
        RideOrder order = new RideOrder(orderId, sessionKey, quote.quoteId(),
                option.productCategory(), RideOrderStatus.DRIVER_SEARCHING,
                "", "", "", null, null, didi.raw(response), Instant.now());
        repository.saveOrder(order);
        return order;
    }

    public RideAppLink generateRideAppLink(String sessionKey, String quoteId, int optionIndex) {
        RideQuote quote = quoteId == null || quoteId.isBlank()
                ? repository.findLatestQuote(sessionKey) : repository.findQuote(quoteId);
        if (quote == null || !quote.sessionKey().equals(sessionKey)) throw new IllegalArgumentException("报价不存在或不属于当前会话");
        if (quote.expired(Instant.now())) throw new IllegalArgumentException("报价已过期，请重新询价");
        if (optionIndex < 1 || optionIndex > quote.options().size()) throw new IllegalArgumentException("车型编号无效");
        RideQuoteOption option = quote.options().get(optionIndex - 1);
        JsonNode response = didi.generateRideAppLink(Map.of(
                "from_lng", coordinate(quote.originLocation(), 0), "from_lat", coordinate(quote.originLocation(), 1),
                "to_lng", coordinate(quote.destinationLocation(), 0), "to_lat", coordinate(quote.destinationLocation(), 1),
                "product_category", option.productCategory()));
        String app = firstText(response, "appLink", "app_link", "deepLink", "deep_link");
        String miniProgram = firstText(response, "miniprogramLink", "miniProgramLink", "miniprogram_link");
        String browser = firstText(response, "browserLink", "browser_link", "url", "link");
        if (app.isBlank() && miniProgram.isBlank() && browser.isBlank()) throw new IllegalStateException("滴滴未返回可用的跳转链接");
        return new RideAppLink(app, miniProgram, browser);
    }

    public RideOrder query(String sessionKey, String orderId) {
        RideOrder current = orderId == null || orderId.isBlank() ? currentOrder(sessionKey) : repository.findOrder(orderId);
        if (current == null || !current.sessionKey().equals(sessionKey)) {
            throw new IllegalArgumentException("没有找到当前会话的进行中订单");
        }
        JsonNode response = didi.queryOrder(current.orderId());
        RideOrderStatus status = status(response, current.status());
        RideOrder updated = new RideOrder(current.orderId(), current.sessionKey(), current.quoteId(),
                current.productCategory(), status,
                firstText(response, "driver_name", "driverName", "driver_name_text"),
                firstText(response, "driver_phone", "driverPhone", "phone"),
                firstText(response, "vehicle_plate", "vehiclePlate", "plate_number", "car_number"),
                integer(response, "eta", "eta_seconds", "estimated_arrival_time"),
                decimal(response, "final_fare", "finalFare", "total_fee", "amount"),
                didi.raw(response), Instant.now());
        repository.saveOrder(updated);
        return updated;
    }

    public RideOrder prepareCancellation(String sessionKey, String orderId) {
        RideOrder current = query(sessionKey, orderId);
        requireCancellable(current);
        return current;
    }

    public RideOrder cancel(String sessionKey, String orderId, String reason) {
        RideOrder current = query(sessionKey, orderId);
        requireCancellable(current);
        JsonNode response = didi.cancelOrder(current.orderId(), reason);
        RideOrder cancelled = current.withStatus(RideOrderStatus.CANCELLED, didi.raw(response));
        repository.saveOrder(cancelled);
        return cancelled;
    }

    private void requireCancellable(RideOrder order) {
        if (!java.util.EnumSet.of(RideOrderStatus.ORDER_CREATING, RideOrderStatus.DRIVER_SEARCHING,
                RideOrderStatus.DRIVER_ASSIGNED, RideOrderStatus.DRIVER_ARRIVING).contains(order.status())) {
            throw new IllegalStateException("订单当前状态为 " + order.status() + "，已不能取消");
        }
    }

    public java.util.List<RideOrder> activeOrders() {
        return repository.activeOrders();
    }

    private RideOrder queryLocal(String sessionKey, String orderId) {
        RideOrder order = repository.findOrder(orderId);
        if (order == null || !order.sessionKey().equals(sessionKey)) {
            throw new IllegalArgumentException("订单不存在或不属于当前会话");
        }
        return order;
    }

    private RideOrder currentOrder(String sessionKey) {
        return repository.activeOrders().stream()
                .filter(item -> item.sessionKey().equals(sessionKey))
                .filter(item -> item.status() != RideOrderStatus.COMPLETED
                        && item.status() != RideOrderStatus.PAID
                        && item.status() != RideOrderStatus.CANCELLED)
                .findFirst().orElse(null);
    }

    private String location(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) return "";
        java.util.ArrayDeque<JsonNode> pending = new java.util.ArrayDeque<>();
        pending.add(node);
        int visited = 0;
        while (!pending.isEmpty() && visited++ < 512) {
            JsonNode current = pending.removeFirst();
            if (current == null || current.isNull() || current.isMissingNode()) continue;
            if (current.isObject()) {
                String value = firstText(current, "location", "position", "coordinate", "lonlat");
                if (!value.isBlank()) return value;
                current.elements().forEachRemaining(child -> {
                    if (child != null && (child.isObject() || child.isArray())) pending.addLast(child);
                });
            } else if (current.isArray()) {
                current.elements().forEachRemaining(child -> {
                    if (child != null && !child.isNull() && !child.isMissingNode()) pending.addLast(child);
                });
            }
        }
        return "";
    }

    private String coordinate(String location, int index) {
        String[] values = location.split(",", -1);
        return values.length > index ? values[index].strip() : "";
    }

    private RideOrderStatus status(JsonNode node, RideOrderStatus fallback) {
        String value = firstText(node, "status", "order_status", "orderStatus").toLowerCase();
        if (value.contains("complete") || value.contains("finish") || value.contains("完成")) return RideOrderStatus.COMPLETED;
        if (value.contains("cancel")) return RideOrderStatus.CANCELLED;
        if (value.contains("arriv")) return RideOrderStatus.DRIVER_ARRIVING;
        if (value.contains("assign") || value.contains("接单")) return RideOrderStatus.DRIVER_ASSIGNED;
        if (value.contains("trip") || value.contains("行程")) return RideOrderStatus.IN_TRIP;
        return fallback;
    }

    private String firstText(JsonNode node, String... names) {
        for (String name : names) {
            JsonNode value = node == null ? null : node.get(name);
            if (value != null && value.isValueNode() && !value.asText("").isBlank()) return value.asText("").strip();
        }
        return "";
    }

    private Integer integer(JsonNode node, String... names) {
        try { String value = firstText(node, names); return value.isBlank() ? null : Integer.valueOf(value); }
        catch (NumberFormatException ignored) { return null; }
    }

    private java.math.BigDecimal decimal(JsonNode node, String... names) {
        try { String value = firstText(node, names); return value.isBlank() ? null : new java.math.BigDecimal(value); }
        catch (NumberFormatException ignored) { return null; }
    }
}
