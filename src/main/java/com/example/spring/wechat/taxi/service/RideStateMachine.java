package com.example.spring.wechat.taxi.service;

import com.example.spring.wechat.taxi.model.RideOrderStatus;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.Map;

@Component
public class RideStateMachine {
    private static final Map<RideOrderStatus, EnumSet<RideOrderStatus>> ALLOWED = Map.of(
            RideOrderStatus.DRIVER_SEARCHING, EnumSet.of(RideOrderStatus.DRIVER_ASSIGNED, RideOrderStatus.NO_DRIVER, RideOrderStatus.CANCELLED),
            RideOrderStatus.DRIVER_ASSIGNED, EnumSet.of(RideOrderStatus.DRIVER_ARRIVING, RideOrderStatus.CANCELLED),
            RideOrderStatus.DRIVER_ARRIVING, EnumSet.of(RideOrderStatus.PASSENGER_ONBOARD, RideOrderStatus.CANCELLED),
            RideOrderStatus.PASSENGER_ONBOARD, EnumSet.of(RideOrderStatus.IN_TRIP),
            RideOrderStatus.IN_TRIP, EnumSet.of(RideOrderStatus.COMPLETED),
            RideOrderStatus.COMPLETED, EnumSet.of(RideOrderStatus.PAYMENT_PENDING),
            RideOrderStatus.PAYMENT_PENDING, EnumSet.of(RideOrderStatus.PAID, RideOrderStatus.PAYMENT_FAILED));

    public boolean canTransition(RideOrderStatus from, RideOrderStatus to) {
        return from == to || ALLOWED.getOrDefault(from, EnumSet.noneOf(RideOrderStatus.class)).contains(to);
    }

    public void require(RideOrderStatus from, RideOrderStatus to) {
        if (!canTransition(from, to)) throw new IllegalStateException("非法订单状态变更: " + from + " -> " + to);
    }
}
