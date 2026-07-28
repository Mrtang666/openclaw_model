package com.example.spring.wechat.food.model;

public record StoredFoodOrderPreview(FoodOrderPreview preview, String confirmationTokenHash) {
    public StoredFoodOrderPreview {
        confirmationTokenHash = confirmationTokenHash == null ? "" : confirmationTokenHash.strip();
    }
}
