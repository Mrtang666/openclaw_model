package com.example.spring.wechat.commerce.logistics.model;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

public enum Carrier {
    AUTO("auto", "自动识别"),
    SF("shunfeng", "顺丰速运"),
    JD("jd", "京东物流"),
    EMS("ems", "EMS"),
    ZTO("zhongtong", "中通快递"),
    YTO("yuantong", "圆通速递"),
    STO("shentong", "申通快递"),
    YUNDA("yunda", "韵达快递"),
    DEPPON("debangkuaidi", "德邦快递"),
    JITU("jtexpress", "极兔速递"),
    OTHER("other", "其他快递公司");

    private final String code;
    private final String displayName;

    Carrier(String code, String displayName) {
        this.code = code;
        this.displayName = displayName;
    }

    public String code() {
        return code;
    }

    public String displayName() {
        return displayName;
    }

    public static Carrier from(String value) {
        if (value == null || value.isBlank()) {
            return AUTO;
        }
        String normalized = value.strip().toLowerCase(Locale.ROOT);
        return Arrays.stream(values())
                .filter(carrier -> carrier.code.equals(normalized)
                        || carrier.name().equalsIgnoreCase(normalized)
                        || carrier.displayName.equals(value.strip()))
                .findFirst()
                .orElseThrow(() -> new LogisticsServiceException(
                        "暂不支持的快递公司：" + value + "，可填写顺丰、京东、EMS、中通、圆通、申通、韵达、德邦或极兔"));
    }

    public static Optional<Carrier> detect(String trackingNo) {
        if (trackingNo == null || trackingNo.isBlank()) {
            return Optional.empty();
        }
        String normalized = trackingNo.strip().toUpperCase(Locale.ROOT);
        if (normalized.startsWith("YT")) {
            return Optional.of(YTO);
        }
        if (normalized.startsWith("SF")) {
            return Optional.of(SF);
        }
        if (normalized.startsWith("JD") || normalized.startsWith("JDX")) {
            return Optional.of(JD);
        }
        if (normalized.startsWith("JT")) {
            return Optional.of(JITU);
        }
        return Optional.empty();
    }
}
