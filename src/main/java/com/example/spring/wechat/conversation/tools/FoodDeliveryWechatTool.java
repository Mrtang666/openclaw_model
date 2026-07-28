package com.example.spring.wechat.conversation.tools;

import com.example.spring.wechat.bot.WechatReply;
import com.example.spring.wechat.food.model.FoodDeliveryAddress;
import com.example.spring.wechat.food.model.FoodMenuItem;
import com.example.spring.wechat.food.model.FoodMerchant;
import com.example.spring.wechat.food.model.FoodOrder;
import com.example.spring.wechat.food.model.FoodOrderDraft;
import com.example.spring.wechat.food.model.FoodOrderItem;
import com.example.spring.wechat.food.model.FoodOrderPreview;
import com.example.spring.wechat.food.model.FoodPaymentHandoff;
import com.example.spring.wechat.food.service.FoodOrderOrchestrationService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;

@Component
@ConditionalOnProperty(prefix = "food.delivery", name = "enabled", havingValue = "true")
public class FoodDeliveryWechatTool implements WechatTool {
    private static final TypeReference<List<FoodOrderItem>> ITEM_LIST = new TypeReference<>() { };

    private final FoodOrderOrchestrationService service;
    private final ObjectMapper objectMapper;

    public FoodDeliveryWechatTool(FoodOrderOrchestrationService service, ObjectMapper objectMapper) {
        this.service = service;
        this.objectMapper = objectMapper;
    }

    @Override
    public String name() {
        return "food_delivery";
    }

    @Override
    public String description() {
        return "微信外卖点餐工具：保存和复用配送地址，搜索商家与菜单，维护购物车，预结算，确认下单，发起微信/小程序支付，查询或取消订单";
    }

    @Override
    public List<String> arguments() {
        return parameters().stream().map(WechatToolParameter::name).toList();
    }

    @Override
    public List<WechatToolParameter> parameters() {
        return List.of(
                new WechatToolParameter("operation", "string", true,
                        "外卖操作", List.of("list_addresses", "save_address", "search_merchants", "get_menu",
                        "update_cart", "preview_order", "confirm_order", "create_payment", "query_order",
                        "prepare_cancel", "confirm_cancel"), "list_addresses"),
                WechatToolParameter.optionalString("address_id", "配送地址编号；留空时使用最近地址", ""),
                WechatToolParameter.optionalString("label", "地址标签，例如家或公司", "家"),
                WechatToolParameter.optionalString("recipient_name", "收货人姓名", "张先生"),
                WechatToolParameter.optionalString("recipient_phone", "11 位配送联系电话", "13800138000"),
                WechatToolParameter.optionalString("city", "配送城市", "杭州"),
                WechatToolParameter.optionalString("district", "配送区县", "滨江区"),
                WechatToolParameter.optionalString("address_detail", "包含小区、楼栋和门牌号的完整地址", "XX小区3幢1202"),
                WechatToolParameter.optionalString("longitude", "地址经度；平台可自行解析时留空", "120.1"),
                WechatToolParameter.optionalString("latitude", "地址纬度；平台可自行解析时留空", "30.2"),
                WechatToolParameter.optionalBoolean("save_consent", "用户是否明确同意保存常用地址", false),
                WechatToolParameter.optionalString("keyword", "商家、品类或商品关键词", "奶茶"),
                WechatToolParameter.optionalString("merchant_id", "平台商家编号", "merchant-1"),
                WechatToolParameter.optionalString("merchant_name", "商家名称", "示例门店"),
                WechatToolParameter.optionalStringArray("items", "购物车商品 JSON 数组，字段为 productId、skuId、name、specification、quantity、unitPrice", "[]"),
                WechatToolParameter.optionalString("remark", "订单备注", "不要餐具"),
                WechatToolParameter.optionalString("preview_id", "预结算编号", ""),
                WechatToolParameter.optionalString("confirmation_token", "预结算返回的一次性确认 Token", ""),
                WechatToolParameter.optionalString("order_id", "本地外卖订单编号；留空时查询最近订单", ""),
                WechatToolParameter.optionalString("reason", "取消原因", "用户取消"));
    }

    @Override
    public WechatToolCapability capability() {
        return new WechatToolCapability(
                "微信内完成配送地址确认、商家和商品选择、订单预览、显式确认下单、支付交接及配送状态查询",
                List.of(
                        "保存地址前必须获得用户同意",
                        "预结算后必须展示地址、商品规格、费用和预计送达时间",
                        "只有用户明确回复确认下单才能创建真实订单",
                        "支付密码、指纹和人脸确认必须由用户本人完成",
                        "微信支付不可用时使用小程序入口，再退化到 H5",
                        "平台未返回的库存、价格和配送进度不得猜测"),
                List.of("配送地址", "商家和商品", "必选规格", "真实下单前的明确确认"),
                List.of("地址列表", "商家和菜单", "订单预览与确认 Token", "支付入口", "订单及配送状态"));
    }

