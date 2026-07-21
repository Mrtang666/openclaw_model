package com.example.spring.weather.service;


/**
 * 天气查询模块组件，负责调用天气接口并整理结果。
 */
import org.springframework.stereotype.Service;
import com.example.spring.weather.client.WeatherClient;
import com.example.spring.weather.model.WeatherResult;

@Service
public class WeatherService {

    private final WeatherClient weatherClient;

    public WeatherService(WeatherClient weatherClient) {
        this.weatherClient = weatherClient;
    }

    public WeatherResult query(String city) {
        return weatherClient.query(city);
    }
}


