package com.example.spring.wechat.food.repository;

import com.example.spring.wechat.food.model.FoodDeliveryAddress;
import com.example.spring.wechat.food.model.FoodOrder;
import com.example.spring.wechat.food.model.FoodOrderDraft;
import com.example.spring.wechat.food.model.FoodOrderPreview;
import com.example.spring.wechat.food.model.FoodPaymentHandoff;
import com.example.spring.wechat.food.model.StoredFoodOrderPreview;

import java.util.List;
import java.util.Optional;

public interface FoodDeliveryRepository {
    FoodDeliveryAddress saveAddress(FoodDeliveryAddress address);

    Optional<FoodDeliveryAddress> findAddress(String userKey, String addressId);

    Optional<FoodDeliveryAddress> findLatestAddress(String userKey);

    List<FoodDeliveryAddress> findAddresses(String userKey);

    FoodOrderDraft saveDraft(FoodOrderDraft draft);

    Optional<FoodOrderDraft> findDraft(String userKey);

    FoodOrderPreview savePreview(FoodOrderPreview preview, String confirmationTokenHash);

    Optional<StoredFoodOrderPreview> findPreview(String previewId);

    FoodOrder saveOrder(FoodOrder order);

    Optional<FoodOrder> findOrder(String userKey, String orderId);

    Optional<FoodOrder> findLatestOrder(String userKey);

    FoodPaymentHandoff savePaymentHandoff(FoodPaymentHandoff handoff);
}
