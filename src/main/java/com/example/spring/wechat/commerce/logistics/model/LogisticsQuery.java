package com.example.spring.wechat.commerce.logistics.model;

public record LogisticsQuery(
        String trackingNo,
        Carrier carrier,
        String phoneLast4) {

    public LogisticsQuery {
        trackingNo = trackingNo == null ? "" : trackingNo.strip();
        carrier = carrier == null ? Carrier.AUTO : carrier;
        phoneLast4 = phoneLast4 == null ? "" : phoneLast4.strip();
    }
}
