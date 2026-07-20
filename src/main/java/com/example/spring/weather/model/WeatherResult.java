package com.example.spring.weather.model;


/**
 * 天气查询模块组件，负责调用天气接口并整理结果。
 */
import java.util.List;

public record WeatherResult(
        String province,
        String city,
        String weather,
        String temperature,
        String windDirection,
        String windPower,
        String humidity,
        String reportTime,
        List<Forecast> forecasts) {

    public WeatherResult {
        forecasts = forecasts == null ? List.of() : List.copyOf(forecasts);
    }

    public record Forecast(
            String date,
            String week,
            String dayWeather,
            String nightWeather,
            String dayTemperature,
            String nightTemperature,
            String dayWind,
            String nightWind,
            String dayPower,
            String nightPower) {
    }
}

