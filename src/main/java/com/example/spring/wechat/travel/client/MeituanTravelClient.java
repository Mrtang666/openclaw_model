package com.example.spring.wechat.travel.client;

import com.example.spring.wechat.travel.model.MeituanTravelResult;
import com.example.spring.wechat.travel.model.TravelQuery;

public interface MeituanTravelClient {

    MeituanTravelResult query(TravelQuery query);
}
