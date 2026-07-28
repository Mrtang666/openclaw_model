package com.example.spring.wechat.food.gateway;

import com.example.spring.wechat.food.model.FoodDeliveryAddress;
import com.example.spring.wechat.food.model.FoodMenuItem;
import com.example.spring.wechat.food.model.FoodMerchant;
import com.example.spring.wechat.food.model.FoodOrder;
import com.example.spring.wechat.food.model.FoodOrderDraft;
import com.example.spring.wechat.food.model.FoodOrderPreview;
import com.example.spring.wechat.food.model.FoodPaymentHandoff;

import java.util.List;

public interface FoodDeliveryGateway {
    List<FoodMerchant> searchMerchants(FoodDeliveryAddress address, String keyword);

    List<FoodMenuItem> menu(String merchantId, String keyword);

    FoodOrderPreview preview(FoodDeliveryAddress address, FoodOrderDraft draft);

    FoodOrder createOrder(FoodOrderPreview preview);

    FoodPaymentHandoff createPayment(FoodOrder order);

    FoodOrder queryOrder(FoodOrder order);

    FoodOrder cancelOrder(FoodOrder order, String reason);
}
