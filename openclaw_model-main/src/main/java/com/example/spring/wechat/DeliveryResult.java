package com.example.spring.wechat;

public record DeliveryResult(boolean success, boolean partial, String error) {
    public static DeliveryResult sent() {
        return new DeliveryResult(true, false, "");
    }

    public static DeliveryResult partial(String error) {
        return new DeliveryResult(false, true, error == null ? "" : error);
    }

    public static DeliveryResult failed(String error) {
        return new DeliveryResult(false, false, error == null ? "" : error);
    }
}
