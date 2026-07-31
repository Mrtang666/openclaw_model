package com.example.spring.wechat.conversation.tools;

import com.example.spring.wechat.bot.WechatReply;
import com.example.spring.wechat.travel.client.MeituanTravelClient;
import com.example.spring.wechat.travel.client.MeituanTravelClientException;
import com.example.spring.wechat.travel.model.MeituanTravelResult;
import com.example.spring.wechat.travel.model.TravelQuery;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@ConditionalOnProperty(prefix = "meituan.travel", name = "enabled", havingValue = "true")
public class MeituanTravelWechatTool implements WechatTool {

    private final MeituanTravelClient client;

    public MeituanTravelWechatTool(MeituanTravelClient client) {
        this.client = client;
    }

    @Override
    public String name() {
        return "meituan_travel";
    }

    @Override
    public String description() {
        return "美团酒旅查询工具，查询酒店、交通、门票、度假产品和国内行程方案。";
    }

    @Override
    public List<String> arguments() {
        return parameters().stream().map(WechatToolParameter::name).toList();
    }

    @Override
    public List<WechatToolParameter> parameters() {
        return List.of(
                WechatToolParameter.requiredString(
                        "query",
                        "包含日期、人数、预算和偏好的完整旅行查询；不要遗漏用户已经提供的条件",
                        "杭州出发，8月10日至12日，2大1小，上海亲子三日游，总预算6000元"),
                WechatToolParameter.requiredString(
                        "origin_query",
                        "用户本轮原始旅行需求；移除与旅行无关的手机号、证件号等敏感信息",
                        "暑假带孩子从杭州去上海玩三天，预算6000元"),
                WechatToolParameter.optionalString(
                        "city",
                        "当前城市或主要目的地；存在歧义时先向用户确认，不要猜测",
                        "上海"),
                WechatToolParameter.optionalEnum(
                        "request_type",
                        "旅行查询类型，仅用于表达意图",
                        List.of("trip_plan", "hotel", "flight", "train", "attraction", "vacation"),
                        "trip_plan"));
    }

    @Override
    public WechatToolCapability capability() {
        return new WechatToolCapability(
                "通过美团酒旅官方 CLI 查询国内酒旅信息并返回结果",
                List.of(),
                List.of(),
                List.of());
    }

    @Override
    public WechatReply execute(WechatToolRequest request) {
        try {
            MeituanTravelResult result = client.query(new TravelQuery(
                    request.argument("query"),
                    request.argument("origin_query"),
                    request.argument("city")));
            return WechatReply.text(result.content());
        } catch (MeituanTravelClientException exception) {
            return WechatReply.text(friendlyMessage(exception.kind()));
        }
    }

    private String friendlyMessage(MeituanTravelClientException.Kind kind) {
        return switch (kind) {
            case CONFIGURATION -> "美团酒旅服务尚未配置，请联系管理员。";
            case AUTHENTICATION -> "美团酒旅服务鉴权失败，请检查访问凭证。";
            case TIMEOUT -> "美团酒旅查询超时，请稍后重试或缩小查询范围。";
            case NO_RESULT -> "暂未查询到合适结果，可以调整日期、预算或目的地后重试。";
            case OUTPUT_LIMIT -> "美团酒旅返回内容过长，请缩小查询范围后重试。";
            case EXECUTION -> "美团酒旅服务暂时不可用，请稍后重试。";
        };
    }
}
