package com.example.spring.wechat.map.client;

import com.example.spring.wechat.map.model.MapNearbyCategory;
import com.example.spring.wechat.map.model.MapPlace;
import com.example.spring.wechat.map.model.MapRouteOption;
import com.example.spring.wechat.map.model.MapTransportMode;

import java.util.List;

public interface MapClient {

    List<MapPlace> searchPlaces(String query, String city, int limit);

    MapPlace placeDetail(String placeId);

    List<MapPlace> searchNearby(MapPlace center, MapNearbyCategory category, int radiusMeters, int limit);

    List<MapRouteOption> planRoutes(MapPlace origin, MapPlace destination, MapTransportMode mode);
}
