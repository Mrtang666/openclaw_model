package com.example.spring.wechat.food.gateway;

import com.example.spring.wechat.food.config.FoodDeliveryProperties;
import com.example.spring.wechat.food.model.FoodDeliveryAddress;
import com.example.spring.wechat.food.model.FoodMenuItem;
import com.example.spring.wechat.food.model.FoodMerchant;
import com.example.spring.wechat.food.model.FoodOrder;
import com.example.spring.wechat.food.model.FoodOrderDraft;
import com.example.spring.wechat.food.model.FoodOrderItem;
import com.example.spring.wechat.food.model.FoodOrderPreview;
import com.example.spring.wechat.food.model.FoodOrderStatus;
import com.example.spring.wechat.food.model.FoodPaymentHandoff;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class HttpFoodDeliveryGateway implements FoodDeliveryGateway {

    private final FoodDeliveryProperties properties;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    public HttpFoodDeliveryGateway(FoodDeliveryProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.timeoutMs());
        requestFactory.setReadTimeout(properties.timeoutMs());
        this.restClient = RestClient.builder().requestFactory(requestFactory).build();
    }

    @Override
    public List<FoodMerchant> searchMerchants(FoodDeliveryAddress address, String keyword) {
        JsonNode root = post("/v1/food/merchants/search", Map.of(
                "address", addressPayload(address),
                "keyword", safe(keyword)));
        List<FoodMerchant> merchants = new ArrayList<>();
        for (JsonNode item : array(root, "merchants", "items", "data")) {
            merchants.add(new FoodMerchant(
                    text(item, "merchant_id", "merchantId", "id"),
                    text(item, "name", "merchant_name"),
                    item.path("open").asBoolean(true),
                    decimal(item, "minimum_order", "minimumOrder"),
                    decimal(item, "delivery_fee", "deliveryFee"),
                    integer(item, "eta_minutes", "etaMinutes"),
                    text(item, "description", "summary")));
        }
        return List.copyOf(merchants);
    }

    @Override
    public List<FoodMenuItem> menu(String merchantId, String keyword) {
        JsonNode root = post("/v1/food/menu", Map.of(
                "merchant_id", safe(merchantId),
                "keyword", safe(keyword)));
        List<FoodMenuItem> items = new ArrayList<>();
        for (JsonNode item : array(root, "items", "products", "menu")) {
            items.add(new FoodMenuItem(
                    text(item, "product_id", "productId", "id"),
                    text(item, "name", "product_name"),
                    decimal(item, "price", "minimum_price"),
                    item.path("available").asBoolean(true),
                    strings(item.path("specification_groups")),
                    text(item, "description", "summary")));
        }
        return List.copyOf(items);
    }

    @Override
    public FoodOrderPreview preview(FoodDeliveryAddress address, FoodOrderDraft draft) {
        JsonNode root = post("/v1/food/orders/preview", Map.of(
                "address", addressPayload(address),
                "merchant_id", draft.merchantId(),
                "items", draft.items(),
                "remark", draft.remark()));
        return new FoodOrderPreview(
                firstNonBlank(text(root, "preview_id", "previewId"), UUID.randomUUID().toString()),
                draft.userKey(),
                draft.addressId(),
                draft.merchantId(),
                firstNonBlank(text(root, "merchant_name", "merchantName"), draft.merchantName()),
                responseItems(root, draft.items()),
                decimal(root, "subtotal"),
                decimal(root, "packing_fee", "packingFee"),
                decimal(root, "delivery_fee", "deliveryFee"),
                decimal(root, "discount", "discount_amount"),
                requiredDecimal(root, "total", "total_amount"),
                integer(root, "eta_minutes", "etaMinutes"),
                "",
                instant(root, "expires_at", "expiresAt", Instant.now().plusSeconds(300)),
                root.toString());
    }

    @Override
    public FoodOrder createOrder(FoodOrderPreview preview) {
        JsonNode root = post("/v1/food/orders", Map.of(
                "provider_preview_id", preview.previewId(),
                "idempotency_key", preview.previewId()));
        String providerOrderId = text(root, "provider_order_id", "providerOrderId", "order_id", "orderId");
        if (providerOrderId.isBlank()) {
            throw new IllegalStateException("外卖平台未返回真实订单号，订单未创建");
        }
        return new FoodOrder(
                firstNonBlank(text(root, "local_order_id", "localOrderId"), UUID.randomUUID().toString()),
                providerOrderId,
                preview.userKey(),
                preview.previewId(),
                preview.merchantName(),
                status(root, FoodOrderStatus.AWAITING_PAYMENT),
                preview.total(),
                integer(root, "eta_minutes", "etaMinutes"),
                text(root, "progress_text", "progressText"),
                root.toString(),
                Instant.now());
    }

    @Override
    public FoodPaymentHandoff createPayment(FoodOrder order) {
        JsonNode root = post("/v1/food/orders/" + encodePath(order.providerOrderId()) + "/payment", Map.of(
                "preferred_channel", "WECHAT_JSAPI",
                "fallback_channel", "MINI_PROGRAM"));
        FoodPaymentHandoff.Type type = paymentType(text(root, "type", "handoff_type"));
        String target = text(root, "target", "url", "scheme", "code_url");
        String fallback = text(root, "fallback_target", "fallback_url", "mini_program_scheme");
        if (target.isBlank() && fallback.isBlank()) {
            throw new IllegalStateException("外卖平台未返回可用的支付入口");
        }
        return new FoodPaymentHandoff(
                firstNonBlank(text(root, "handoff_id", "handoffId"), UUID.randomUUID().toString()),
                order.orderId(),
                type,
                target,
                fallback,
                instant(root, "expires_at", "expiresAt", Instant.now().plusSeconds(600)),
                "CREATED");
    }

    @Override
    public FoodOrder queryOrder(FoodOrder order) {
        JsonNode root = get("/v1/food/orders/" + encodePath(order.providerOrderId()));
        return new FoodOrder(
                order.orderId(), order.providerOrderId(), order.userKey(), order.previewId(), order.merchantName(),
                status(root, order.status()), decimalOr(root, order.total(), "total", "total_amount"),
                integerOr(root, order.etaMinutes(), "eta_minutes", "etaMinutes"),
                firstNonBlank(text(root, "progress_text", "progressText", "latest_event"), order.progressText()),
                root.toString(), Instant.now());
    }

    @Override
    public FoodOrder cancelOrder(FoodOrder order, String reason) {
        JsonNode root = post("/v1/food/orders/" + encodePath(order.providerOrderId()) + "/cancel", Map.of(
                "reason", safe(reason)));
        return new FoodOrder(
                order.orderId(), order.providerOrderId(), order.userKey(), order.previewId(), order.merchantName(),
                status(root, FoodOrderStatus.CANCELLED), order.total(), order.etaMinutes(),
                firstNonBlank(text(root, "progress_text", "message"), "订单已取消"),
                root.toString(), Instant.now());
    }

    private JsonNode post(String path, Object body) {
        requireConfigured();
        try {
            RestClient.RequestBodySpec request = restClient.post().uri(properties.baseUrl() + path);
            authorize(request);
            return request
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientException exception) {
            throw new IllegalStateException("外卖平台调用失败：" + rootMessage(exception), exception);
        }
    }

    private JsonNode get(String path) {
        requireConfigured();
        try {
            RestClient.RequestHeadersSpec<?> request = restClient.get().uri(properties.baseUrl() + path);
            authorize(request);
            return request
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientException exception) {
            throw new IllegalStateException("外卖订单查询失败：" + rootMessage(exception), exception);
        }
    }

    private void authorize(RestClient.RequestHeadersSpec<?> request) {
        if (!properties.apiKey().isBlank()) {
            request.headers(headers -> headers.setBearerAuth(properties.apiKey()));
        }
    }

    private void requireConfigured() {
        if (!properties.enabled() || properties.baseUrl().isBlank()) {
            throw new IllegalStateException("外卖服务尚未配置，请设置 FOOD_DELIVERY_ENABLED 和 FOOD_DELIVERY_BASE_URL");
        }
    }

    private Map<String, Object> addressPayload(FoodDeliveryAddress address) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("address_id", address.addressId());
        payload.put("recipient_name", address.recipientName());
        payload.put("recipient_phone", address.recipientPhone());
        payload.put("city", address.city());
        payload.put("district", address.district());
        payload.put("detail", address.detail());
        payload.put("longitude", address.longitude());
        payload.put("latitude", address.latitude());
        return payload;
    }

    private List<FoodOrderItem> responseItems(JsonNode root, List<FoodOrderItem> fallback) {
        JsonNode items = root.path("items");
        if (!items.isArray()) {
            return fallback;
        }
        try {
            return objectMapper.readerForListOf(FoodOrderItem.class).readValue(items);
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private Iterable<JsonNode> array(JsonNode root, String... names) {
        for (String name : names) {
            JsonNode value = root == null ? null : root.path(name);
            if (value != null && value.isArray()) {
                return value;
            }
        }
        return List.of();
    }

    private List<String> strings(JsonNode node) {
        if (node == null || !node.isArray()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        for (JsonNode item : node) {
            if (item.isTextual()) {
                values.add(item.asText());
            } else {
                String name = text(item, "name", "title");
                if (!name.isBlank()) values.add(name);
            }
        }
        return List.copyOf(values);
    }

    private String text(JsonNode node, String... names) {
        for (String name : names) {
            JsonNode value = node == null ? null : node.get(name);
            if (value != null && value.isValueNode() && !value.asText("").isBlank()) return value.asText("").strip();
        }
        return "";
    }

    private BigDecimal decimal(JsonNode node, String... names) {
        String value = text(node, names);
        try { return value.isBlank() ? null : new BigDecimal(value); }
        catch (NumberFormatException ignored) { return null; }
    }

    private BigDecimal requiredDecimal(JsonNode node, String... names) {
        BigDecimal value = decimal(node, names);
        if (value == null) throw new IllegalStateException("外卖平台未返回订单应付金额");
        return value;
    }

    private BigDecimal decimalOr(JsonNode node, BigDecimal fallback, String... names) {
        BigDecimal value = decimal(node, names);
        return value == null ? fallback : value;
    }

    private Integer integer(JsonNode node, String... names) {
        String value = text(node, names);
        try { return value.isBlank() ? null : Integer.valueOf(value); }
        catch (NumberFormatException ignored) { return null; }
    }

    private Integer integerOr(JsonNode node, Integer fallback, String... names) {
        Integer value = integer(node, names);
        return value == null ? fallback : value;
    }

    private Instant instant(JsonNode node, String first, String second, Instant fallback) {
        String value = text(node, first, second);
        try { return value.isBlank() ? fallback : Instant.parse(value); }
        catch (Exception ignored) { return fallback; }
    }

    private FoodOrderStatus status(JsonNode node, FoodOrderStatus fallback) {
        String value = text(node, "status", "order_status").toUpperCase(java.util.Locale.ROOT);
        try { return value.isBlank() ? fallback : FoodOrderStatus.valueOf(value); }
        catch (IllegalArgumentException ignored) { return fallback; }
    }

    private FoodPaymentHandoff.Type paymentType(String value) {
        try { return FoodPaymentHandoff.Type.valueOf(safe(value).toUpperCase(java.util.Locale.ROOT)); }
        catch (IllegalArgumentException ignored) { return FoodPaymentHandoff.Type.MANUAL; }
    }

    private String encodePath(String value) {
        return java.net.URLEncoder.encode(safe(value), java.nio.charset.StandardCharsets.UTF_8);
    }

    private String firstNonBlank(String... values) {
        for (String value : values) if (value != null && !value.isBlank()) return value.strip();
        return "";
    }

    private String safe(String value) {
        return value == null ? "" : value.strip();
    }

    private String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) current = current.getCause();
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }
}
