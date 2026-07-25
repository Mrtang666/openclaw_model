package com.example.spring.wechat.conversation.tools;

import com.example.spring.wechat.bot.WechatReply;
import com.example.spring.wechat.commerce.advice.model.ShoppingAdviceException;
import com.example.spring.wechat.commerce.advice.model.ShoppingAdviceRequest;
import com.example.spring.wechat.commerce.advice.model.ShoppingAdviceResult;
import com.example.spring.wechat.commerce.advice.service.ShoppingAdviceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

@Component
public class ShoppingAdviceWechatTool implements WechatTool {

    private static final Logger log = LoggerFactory.getLogger(ShoppingAdviceWechatTool.class);

    private final ShoppingAdviceService shoppingAdviceService;

    public ShoppingAdviceWechatTool(ShoppingAdviceService shoppingAdviceService) {
        this.shoppingAdviceService = shoppingAdviceService;
    }

    @Override
    public String name() {
        return "shopping_advice";
    }

    @Override
    public String description() {
        return "根据商品品类、预算、用途、偏好和限制条件提供中立选购建议、关键参数、避坑项与购买检查清单";
    }

    @Override
    public List<String> arguments() {
        return List.of("product", "budget_min", "budget_max", "usage", "preferences", "constraints");
    }

    @Override
    public List<WechatToolParameter> parameters() {
        return List.of(
                WechatToolParameter.requiredString("product", "要购买的商品品类或装备类型", "蓝牙耳机"),
                WechatToolParameter.optionalString("budget_min", "最低预算，单位元，不能小于 0", "100"),
                WechatToolParameter.optionalString("budget_max", "最高预算，单位元，不能小于最低预算", "300"),
                WechatToolParameter.optionalString("usage", "主要使用场景", "地铁通勤和日常会议"),
                WechatToolParameter.optionalString("preferences", "希望优先满足的体验或功能", "佩戴舒适、降噪、长续航"),
                WechatToolParameter.optionalString("constraints", "不能接受的条件、尺寸限制或兼容性要求", "不要入耳式，只连接安卓手机"));
    }

    @Override
    public WechatToolCapability capability() {
        return new WechatToolCapability(
                "提供不依赖电商平台 API 的中立选购建议，帮助用户明确关键参数、预算取舍、避坑项和下单前检查清单。",
                List.of(
                        "不搜索、抓取、推荐或返回京东、淘宝及其他平台的具体商品链接",
                        "不提供实时价格、库存、销量、优惠券、佣金、下单或支付能力",
                        "建议用于缩小选购范围，最终规格、售后和价格必须由用户在正规平台核对",
                        "涉及地点、路线或天气时，可由 Agent 组合调用 map_search 和 weather"),
                List.of(
                        "用户询问某类商品怎么选、需要关注哪些参数、预算如何分配或有哪些避坑点时调用",
                        "缺少 product 时先向用户追问商品品类"),
                List.of(
                        "预算建议、核心选购指标、针对使用场景的取舍建议、常见误区和下单检查清单"));
    }

    @Override
    public WechatReply execute(WechatToolRequest request) {
        String product = request.argument("product");
        if (product.isBlank()) {
            return WechatReply.text("请告诉我你想购买什么商品，我再给你选购建议");
        }
        try {
            ShoppingAdviceRequest adviceRequest = new ShoppingAdviceRequest(
                    product,
                    decimalArgument(request, "budget_min"),
                    decimalArgument(request, "budget_max"),
                    request.argument("usage"),
                    request.argument("preferences"),
                    request.argument("constraints"));
            return WechatReply.text(format(shoppingAdviceService.advise(adviceRequest)));
        } catch (ShoppingAdviceException exception) {
            log.warn("微信选购建议工具执行失败，error={}", exception.getMessage());
            return WechatReply.text("生成选购建议失败：" + exception.getMessage());
        } catch (RuntimeException exception) {
            log.error("微信选购建议工具发生未预期错误", exception);
            return WechatReply.text("生成选购建议失败：服务暂时不可用，请稍后再试");
        }
    }

    private BigDecimal decimalArgument(WechatToolRequest request, String name) {
        String value = request.argument(name);
        if (value.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException exception) {
            throw new ShoppingAdviceException(name + " 必须是有效数字");
        }
    }

    private String format(ShoppingAdviceResult result) {
        StringBuilder text = new StringBuilder(result.title());
        appendSection(text, "预算", List.of(result.budgetSummary()));
        appendSection(text, "优先关注", result.priorities());
        appendSection(text, "选择建议", result.recommendations());
        appendSection(text, "常见误区", result.pitfalls());
        appendSection(text, "下单前检查", result.checklist());
        appendSection(text, "说明", result.notices());
        return text.toString().strip();
    }

    private void appendSection(StringBuilder text, String title, List<String> values) {
        if (values == null || values.isEmpty()) {
            return;
        }
        text.append("\n\n").append(title).append("：");
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                text.append("\n- ").append(value.strip());
            }
        }
    }
}
