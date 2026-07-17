package com.example.spring.weather;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import java.util.stream.Stream;

public final class WeatherModels {
    private WeatherModels() {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CityLookupResponse(String code, List<Location> location) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Location(
        String id,
        String name,
        String adm1,
        String adm2,
        String country) {

        public String displayName() {
            return String.join(" ", Stream.of(name, adm2, adm1, country)
                .filter(value -> value != null && !value.isBlank())
                .distinct()
                .toList());
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record WeatherNowResponse(String code, String updateTime, Now now) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Now(
        String obsTime,
        String temp,
        String feelsLike,
        String text,
        String humidity,
        String windDir,
        String windScale,
        String windSpeed) {
    }

    public record WeatherReport(
        Location location,
        String updateTime,
        String description,
        double temperature,
        double apparentTemperature,
        int humidity,
        String windDirection,
        String windScale,
        double windSpeed) {
    }
}
