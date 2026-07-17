package com.example.spring.agent;

import com.example.spring.weather.WeatherException;
import com.example.spring.weather.WeatherFormatter;
import com.example.spring.weather.WeatherService;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class WeatherAgent implements ModuleAgent {
    private static final Pattern NOISE = Pattern.compile(
        "(?i)(请|帮我|帮忙|查询|查一下|查查|告诉我|现在|当前|实时|今天|今日|当地|的|"
            + "天气预报|天气|气温|温度|多少度|湿度|风速|怎么样|如何|情况|weather|temperature|[？?！!。,.，])");
    private final WeatherService weatherService;
    private final WeatherFormatter formatter;

    public WeatherAgent(WeatherService weatherService, WeatherFormatter formatter) {
        this.weatherService = weatherService;
        this.formatter = formatter;
    }

    @Override
    public AgentType type() {
        return AgentType.WEATHER;
    }

    @Override
    public AgentResponse execute(AgentRequest request) throws Exception {
        String region = extractRegion(request.text());
        if (region.isBlank()) {
            return AgentResponse.text("请告诉我需要查询的城市、区或县，例如：江苏无锡滨湖区天气。" );
        }
        try {
            return AgentResponse.text(formatter.format(weatherService.currentWeather(region)));
        } catch (WeatherException exception) {
            return AgentResponse.text("天气查询失败：" + exception.getMessage());
        }
    }

    static String extractRegion(String text) {
        if (text == null) {
            return "";
        }
        return NOISE.matcher(text).replaceAll("").replaceAll("\\s+", "").trim();
    }
}