    @Override
    public WechatReply execute(WechatToolRequest request) {
        try {
            return switch (request.argument("operation").toLowerCase(Locale.ROOT)) {
                case "list_addresses" -> listAddresses(request.userId());
                case "save_address" -> saveAddress(request);
                case "search_merchants" -> searchMerchants(request);
                case "get_menu" -> menu(request);
                case "update_cart" -> updateCart(request);
                case "preview_order" -> preview(request.userId());
                case "confirm_order" -> confirmOrder(request);
                case "create_payment" -> payment(request);
                case "query_order" -> order(service.queryOrder(request.userId(), request.argument("order_id")));
                case "prepare_cancel" -> prepareCancel(request);
                case "confirm_cancel" -> confirmCancel(request);
                default -> WechatReply.text("暂不支持该外卖操作。");
            };
        } catch (RuntimeException exception) {
            return WechatReply.text("外卖服务暂时无法完成：" + rootMessage(exception));
        }
    }

    private WechatReply listAddresses(String userKey) {
        List<FoodDeliveryAddress> addresses = service.addresses(userKey);
        if (addresses.isEmpty()) {
            return WechatReply.text("还没有保存配送地址。请告诉我城市、区县、小区或写字楼、楼栋门牌、收货人和联系电话。");
        }
        StringBuilder text = new StringBuilder("可用配送地址：");
        for (int index = 0; index < addresses.size(); index++) {
            FoodDeliveryAddress address = addresses.get(index);
            text.append("\n").append(index + 1).append(". ").append(address.label())
                    .append("：").append(address.maskedSummary())
                    .append("（地址编号：").append(address.addressId()).append("）");
        }
        FoodDeliveryAddress latest = addresses.get(0);
        text.append("\n\n上次使用的是“").append(latest.label()).append("：")
                .append(latest.maskedSummary()).append("”，请询问用户是否仍送到这里。");
        return WechatReply.text(text.toString());
    }

    private WechatReply saveAddress(WechatToolRequest request) {
        FoodDeliveryAddress address = service.saveAddress(
                request.userId(), request.argument("address_id"), request.argument("label"),
                request.argument("recipient_name"), request.argument("recipient_phone"),
                request.argument("city"), request.argument("district"), request.argument("address_detail"),
                request.argument("longitude"), request.argument("latitude"), request.booleanArgument("save_consent"));
        return WechatReply.text("配送地址已加密保存：" + address.label() + "，" + address.maskedSummary()
                + "\n地址编号：" + address.addressId());
    }

    private WechatReply searchMerchants(WechatToolRequest request) {
        List<FoodMerchant> merchants = service.searchMerchants(
                request.userId(), request.argument("address_id"), request.argument("keyword"));
        if (merchants.isEmpty()) {
            return WechatReply.text("当前地址附近没有找到匹配且可配送的商家。");
        }
        StringBuilder text = new StringBuilder("可配送商家：\n\n| 编号 | 商家 | 起送 | 配送费 | 预计送达 |\n| --- | --- | --- | --- | --- |");
        for (int index = 0; index < merchants.size(); index++) {
            FoodMerchant merchant = merchants.get(index);
            text.append("\n| ").append(index + 1).append(" | ").append(merchant.name())
                    .append(" | ").append(money(merchant.minimumOrder())).append(" | ")
                    .append(money(merchant.deliveryFee())).append(" | ")
                    .append(merchant.etaMinutes() == null ? "以平台为准" : merchant.etaMinutes() + "分钟")
                    .append(" |");
            text.append("\n商家编号 ").append(index + 1).append("：").append(merchant.merchantId());
        }
        return WechatReply.text(text.toString());
    }

    private WechatReply menu(WechatToolRequest request) {
        List<FoodMenuItem> items = service.menu(request.argument("merchant_id"), request.argument("keyword"));
        if (items.isEmpty()) {
            return WechatReply.text("没有找到匹配商品，请更换关键词或商家。");
        }
        StringBuilder text = new StringBuilder("菜单商品：");
        for (FoodMenuItem item : items) {
            text.append("\n- ").append(item.name()).append("：").append(money(item.price()))
                    .append(item.available() ? "" : "（已售罄）")
                    .append("，商品编号：").append(item.productId());
            if (!item.specificationGroups().isEmpty()) {
                text.append("，规格：").append(String.join("、", item.specificationGroups()));
            }
        }
        return WechatReply.text(text.toString());
    }

