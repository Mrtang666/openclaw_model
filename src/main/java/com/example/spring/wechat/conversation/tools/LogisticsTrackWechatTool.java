package com.example.spring.wechat.conversation.tools;

import com.example.spring.wechat.bot.WechatReply;
import com.example.spring.wechat.commerce.logistics.model.ShipmentEvent;
import com.example.spring.wechat.commerce.logistics.model.ShipmentTrace;
import com.example.spring.wechat.commerce.logistics.model.LogisticsServiceException;
import com.example.spring.wechat.commerce.logistics.service.LogisticsTrackService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Component
public class LogisticsTrackWechatTool implements WechatTool {

    private static final Logger log = LoggerFactory.getLogger(LogisticsTrackWechatTool.class);
    private static final int MAX_EVENTS_IN_REPLY = 5;
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter
            .ofPattern("yyyy-MM-dd HH:mm")
            .withZone(ZoneId.of("Asia/Shanghai"));

    private final LogisticsTrackService logisticsTrackService;

    public LogisticsTrackWechatTool(LogisticsTrackService logisticsTrackService) {
        this.logisticsTrackService = logisticsTrackService;
    }

    @Override
    public String name() {
        return "logistics_track";
    }

    @Override
    public String description() {
        return "根据快递单号查询快递物流状态、最新轨迹和近期物流节点；部分快递公司需要手机号后四位";
    }

    @Override
    public List<String> arguments() {
        return List.of("tracking_no", "carrier", "phone_last4");
    }

    @Override
    public List<WechatToolParameter> parameters() {
        return List.of(
                WechatToolParameter.requiredString(
                        "tracking_no",
                        "快递单号，只能包含字母、数字或连字符，长度为 6 到 40 位",
                        "SF1234567890"),
                WechatToolParameter.optionalEnum(
                        "carrier",
                        "快递公司；省略时由物流服务自动识别",
                        List.of("auto", "sf", "jd", "ems", "zto", "yto", "sto", "yunda", "deppon", "jitu"),
                        "sf"),
                WechatToolParameter.optionalString(
                        "phone_last4",
                        "收件人或寄件人手机号后四位；仅在部分快递公司要求校验时提供",
                        "5678"));
    }

    @Override
    public WechatToolCapability capability() {
        return new WechatToolCapability(
                "查询单个快递单号的实时物流状态、最新节点和近期轨迹。",
                List.of(
                        "不获取取件码，不读取短信、菜鸟、丰巢或其他第三方账户数据",
                        "第一版仅支持即时查询，不创建物流订阅或主动提醒",
                        "顺丰等快递公司可能要求手机号后四位；缺失时应提示用户补充",
                        "物流状态和预计送达时间以快递公司返回数据为准"),
                List.of(
                        "用户明确询问快递到了哪里、何时送达、是否签收或提供快递单号时调用",
                        "缺少 tracking_no 时必须先向用户追问，不要猜测或编造单号"),
                List.of(
                        "快递公司、已脱敏的快递单号、当前状态、最新物流位置和近期节点",
                        "快递公司要求手机号校验时，返回补充手机号后四位的提示"));
    }

    @Override
    public WechatReply execute(WechatToolRequest request) {
        String trackingNo = request.argument("tracking_no");
        if (trackingNo.isBlank()) {
            return WechatReply.text("请提供需要查询的快递单号");
        }
        try {
            ShipmentTrace trace = logisticsTrackService.track(
                    trackingNo,
                    request.argument("carrier"),
                    request.argument("phone_last4"));
            return WechatReply.text(format(trace));
        } catch (LogisticsServiceException exception) {
            log.warn("微信物流查询工具执行失败，error={}", exception.getMessage());
            return WechatReply.text("物流查询失败：" + exception.getMessage());
        } catch (RuntimeException exception) {
            log.error("微信物流查询工具发生未预期错误", exception);
            return WechatReply.text("物流查询失败：服务暂时不可用，请稍后再试");
        }
    }

    private String format(ShipmentTrace trace) {
        StringBuilder text = new StringBuilder();
        text.append(trace.carrier().displayName())
                .append(" ")
                .append(trace.trackingNoMasked())
                .append("\n当前状态：")
                .append(trace.status().displayName());
        appendField(text, "最新位置", trace.currentLocation());
        appendField(text, "预计送达", trace.estimatedDelivery());

        if (!trace.events().isEmpty()) {
            text.append("\n近期轨迹：");
            trace.events().stream()
                    .limit(MAX_EVENTS_IN_REPLY)
                    .forEach(event -> appendEvent(text, event));
        }
        text.append("\n查询时间：").append(TIME_FORMATTER.format(trace.queriedAt()));
        return text.toString();
    }

    private void appendEvent(StringBuilder text, ShipmentEvent event) {
        text.append("\n- ");
        if (event.occurredAt() != null) {
            text.append(TIME_FORMATTER.format(event.occurredAt())).append(" ");
        }
        text.append(event.description());
        if (!event.location().isBlank() && !event.description().contains(event.location())) {
            text.append("（").append(event.location()).append("）");
        }
    }

    private void appendField(StringBuilder text, String label, String value) {
        if (value != null && !value.isBlank()) {
            text.append("\n").append(label).append("：").append(value.strip());
        }
    }
}
