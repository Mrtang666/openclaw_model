package com.example.spring.wechat.map.model;

import java.util.List;

public record MapPlace(
        String id,
        String name,
        String type,
        String address,
        String location,
        String province,
        String city,
        String district,
        String adcode,
        String telephone,
        Integer distanceMeters,
        String rating,
        String averageCost,
        String openingHours,
        List<String> photos) {

    public MapPlace {
        id = clean(id);
        name = clean(name);
        type = clean(type);
        address = clean(address);
        location = clean(location);
        province = clean(province);
        city = clean(city);
        district = clean(district);
        adcode = clean(adcode);
        telephone = clean(telephone);
        rating = clean(rating);
        averageCost = clean(averageCost);
        openingHours = clean(openingHours);
        photos = photos == null ? List.of() : List.copyOf(photos);
    }

    public String longitude() {
        return coordinate(0);
    }

    public String latitude() {
        return coordinate(1);
    }

    public boolean hasLocation() {
        return !longitude().isBlank() && !latitude().isBlank();
    }

    public boolean isAttraction() {
        return type.startsWith("风景名胜") || type.contains("景点") || type.contains("旅游景点");
    }

    private String coordinate(int index) {
        String[] parts = location.split(",", -1);
        return parts.length > index ? parts[index].strip() : "";
    }

    private static String clean(String value) {
        return value == null ? "" : value.strip();
    }
}
