package com.example.spring.wechat.taxi.client;

import com.example.spring.wechat.taxi.model.RideQuoteOption;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class DidiTaxiGateway {

    private final DidiMcpClient client;
    private final ObjectMapper objectMapper;

    public DidiTaxiGateway(DidiMcpClient client, ObjectMapper objectMapper) {
        this.client = client;
        this.objectMapper = objectMapper;
    }

    public JsonNode textSearch(String keywords, String city) {
        return client.call("maps_textsearch", Map.of("keywords", keywords, "city", city));
    }

    public JsonNode estimate(Map<String, Object> arguments) {
        return client.call("taxi_estimate", arguments);
    }

    public JsonNode createOrder(String estimateTraceId, String productCategory, String phone) {
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("estimate_trace_id", estimateTraceId);
        args.put("product_category", productCategory);
        if (phone != null && !phone.isBlank()) {
            args.put("caller_car_phone", phone);
        }
        return client.call("taxi_create_order", args);
    }

    public JsonNode queryOrder(String orderId) {
        return client.call("taxi_query_order", orderId == null || orderId.isBlank()
                ? Map.of() : Map.of("order_id", orderId));
    }

    public JsonNode driverLocation(String orderId) {
        return client.call("taxi_get_driver_location", Map.of("order_id", orderId));
    }

    public JsonNode cancelOrder(String orderId, String reason) {
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("order_id", orderId);
        if (reason != null && !reason.isBlank()) {
            args.put("reason", reason);
        }
        return client.call("taxi_cancel_order", args);
    }

    public JsonNode generateRideAppLink(Map<String, Object> arguments) {
        return client.call("taxi_generate_ride_app_link", arguments);
    }

    public List<RideQuoteOption> parseOptions(JsonNode root, String rawJson) {
        List<RideQuoteOption> options = new ArrayList<>();
        JsonNode candidates = firstArray(root, "items", "options", "products", "data", "result");
        if (candidates.isArray()) {
            int index = 1;
            for (JsonNode item : candidates) {
                String category = firstText(item, "product_category", "productCategory", "category", "id");
                String name = firstText(item, "name", "product_name", "productName", "title");
                BigDecimal regularPrice = decimal(item, "priceText", "max_price", "maxPrice", "price");
                BigDecimal discountedPrice = decimal(item, "priceDiscounted", "min_price", "minPrice");
                BigDecimal min = discountedPrice == null ? regularPrice : discountedPrice;
                BigDecimal max = regularPrice == null ? min : regularPrice;
                options.add(new RideQuoteOption("option-" + index++, category,
                        name.isBlank() ? category : name, min, max,
                        integer(item, "duration", "duration_seconds", "durationSeconds"),
                        item.toString()));
            }
        }
        return List.copyOf(options);
    }

    public String raw(JsonNode node) {
        try {
            return objectMapper.writeValueAsString(node);
        } catch (Exception exception) {
            return node == null ? "" : node.toString();
        }
    }

    private JsonNode firstArray(JsonNode root, String... names) {
        if (root == null || root.isNull() || root.isMissingNode()) return objectMapper.createArrayNode();
        java.util.ArrayDeque<JsonNode> pending = new java.util.ArrayDeque<>();
        pending.add(root);
        int visited = 0;
        while (!pending.isEmpty() && visited++ < 256) {
            JsonNode current = pending.removeFirst();
            if (current.isObject()) {
                for (String name : names) {
                    JsonNode candidate = current.get(name);
                    if (candidate != null && candidate.isArray()) return candidate;
                }
                current.elements().forEachRemaining(child -> {
                    if (child != null && (child.isObject() || child.isArray())) pending.addLast(child);
                });
            } else if (current.isArray()) {
                current.elements().forEachRemaining(child -> {
                    if (child != null && (child.isObject() || child.isArray())) pending.addLast(child);
                });
            }
        }
        return objectMapper.createArrayNode();
    }

    private String firstText(JsonNode node, String... names) {
        for (String name : names) {
            JsonNode value = node == null ? null : node.get(name);
            if (value != null && value.isValueNode() && !value.asText("").isBlank()) {
                return value.asText("").strip();
            }
        }
        return "";
    }

    private BigDecimal decimal(JsonNode node, String... names) {
        String value = firstText(node, names).replace("元", "").strip();
        try {
            return value.isBlank() ? null : new BigDecimal(value);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private Integer integer(JsonNode node, String... names) {
        String value = firstText(node, names);
        try {
            return value.isBlank() ? null : Integer.valueOf(value);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
