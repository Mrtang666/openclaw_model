package com.example.spring.wechat.commerce.logistics.client;

import com.example.spring.wechat.commerce.logistics.model.Carrier;
import com.example.spring.wechat.commerce.logistics.model.LogisticsQuery;
import com.example.spring.wechat.commerce.logistics.model.LogisticsServiceException;
import com.example.spring.wechat.commerce.logistics.model.ShipmentEvent;
import com.example.spring.wechat.commerce.logistics.model.ShipmentStatus;
import com.example.spring.wechat.commerce.logistics.model.ShipmentTrace;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
public class Kuaidi100LogisticsClient implements LogisticsClient {

    private static final ZoneId CHINA_ZONE = ZoneId.of("Asia/Shanghai");
    private static final List<DateTimeFormatter> EVENT_TIME_FORMATTERS = List.of(
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String customer;
    private final String key;
    private final Clock clock;

    @Autowired
    public Kuaidi100LogisticsClient(
            RestClient.Builder builder,
            ObjectMapper objectMapper,
            @Value("${kuaidi100.customer:}") String customer,
            @Value("${kuaidi100.key:}") String key,
            @Value("${kuaidi100.base-url:https://poll.kuaidi100.com}") String baseUrl) {
        this(builder, objectMapper, customer, key, baseUrl, Clock.systemUTC());
    }

    Kuaidi100LogisticsClient(
            RestClient.Builder builder,
            ObjectMapper objectMapper,
            String customer,
            String key,
            String baseUrl,
            Clock clock) {
        this.restClient = builder.baseUrl(baseUrl).build();
        this.objectMapper = objectMapper;
        this.customer = normalized(customer);
        this.key = normalized(key);
        this.clock = clock;
    }

    @Override
    public ShipmentTrace query(LogisticsQuery query) {
        validateConfiguration();
        try {
            String param = requestParam(query);
            MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
            form.add("customer", customer);
            form.add("sign", sign(param));
            form.add("param", param);

            String responseBody = restClient.post()
                    .uri("/poll/query.do")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(String.class);
            JsonNode response = objectMapper.readTree(responseBody);
            return parseResponse(response, query);
        } catch (LogisticsServiceException exception) {
            throw exception;
        } catch (RestClientException exception) {
            throw new LogisticsServiceException("快递100物流查询暂时不可用，请稍后再试", exception);
        } catch (JsonProcessingException exception) {
            throw new LogisticsServiceException("物流服务返回数据解析失败，请稍后再试", exception);
        }
    }

    private String requestParam(LogisticsQuery query) throws JsonProcessingException {
        Map<String, String> params = new LinkedHashMap<>();
        if (query.carrier() != Carrier.AUTO) {
            params.put("com", query.carrier().code());
        }
        params.put("num", query.trackingNo());
        if (!query.phoneLast4().isBlank()) {
            params.put("phone", query.phoneLast4());
        }
        params.put("resultv2", "4");
        params.put("show", "0");
        params.put("order", "desc");
        params.put("lang", "zh");
        return objectMapper.writeValueAsString(params);
    }

    private ShipmentTrace parseResponse(JsonNode response, LogisticsQuery query) {
        if (response == null || response.isNull()) {
            throw new LogisticsServiceException("物流服务未返回数据，请稍后再试");
        }
        String message = text(response, "message");
        if (!isSuccessful(response)) {
            throw responseException(text(response, "returnCode"), message);
        }

        List<ShipmentEvent> events = parseEvents(response.path("data"));
        if (events.isEmpty()) {
            throw new LogisticsServiceException("暂未查询到物流轨迹，请核对单号或稍后再试");
        }
        Carrier carrier = carrierFromProviderCode(firstNonBlank(text(response, "com"), query.carrier().code()));
        ShipmentStatus status = ShipmentStatus.fromKuaidi100State(text(response, "state"));
        ShipmentEvent latest = events.get(0);
        String currentLocation = firstNonBlank(latest.location(), latest.description());
        return new ShipmentTrace(
                maskTrackingNo(query.trackingNo()),
                carrier,
                status,
                currentLocation,
                "",
                events,
                clock.instant());
    }

    private List<ShipmentEvent> parseEvents(JsonNode data) {
        if (!data.isArray()) {
            return List.of();
        }
        List<ShipmentEvent> events = new ArrayList<>();
        for (JsonNode item : data) {
            String description = firstNonBlank(text(item, "context"), text(item, "desc"));
            if (description.isBlank()) {
                continue;
            }
            events.add(new ShipmentEvent(
                    parseEventTime(firstNonBlank(text(item, "ftime"), text(item, "time"))),
                    text(item, "location"),
                    description));
        }
        return List.copyOf(events);
    }

    private boolean isSuccessful(JsonNode response) {
        String returnCode = text(response, "returnCode");
        if (!returnCode.isBlank()) {
            return "200".equals(returnCode) || "1".equals(returnCode);
        }
        return !response.hasNonNull("status") || "200".equals(text(response, "status")) || "1".equals(text(response, "status"));
    }

    private LogisticsServiceException responseException(String returnCode, String message) {
        String normalizedMessage = message.toLowerCase(Locale.ROOT);
        if (normalizedMessage.contains("phone") || message.contains("手机号")) {
            return new LogisticsServiceException("该快递公司需要收件人或寄件人手机号后四位，请补充后重试");
        }
        if (normalizedMessage.contains("not found") || message.contains("无此单号") || message.contains("暂无轨迹")) {
            return new LogisticsServiceException("未找到该快递单号的物流信息，请核对单号和快递公司");
        }
        if ("400".equals(returnCode)) {
            return new LogisticsServiceException(
                    "快递100返回 400：" + firstNonBlank(message, "请求参数错误")
                            + "。请核对快递单号、快递公司和 Customer 配置");
        }
        if ("503".equals(returnCode)) {
            return new LogisticsServiceException("快递100账户授权失败，请检查 Customer、Key 和接口权限");
        }
        if ("601".equals(returnCode)) {
            return new LogisticsServiceException("快递100查询次数已达上限，请检查套餐余量或稍后再试");
        }
        return new LogisticsServiceException("物流查询失败：" + firstNonBlank(message, "快递100返回未知错误"));
    }

    private void validateConfiguration() {
        if (customer.isBlank() || key.isBlank()) {
            throw new LogisticsServiceException("物流服务尚未配置，请联系管理员设置快递100凭证");
        }
    }

    private String sign(String param) {
        return md5Hex(param + key + customer).toUpperCase(Locale.ROOT);
    }

    private String md5Hex(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("MD5").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(digest.length * 2);
            for (byte item : digest) {
                result.append(String.format("%02x", item));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("JVM 不支持 MD5", exception);
        }
    }

    private Instant parseEventTime(String value) {
        if (value.isBlank()) {
            return null;
        }
        for (DateTimeFormatter formatter : EVENT_TIME_FORMATTERS) {
            try {
                return LocalDateTime.parse(value, formatter).atZone(CHINA_ZONE).toInstant();
            } catch (DateTimeParseException ignored) {
                // Try the next format returned by the provider.
            }
        }
        return null;
    }

    private String maskTrackingNo(String trackingNo) {
        if (trackingNo.length() <= 4) {
            return "****";
        }
        int visiblePrefix = Math.min(3, trackingNo.length() - 4);
        return trackingNo.substring(0, visiblePrefix) + "****" + trackingNo.substring(trackingNo.length() - 4);
    }

    private Carrier carrierFromProviderCode(String value) {
        if (value == null || value.isBlank()) {
            return Carrier.OTHER;
        }
        return java.util.Arrays.stream(Carrier.values())
                .filter(carrier -> carrier.code().equalsIgnoreCase(value.strip()))
                .findFirst()
                .orElse(Carrier.OTHER);
    }

    private static String text(JsonNode node, String field) {
        if (node == null || !node.isObject()) {
            return "";
        }
        JsonNode value = node.path(field);
        return value.isValueNode() ? value.asText("").strip() : "";
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.strip();
            }
        }
        return "";
    }

    private static String normalized(String value) {
        return value == null ? "" : value.strip();
    }
}
