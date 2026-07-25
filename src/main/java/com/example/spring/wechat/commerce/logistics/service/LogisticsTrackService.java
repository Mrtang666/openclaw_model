package com.example.spring.wechat.commerce.logistics.service;

import com.example.spring.wechat.commerce.logistics.client.LogisticsClient;
import com.example.spring.wechat.commerce.logistics.model.Carrier;
import com.example.spring.wechat.commerce.logistics.model.LogisticsQuery;
import com.example.spring.wechat.commerce.logistics.model.LogisticsServiceException;
import com.example.spring.wechat.commerce.logistics.model.ShipmentEvent;
import com.example.spring.wechat.commerce.logistics.model.ShipmentTrace;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;

@Service
public class LogisticsTrackService {

    private final LogisticsClient logisticsClient;

    public LogisticsTrackService(LogisticsClient logisticsClient) {
        this.logisticsClient = logisticsClient;
    }

    public ShipmentTrace track(String trackingNo, String carrier, String phoneLast4) {
        LogisticsQuery query = validate(trackingNo, carrier, phoneLast4);
        ShipmentTrace trace = logisticsClient.query(query);
        List<ShipmentEvent> events = trace.events().stream()
                .sorted(Comparator.comparing(ShipmentEvent::occurredAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
        return new ShipmentTrace(
                trace.trackingNoMasked(),
                trace.carrier(),
                trace.status(),
                trace.currentLocation(),
                trace.estimatedDelivery(),
                events,
                trace.queriedAt());
    }

    private LogisticsQuery validate(String trackingNo, String carrier, String phoneLast4) {
        String number = trackingNo == null ? "" : trackingNo.strip();
        if (number.isBlank()) {
            throw new LogisticsServiceException("请提供快递单号");
        }
        if (!number.matches("[A-Za-z0-9-]{6,40}")) {
            throw new LogisticsServiceException("快递单号格式不正确，请检查后重试");
        }
        String phone = phoneLast4 == null ? "" : phoneLast4.strip();
        if (!phone.isBlank() && !phone.matches("\\d{4}")) {
            throw new LogisticsServiceException("phone_last4 必须是手机号后四位数字");
        }
        Carrier selectedCarrier = Carrier.from(carrier);
        if (selectedCarrier == Carrier.AUTO) {
            selectedCarrier = Carrier.detect(number)
                    .orElseThrow(() -> new LogisticsServiceException(
                            "无法自动识别快递公司，请补充 carrier 参数后重试"));
        }
        return new LogisticsQuery(number, selectedCarrier, phone);
    }
}
