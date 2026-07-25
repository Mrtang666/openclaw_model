package com.example.spring.wechat.commerce.logistics.client;

import com.example.spring.wechat.commerce.logistics.model.LogisticsQuery;
import com.example.spring.wechat.commerce.logistics.model.ShipmentTrace;

public interface LogisticsClient {

    ShipmentTrace query(LogisticsQuery query);
}