    private WechatReply updateCart(WechatToolRequest request) {
        FoodOrderDraft draft = service.updateCart(
                request.userId(), request.argument("address_id"), request.argument("merchant_id"),
                request.argument("merchant_name"), parseItems(request.argument("items")), request.argument("remark"));
        return WechatReply.text("购物车已更新：" + draft.merchantName() + "，共 "
                + draft.items().stream().mapToInt(FoodOrderItem::quantity).sum() + " 件商品。可以继续添加商品或进行预结算。");
    }

    private WechatReply preview(String userKey) {
        FoodOrderPreview preview = service.preview(userKey);
        StringBuilder text = new StringBuilder("请确认外卖订单：\n\n商家：").append(preview.merchantName()).append("\n商品：");
        preview.items().forEach(item -> text.append("\n- ").append(item.name()).append(" ×").append(item.quantity())
                .append(item.specification().isBlank() ? "" : "，" + item.specification()));
        text.append("\n\n商品金额：").append(money(preview.subtotal()))
                .append("\n包装费：").append(money(preview.packingFee()))
                .append("\n配送费：").append(money(preview.deliveryFee()))
                .append("\n优惠：").append(money(preview.discount()))
                .append("\n应付：").append(money(preview.total()))
                .append("\n预计送达：").append(preview.etaMinutes() == null ? "以平台为准" : preview.etaMinutes() + "分钟")
                .append("\n\n如信息正确，请明确回复“确认下单”。")
                .append("\n预结算编号：").append(preview.previewId())
                .append("\n确认 Token：").append(preview.confirmationToken());
        return WechatReply.text(text.toString());
    }

    private WechatReply confirmOrder(WechatToolRequest request) {
        FoodOrder order = service.confirmOrder(
                request.userId(), request.argument("preview_id"), request.argument("confirmation_token"),
                request.userText().contains("确认下单"));
        return WechatReply.text("外卖订单已创建。\n订单号：" + order.orderId()
                + "\n商家：" + order.merchantName() + "\n应付：" + money(order.total())
                + "\n下一步可以发起微信支付；若微信支付不可用，将提供外卖小程序入口。");
    }

    private WechatReply payment(WechatToolRequest request) {
        FoodPaymentHandoff handoff = service.createPayment(request.userId(), request.argument("order_id"));
        StringBuilder text = new StringBuilder("支付入口已创建，请由你本人完成支付确认。\n");
        if (!handoff.target().isBlank()) {
            text.append("\n[打开支付页面](").append(handoff.target()).append(")");
        }
        if (!handoff.fallbackTarget().isBlank()) {
            text.append("\n\n如果主支付入口无法打开，请使用小程序/H5兜底：\n")
                    .append(handoff.fallbackTarget());
        } else if (handoff.type() == FoodPaymentHandoff.Type.MINI_PROGRAM) {
            text.append("\n\n这是微信小程序支付入口；微信不允许机器人代替用户点击或输入支付密码。");
        }
        text.append("\n\n支付状态以平台服务端回调或订单查询结果为准。有效期至：")
                .append(handoff.expiresAt());
        return WechatReply.text(text.toString());
    }

    private WechatReply prepareCancel(WechatToolRequest request) {
        FoodOrder current = service.queryOrder(request.userId(), request.argument("order_id"));
        return WechatReply.text(order(current).text()
                + "\n取消可能产生费用或退款等待。如果仍要取消，请明确回复“确认取消外卖订单”。");
    }

    private WechatReply confirmCancel(WechatToolRequest request) {
        FoodOrder cancelled = service.cancelOrder(
                request.userId(), request.argument("order_id"), request.argument("reason"),
                request.userText().contains("确认取消外卖订单"));
        return WechatReply.text("外卖订单取消结果：" + cancelled.status() + "\n订单号：" + cancelled.orderId()
                + (cancelled.progressText().isBlank() ? "" : "\n" + cancelled.progressText()));
    }

    private WechatReply order(FoodOrder order) {
        StringBuilder text = new StringBuilder("外卖订单状态：").append(order.status())
                .append("\n订单号：").append(order.orderId())
                .append("\n商家：").append(order.merchantName());
        if (order.etaMinutes() != null) {
            text.append("\n预计送达：").append(order.etaMinutes()).append("分钟");
        }
        if (!order.progressText().isBlank()) {
            text.append("\n最新进度：").append(order.progressText());
        }
        return WechatReply.text(text.toString());
    }

    private List<FoodOrderItem> parseItems(String json) {
        try {
            if (json == null || json.isBlank()) {
                return List.of();
            }
            return objectMapper.readValue(json, ITEM_LIST);
        } catch (Exception exception) {
            throw new IllegalArgumentException("购物车 items 必须是合法 JSON 数组", exception);
        }
    }

    private String money(BigDecimal value) {
        return value == null ? "以平台为准" : value.stripTrailingZeros().toPlainString() + "元";
    }

    private String rootMessage(Throwable exception) {
        Throwable current = exception;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }
}
