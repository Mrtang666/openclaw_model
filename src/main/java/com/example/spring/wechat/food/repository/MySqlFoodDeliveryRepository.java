package com.example.spring.wechat.food.repository;

import com.example.spring.wechat.food.model.FoodDeliveryAddress;
import com.example.spring.wechat.food.model.FoodOrder;
import com.example.spring.wechat.food.model.FoodOrderDraft;
import com.example.spring.wechat.food.model.FoodOrderItem;
import com.example.spring.wechat.food.model.FoodOrderPreview;
import com.example.spring.wechat.food.model.FoodOrderStatus;
import com.example.spring.wechat.food.model.FoodPaymentHandoff;
import com.example.spring.wechat.food.model.StoredFoodOrderPreview;
import com.example.spring.wechat.food.service.FoodAddressCryptoService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public class MySqlFoodDeliveryRepository implements FoodDeliveryRepository {

    private static final TypeReference<List<FoodOrderItem>> ITEM_LIST = new TypeReference<>() {};

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final FoodAddressCryptoService crypto;

    public MySqlFoodDeliveryRepository(JdbcTemplate jdbc, ObjectMapper objectMapper, FoodAddressCryptoService crypto) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.crypto = crypto;
    }

    @Override
    public FoodDeliveryAddress saveAddress(FoodDeliveryAddress address) {
        Instant now = Instant.now();
        if (address.defaultAddress()) {
            jdbc.update("UPDATE food_delivery_addresses SET is_default = FALSE WHERE user_key = ?", address.userKey());
        }
        jdbc.update("""
                INSERT INTO food_delivery_addresses
                (address_id, user_key, label, recipient_name_encrypted, recipient_phone_encrypted,
                 city, district, detail_encrypted, longitude, latitude, is_default,
                 last_used_at, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE label=VALUES(label), recipient_name_encrypted=VALUES(recipient_name_encrypted),
                    recipient_phone_encrypted=VALUES(recipient_phone_encrypted), city=VALUES(city), district=VALUES(district),
                    detail_encrypted=VALUES(detail_encrypted), longitude=VALUES(longitude), latitude=VALUES(latitude),
                    is_default=VALUES(is_default), last_used_at=VALUES(last_used_at), updated_at=VALUES(updated_at)
                """,
                address.addressId(), address.userKey(), address.label(),
                crypto.encrypt(address.userKey(), address.recipientName()),
                crypto.encrypt(address.userKey(), address.recipientPhone()),
                address.city(), address.district(), crypto.encrypt(address.userKey(), address.detail()),
                address.longitude(), address.latitude(), address.defaultAddress(),
                timestamp(address.lastUsedAt()), timestamp(now), timestamp(now));
        return findAddress(address.userKey(), address.addressId()).orElse(address);
    }

    @Override
    public Optional<FoodDeliveryAddress> findAddress(String userKey, String addressId) {
        return jdbc.query("SELECT * FROM food_delivery_addresses WHERE user_key=? AND address_id=?",
                (rs, row) -> mapAddress(rs), userKey, addressId).stream().findFirst();
    }

    @Override
    public Optional<FoodDeliveryAddress> findLatestAddress(String userKey) {
        return jdbc.query("SELECT * FROM food_delivery_addresses WHERE user_key=? ORDER BY is_default DESC,last_used_at DESC LIMIT 1",
                (rs, row) -> mapAddress(rs), userKey).stream().findFirst();
    }

    @Override
    public List<FoodDeliveryAddress> findAddresses(String userKey) {
        return jdbc.query("SELECT * FROM food_delivery_addresses WHERE user_key=? ORDER BY is_default DESC,last_used_at DESC",
                (rs, row) -> mapAddress(rs), userKey);
    }

    @Override
    public FoodOrderDraft saveDraft(FoodOrderDraft draft) {
        jdbc.update("""
                INSERT INTO food_order_drafts
                (user_key,address_id,merchant_id,merchant_name,items_json,remark,updated_at)
                VALUES (?,?,?,?,?,?,?)
                ON DUPLICATE KEY UPDATE address_id=VALUES(address_id),merchant_id=VALUES(merchant_id),
                    merchant_name=VALUES(merchant_name),items_json=VALUES(items_json),remark=VALUES(remark),updated_at=VALUES(updated_at)
                """, draft.userKey(), draft.addressId(), draft.merchantId(), draft.merchantName(),
                json(draft.items()), draft.remark(), timestamp(draft.updatedAt()));
        return draft;
    }

    @Override
    public Optional<FoodOrderDraft> findDraft(String userKey) {
        return jdbc.query("SELECT * FROM food_order_drafts WHERE user_key=?", (rs, row) -> new FoodOrderDraft(
                rs.getString("user_key"), rs.getString("address_id"), rs.getString("merchant_id"),
                rs.getString("merchant_name"), items(rs.getString("items_json")), rs.getString("remark"),
                instant(rs.getTimestamp("updated_at"))), userKey).stream().findFirst();
    }

    @Override
    public FoodOrderPreview savePreview(FoodOrderPreview preview, String confirmationTokenHash) {
        jdbc.update("""
                INSERT INTO food_order_previews
                (preview_id,user_key,address_id,merchant_id,merchant_name,items_json,subtotal,packing_fee,
                 delivery_fee,discount_amount,total_amount,eta_minutes,confirmation_token_hash,raw_json,expires_at,created_at)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                """, preview.previewId(), preview.userKey(), preview.addressId(), preview.merchantId(), preview.merchantName(),
                json(preview.items()), preview.subtotal(), preview.packingFee(), preview.deliveryFee(), preview.discount(),
                preview.total(), preview.etaMinutes(), confirmationTokenHash, preview.rawJson(),
                timestamp(preview.expiresAt()), timestamp(Instant.now()));
        return preview;
    }

    @Override
    public Optional<StoredFoodOrderPreview> findPreview(String previewId) {
        return jdbc.query("SELECT * FROM food_order_previews WHERE preview_id=?", (rs, row) -> {
            FoodOrderPreview preview = new FoodOrderPreview(
                    rs.getString("preview_id"), rs.getString("user_key"), rs.getString("address_id"),
                    rs.getString("merchant_id"), rs.getString("merchant_name"), items(rs.getString("items_json")),
                    rs.getBigDecimal("subtotal"), rs.getBigDecimal("packing_fee"), rs.getBigDecimal("delivery_fee"),
                    rs.getBigDecimal("discount_amount"), rs.getBigDecimal("total_amount"), nullableInt(rs, "eta_minutes"),
                    "", instant(rs.getTimestamp("expires_at")), rs.getString("raw_json"));
            return new StoredFoodOrderPreview(preview, rs.getString("confirmation_token_hash"));
        }, previewId).stream().findFirst();
    }

    @Override
    public FoodOrder saveOrder(FoodOrder order) {
        Instant now = Instant.now();
        jdbc.update("""
                INSERT INTO food_orders
                (order_id,provider_order_id,user_key,preview_id,merchant_name,status,total_amount,eta_minutes,
                 progress_text,raw_json,created_at,updated_at)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?)
                ON DUPLICATE KEY UPDATE status=VALUES(status),total_amount=VALUES(total_amount),eta_minutes=VALUES(eta_minutes),
                    progress_text=VALUES(progress_text),raw_json=VALUES(raw_json),updated_at=VALUES(updated_at)
                """, order.orderId(), order.providerOrderId(), order.userKey(), order.previewId(), order.merchantName(),
                order.status().name(), order.total(), order.etaMinutes(), order.progressText(), order.rawJson(),
                timestamp(now), timestamp(order.updatedAt()));
        jdbc.update("INSERT INTO food_order_events(order_id,event_type,payload,created_at) VALUES(?,?,?,?)",
                order.orderId(), order.status().name(), order.rawJson(), timestamp(order.updatedAt()));
        return order;
    }

    @Override
    public Optional<FoodOrder> findOrder(String userKey, String orderId) {
        return jdbc.query("SELECT * FROM food_orders WHERE user_key=? AND order_id=?",
                (rs, row) -> mapOrder(rs), userKey, orderId).stream().findFirst();
    }

    @Override
    public Optional<FoodOrder> findLatestOrder(String userKey) {
        return jdbc.query("SELECT * FROM food_orders WHERE user_key=? ORDER BY updated_at DESC LIMIT 1",
                (rs, row) -> mapOrder(rs), userKey).stream().findFirst();
    }

    @Override
    public FoodPaymentHandoff savePaymentHandoff(FoodPaymentHandoff handoff) {
        jdbc.update("""
                INSERT INTO food_payment_handoffs
                (handoff_id,order_id,handoff_type,target_url,fallback_url,status,expires_at,created_at)
                VALUES (?,?,?,?,?,?,?,?)
                ON DUPLICATE KEY UPDATE target_url=VALUES(target_url),fallback_url=VALUES(fallback_url),
                    status=VALUES(status),expires_at=VALUES(expires_at)
                """, handoff.handoffId(), handoff.orderId(), handoff.type().name(), handoff.target(),
                handoff.fallbackTarget(), handoff.status(), timestamp(handoff.expiresAt()), timestamp(Instant.now()));
        return handoff;
    }

    private FoodDeliveryAddress mapAddress(ResultSet rs) throws SQLException {
        String userKey = rs.getString("user_key");
        return new FoodDeliveryAddress(
                rs.getString("address_id"), userKey, rs.getString("label"),
                crypto.decrypt(userKey, rs.getString("recipient_name_encrypted")),
                crypto.decrypt(userKey, rs.getString("recipient_phone_encrypted")),
                rs.getString("city"), rs.getString("district"),
                crypto.decrypt(userKey, rs.getString("detail_encrypted")),
                rs.getString("longitude"), rs.getString("latitude"), rs.getBoolean("is_default"),
                instant(rs.getTimestamp("last_used_at")));
    }

    private FoodOrder mapOrder(ResultSet rs) throws SQLException {
        return new FoodOrder(
                rs.getString("order_id"), rs.getString("provider_order_id"), rs.getString("user_key"),
                rs.getString("preview_id"), rs.getString("merchant_name"), FoodOrderStatus.valueOf(rs.getString("status")),
                rs.getBigDecimal("total_amount"), nullableInt(rs, "eta_minutes"), rs.getString("progress_text"),
                rs.getString("raw_json"), instant(rs.getTimestamp("updated_at")));
    }

    private String json(Object value) {
        try { return objectMapper.writeValueAsString(value); }
        catch (Exception exception) { throw new IllegalArgumentException("外卖数据序列化失败", exception); }
    }

    private List<FoodOrderItem> items(String value) {
        try { return value == null || value.isBlank() ? List.of() : objectMapper.readValue(value, ITEM_LIST); }
        catch (Exception exception) { throw new IllegalArgumentException("购物车数据解析失败", exception); }
    }

    private Integer nullableInt(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }

    private Timestamp timestamp(Instant value) {
        return Timestamp.from(value == null ? Instant.now() : value);
    }

    private Instant instant(Timestamp value) {
        return value == null ? Instant.now() : value.toInstant();
    }
}
