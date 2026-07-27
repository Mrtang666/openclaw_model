package com.example.spring.wechat.food.exception;

public class FoodDeliveryException extends RuntimeException {
    public FoodDeliveryException(String message) {
        super(message);
    }

    public FoodDeliveryException(String message, Throwable cause) {
        super(message, cause);
    }
}
