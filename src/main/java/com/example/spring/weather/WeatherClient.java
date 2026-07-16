package com.example.spring.weather;

public interface WeatherClient {

    WeatherResult query(String city);
}

