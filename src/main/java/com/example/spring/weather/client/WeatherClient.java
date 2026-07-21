package com.example.spring.weather.client;
import com.example.spring.weather.model.WeatherResult;


/**
 * 天气查询模块组件，负责调用天气接口并整理结果。
 */
public interface WeatherClient {

    WeatherResult query(String city);
}


