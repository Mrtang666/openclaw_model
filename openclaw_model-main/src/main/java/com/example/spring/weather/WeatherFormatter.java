package com.example.spring.weather;

import com.example.spring.weather.WeatherModels.WeatherReport;
import org.springframework.stereotype.Component;

@Component
public class WeatherFormatter {
    public String format(WeatherReport report) {
        return """
            地区：%s
            实时天气：%s
            温度：%.1f°C（体感 %.1f°C）
            湿度：%d%%
            风向风力：%s %s级，%.1f km/h
            更新时间：%s
            """.formatted(
                report.location().displayName(),
                report.description(),
                report.temperature(),
                report.apparentTemperature(),
                report.humidity(),
                valueOrUnknown(report.windDirection()),
                valueOrUnknown(report.windScale()),
                report.windSpeed(),
                valueOrUnknown(report.updateTime())).strip();
    }

    private static String valueOrUnknown(String value) {
        return value == null || value.isBlank() ? "未知" : value;
    }
}
