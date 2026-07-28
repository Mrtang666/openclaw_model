package com.example.spring.wechat.food.service;

import com.example.spring.wechat.food.gateway.FoodDeliveryGateway;
import com.example.spring.wechat.food.model.FoodDeliveryAddress;
import com.example.spring.wechat.food.model.FoodMenuItem;
import com.example.spring.wechat.food.model.FoodMerchant;
import com.example.spring.wechat.food.model.FoodOrder;
import com.example.spring.wechat.food.model.FoodOrderDraft;
import com.example.spring.wechat.food.model.FoodOrderItem;
import com.example.spring.wechat.food.model.FoodOrderPreview;
import com.example.spring.wechat.food.model.FoodPaymentHandoff;
import com.example.spring.wechat.food.model.StoredFoodOrderPreview;
import com.example.spring.wechat.food.repository.FoodDeliveryRepository;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class FoodOrderOrchestrationService {
    private final FoodDeliveryRepository repository;
    private final FoodDeliveryGateway gateway;

    public FoodOrderOrchestrationService(FoodDeliveryRepository repository, FoodDeliveryGateway gateway) {
        this.repository = repository;
        this.gateway = gateway;
    }

    public List<FoodDeliveryAddress> addresses(String userKey) {
        return repository.findAddresses(require(userKey, "缺少微信用户标识"));
    }

    public Optional<FoodDeliveryAddress> latestAddress(String userKey) {
        return repository.findLatestAddress(require(userKey, "缺少微信用户标识"));
    }

    public FoodDeliveryAddress saveAddress(
            String userKey,
            String addressId,
            String label,
            String recipientName,
            String recipientPhone,
            String city,
            String district,
            String detail,
            String longitude,
            String latitude,
            boolean consent) {
        if (!consent) {
            throw new IllegalArgumentException("保存常用地址前必须获得用户明确同意");
        }
        if (!safe(recipientPhone).matches("^1\\d{10}$")) {
            throw new IllegalArgumentException("请提供 11 位配送联系电话");
        }
        require(city, "请提供配送城市");
        require(detail, "请提供小区、楼栋和门牌号等完整配送地址");
        String owner = require(userKey, "缺少微信用户标识");
        String resolvedAddressId = safe(addressId).isBlank()
                ? UUID.randomUUID().toString()
                : repository.findAddress(owner, addressId)
                        .map(FoodDeliveryAddress::addressId)
                        .orElseThrow(() -> new IllegalArgumentException("只能修改当前用户已有的配送地址"));
        FoodDeliveryAddress address = new FoodDeliveryAddress(
                resolvedAddressId,
                owner,
                safe(label).isBlank() ? "常用地址" : label,
                require(recipientName, "请提供收货人姓名"),
                recipientPhone,
                city,
                district,
                detail,
                longitude,
                latitude,
                true,
                Instant.now());
        return repository.saveAddress(address);
    }

    public List<FoodMerchant> searchMerchants(String userKey, String addressId, String keyword) {
        FoodDeliveryAddress address = address(userKey, addressId);
        return gateway.searchMerchants(address, keyword);
    }

    public List<FoodMenuItem> menu(String merchantId, String keyword) {
        return gateway.menu(require(merchantId, "请先选择商家"), keyword);
    }

    public FoodOrderDraft updateCart(
            String userKey,
            String addressId,
            String merchantId,
            String merchantName,
            List<FoodOrderItem> items,
            String remark) {
        address(userKey, addressId);
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("购物车不能为空，请至少选择一件商品");
        }
        items.forEach(item -> require(item.productId(), "商品缺少 product_id"));
        return repository.saveDraft(new FoodOrderDraft(
                require(userKey, "缺少微信用户标识"),
                addressId,
                require(merchantId, "请先选择商家"),
                require(merchantName, "缺少商家名称"),
                items,
                remark,
                Instant.now()));
    }

    public FoodOrderPreview preview(String userKey) {
        String owner = require(userKey, "缺少微信用户标识");
        FoodOrderDraft draft = repository.findDraft(owner)
                .orElseThrow(() -> new IllegalArgumentException("当前没有待结算购物车"));
        FoodDeliveryAddress address = address(owner, draft.addressId());
        FoodOrderPreview quoted = gateway.preview(address, draft);
        String token = UUID.randomUUID() + "." + UUID.randomUUID();
        FoodOrderPreview preview = new FoodOrderPreview(
                quoted.previewId().isBlank() ? UUID.randomUUID().toString() : quoted.previewId(),
                owner,
                draft.addressId(),
                draft.merchantId(),
                quoted.merchantName().isBlank() ? draft.merchantName() : quoted.merchantName(),
                quoted.items().isEmpty() ? draft.items() : quoted.items(),
                quoted.subtotal(), quoted.packingFee(), quoted.deliveryFee(), quoted.discount(), quoted.total(),
                quoted.etaMinutes(), token,
                quoted.expiresAt().isAfter(Instant.now()) ? quoted.expiresAt() : Instant.now().plusSeconds(300),
                quoted.rawJson());
        repository.savePreview(preview, hash(token));
        return preview;
    }

    public FoodOrder confirmOrder(String userKey, String previewId, String confirmationToken, boolean explicitlyConfirmed) {
        if (!explicitlyConfirmed) {
            throw new IllegalArgumentException("请明确回复“确认下单”后再创建真实订单");
        }
        StoredFoodOrderPreview stored = repository.findPreview(require(previewId, "缺少预结算编号"))
                .orElseThrow(() -> new IllegalArgumentException("没有找到待确认的订单预览"));
        FoodOrderPreview preview = stored.preview();
        if (!preview.userKey().equals(require(userKey, "缺少微信用户标识"))) {
            throw new IllegalArgumentException("该订单预览不属于当前用户");
        }
        if (preview.expired(Instant.now())) {
            throw new IllegalArgumentException("订单价格已经过期，请重新结算");
        }
        if (!MessageDigest.isEqual(
                hash(require(confirmationToken, "缺少订单确认 Token")).getBytes(StandardCharsets.US_ASCII),
                stored.confirmationTokenHash().getBytes(StandardCharsets.US_ASCII))) {
            throw new IllegalArgumentException("订单确认 Token 无效，请重新结算");
        }
        FoodOrder providerOrder = gateway.createOrder(preview);
        String localOrderId = providerOrder.orderId().isBlank() ? UUID.randomUUID().toString() : providerOrder.orderId();
        FoodOrder order = new FoodOrder(
                localOrderId,
                providerOrder.providerOrderId(),
                preview.userKey(),
                preview.previewId(),
                providerOrder.merchantName().isBlank() ? preview.merchantName() : providerOrder.merchantName(),
                providerOrder.status(),
                providerOrder.total() == null ? preview.total() : providerOrder.total(),
                providerOrder.etaMinutes(),
                providerOrder.progressText(),
                providerOrder.rawJson(),
                Instant.now());
        return repository.saveOrder(order);
    }

    public FoodPaymentHandoff createPayment(String userKey, String orderId) {
        FoodOrder order = order(userKey, orderId);
        FoodPaymentHandoff providerHandoff = gateway.createPayment(order);
        if (providerHandoff.target().isBlank() && providerHandoff.fallbackTarget().isBlank()) {
            throw new IllegalStateException("外卖平台没有返回可用的微信、小程序或 H5 支付入口");
        }
        FoodPaymentHandoff handoff = new FoodPaymentHandoff(
                providerHandoff.handoffId().isBlank() ? UUID.randomUUID().toString() : providerHandoff.handoffId(),
                order.orderId(),
                providerHandoff.type(),
                providerHandoff.target(),
                providerHandoff.fallbackTarget(),
                providerHandoff.expiresAt(),
                providerHandoff.status());
        return repository.savePaymentHandoff(handoff);
    }

    public FoodOrder queryOrder(String userKey, String orderId) {
        FoodOrder current = order(userKey, orderId);
        return repository.saveOrder(gateway.queryOrder(current));
    }

    public FoodOrder cancelOrder(String userKey, String orderId, String reason, boolean explicitlyConfirmed) {
        if (!explicitlyConfirmed) {
            throw new IllegalArgumentException("请明确回复“确认取消外卖订单”后再取消");
        }
        FoodOrder current = order(userKey, orderId);
        return repository.saveOrder(gateway.cancelOrder(current, safe(reason).isBlank() ? "用户取消" : reason));
    }

    private FoodDeliveryAddress address(String userKey, String addressId) {
        String owner = require(userKey, "缺少微信用户标识");
        if (safe(addressId).isBlank()) {
            return repository.findLatestAddress(owner)
                    .orElseThrow(() -> new IllegalArgumentException("请先提供并确认配送地址"));
        }
        return repository.findAddress(owner, addressId)
                .orElseThrow(() -> new IllegalArgumentException("没有找到当前用户的配送地址"));
    }

    private FoodOrder order(String userKey, String orderId) {
        String owner = require(userKey, "缺少微信用户标识");
        if (safe(orderId).isBlank()) {
            return repository.findLatestOrder(owner)
                    .orElseThrow(() -> new IllegalArgumentException("当前没有可查询的外卖订单"));
        }
        return repository.findOrder(owner, orderId)
                .orElseThrow(() -> new IllegalArgumentException("没有找到当前用户的外卖订单"));
    }

    private String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("订单确认 Token 处理失败", exception);
        }
    }

    private String require(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value.strip();
    }

    private String safe(String value) {
        return value == null ? "" : value.strip();
    }
}
