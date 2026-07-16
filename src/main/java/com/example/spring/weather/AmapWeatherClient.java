package com.example.spring.weather;

import com.example.spring.exception.WeatherServiceException;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;
import java.util.Set;

@Component
public class AmapWeatherClient implements WeatherClient {

    private static final Set<String> MUNICIPALITY_ADCODES = Set.of("110000", "120000", "310000", "500000");

    private final RestClient restClient;
    private final String key;

    public AmapWeatherClient(
            RestClient.Builder builder,
            @Value("${amap.weather.key:}") String key,
            @Value("${amap.weather.base-url:https://restapi.amap.com}") String baseUrl) {
        this.restClient = builder.baseUrl(baseUrl).build();
        this.key = key;
    }

    @Override
    public WeatherResult query(String city) {
        validateConfiguration();
        String cityName = normalizeCityName(city);

        try {
            ResolvedCity resolvedCity = resolveCity(cityName);
            LiveWeather live = queryLiveWeather(resolvedCity.adcode());
            List<WeatherResult.Forecast> forecasts = queryForecasts(resolvedCity.adcode());

            return new WeatherResult(
                    live.province(),
                    live.city(),
                    live.weather(),
                    live.temperature(),
                    live.winddirection(),
                    live.windpower(),
                    live.humidity(),
                    live.reporttime(),
                    forecasts);
        } catch (WeatherServiceException exception) {
            throw exception;
        } catch (RestClientException exception) {
            throw new WeatherServiceException("高德天气服务暂时不可用");
        }
    }

    private ResolvedCity resolveCity(String cityName) {
        DistrictResponse response = restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/v3/config/district")
                        .queryParam("key", key)
                        .queryParam("keywords", cityName)
                        .queryParam("subdistrict", "0")
                        .queryParam("extensions", "base")
                        .queryParam("output", "JSON")
                        .build())
                .retrieve()
                .body(DistrictResponse.class);

        validateAmapResponse(response, "高德城市解析服务错误");
        List<District> districts = response.districts() == null ? List.of() : response.districts();
        if (districts.isEmpty()) {
            throw new WeatherServiceException("未找到城市：" + cityName + "，目前高德天气仅支持中国城市");
        }

        return districts.stream()
                .filter(this::isMunicipality)
                .findFirst()
                .or(() -> districts.stream()
                        .filter(district -> "city".equals(district.level()))
                        .filter(district -> matchesCityName(cityName, district.name()))
                        .findFirst())
                .or(() -> districts.stream()
                        .filter(district -> "city".equals(district.level()))
                        .findFirst())
                .map(district -> new ResolvedCity(district.name(), district.adcode()))
                .orElseThrow(() -> cityLevelException(cityName, districts));
    }

    private LiveWeather queryLiveWeather(String adcode) {
        LiveWeatherResponse response = restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/v3/weather/weatherInfo")
                        .queryParam("key", key)
                        .queryParam("city", adcode)
                        .queryParam("extensions", "base")
                        .queryParam("output", "JSON")
                        .build())
                .retrieve()
                .body(LiveWeatherResponse.class);

        validateAmapResponse(response, "高德天气服务错误");
        List<LiveWeather> lives = response.lives() == null ? List.of() : response.lives();
        if (lives.isEmpty()) {
            throw new WeatherServiceException("高德天气服务未返回实时天气");
        }
        return lives.get(0);
    }

    private List<WeatherResult.Forecast> queryForecasts(String adcode) {
        ForecastResponse response = restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/v3/weather/weatherInfo")
                        .queryParam("key", key)
                        .queryParam("city", adcode)
                        .queryParam("extensions", "all")
                        .queryParam("output", "JSON")
                        .build())
                .retrieve()
                .body(ForecastResponse.class);

        validateAmapResponse(response, "高德天气预报服务错误");
        List<ForecastBlock> forecasts = response.forecasts() == null ? List.of() : response.forecasts();
        if (forecasts.isEmpty() || forecasts.get(0).casts() == null) {
            return List.of();
        }
        return forecasts.get(0).casts().stream()
                .map(cast -> new WeatherResult.Forecast(
                        cast.date(),
                        cast.week(),
                        cast.dayweather(),
                        cast.nightweather(),
                        cast.daytemp(),
                        cast.nighttemp(),
                        cast.daywind(),
                        cast.nightwind(),
                        cast.daypower(),
                        cast.nightpower()))
                .toList();
    }

    private void validateConfiguration() {
        if (key == null || key.isBlank()) {
            throw new WeatherServiceException("未配置高德天气 KEY");
        }
    }

    private String normalizeCityName(String city) {
        if (city == null || city.isBlank()) {
            throw new WeatherServiceException("缺少城市名，用法：weather <城市名>");
        }
        return city.strip();
    }

    private void validateAmapResponse(AmapResponse response, String prefix) {
        if (response == null) {
            throw new WeatherServiceException(prefix + "：服务未返回数据");
        }
        if (!"1".equals(response.status())) {
            String message = response.info() == null || response.info().isBlank() ? "未知错误" : response.info();
            throw new WeatherServiceException(prefix + "：" + message);
        }
    }

    private boolean isMunicipality(District district) {
        return "province".equals(district.level()) && MUNICIPALITY_ADCODES.contains(district.adcode());
    }

    private boolean matchesCityName(String input, String candidate) {
        if (candidate == null || candidate.isBlank()) {
            return false;
        }
        String normalizedInput = stripCitySuffix(input);
        String normalizedCandidate = stripCitySuffix(candidate);
        return normalizedCandidate.equals(normalizedInput);
    }

    private String stripCitySuffix(String value) {
        return value != null && value.endsWith("市") ? value.substring(0, value.length() - 1) : value;
    }

    private WeatherServiceException cityLevelException(String cityName, List<District> districts) {
        boolean hasProvince = districts.stream().anyMatch(district -> "province".equals(district.level()));
        if (hasProvince) {
            return new WeatherServiceException("请输入城市名，不要输入省份：" + cityName);
        }
        boolean hasDistrict = districts.stream().anyMatch(district -> "district".equals(district.level()));
        if (hasDistrict) {
            return new WeatherServiceException("请输入城市名，不要输入区县：" + cityName);
        }
        return new WeatherServiceException("未找到城市：" + cityName + "，目前高德天气仅支持中国城市");
    }

    private interface AmapResponse {
        String status();

        String info();
    }

    private record ResolvedCity(String name, String adcode) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record DistrictResponse(String status, String info, List<District> districts) implements AmapResponse {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record District(String name, String level, String adcode) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record LiveWeatherResponse(String status, String info, List<LiveWeather> lives) implements AmapResponse {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record LiveWeather(
            String province,
            String city,
            String weather,
            String temperature,
            String winddirection,
            String windpower,
            String humidity,
            String reporttime) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ForecastResponse(String status, String info, List<ForecastBlock> forecasts) implements AmapResponse {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ForecastBlock(List<ForecastCast> casts) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ForecastCast(
            String date,
            String week,
            String dayweather,
            String nightweather,
            String daytemp,
            String nighttemp,
            String daywind,
            String nightwind,
            String daypower,
            String nightpower) {
    }
}
