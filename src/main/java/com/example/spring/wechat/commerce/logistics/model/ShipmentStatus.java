package com.example.spring.wechat.commerce.logistics.model;

public enum ShipmentStatus {
    UNKNOWN("状态未知"),
    COLLECTED("已揽收"),
    IN_TRANSIT("运输中"),
    OUT_FOR_DELIVERY("派送中"),
    DELIVERED("已签收"),
    EXCEPTION("运输异常"),
    RETURNING("退回中"),
    PICKUP_AVAILABLE("待取件");

    private final String displayName;

    ShipmentStatus(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }

    public static ShipmentStatus fromKuaidi100State(String state) {
        return switch (state == null ? "" : state.strip()) {
            case "0" -> IN_TRANSIT;
            case "1" -> COLLECTED;
            case "2" -> EXCEPTION;
            case "3" -> DELIVERED;
            case "4" -> RETURNING;
            case "5" -> OUT_FOR_DELIVERY;
            case "6", "8" -> PICKUP_AVAILABLE;
            default -> UNKNOWN;
        };
    }
}
