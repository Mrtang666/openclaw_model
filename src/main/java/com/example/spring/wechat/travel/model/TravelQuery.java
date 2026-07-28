package com.example.spring.wechat.travel.model;

public record TravelQuery(String query, String originQuery, String city) {

    public TravelQuery {
        query = query == null ? "" : query.strip();
        originQuery = originQuery == null ? "" : originQuery.strip();
        city = city == null ? "" : city.strip();
    }
}
