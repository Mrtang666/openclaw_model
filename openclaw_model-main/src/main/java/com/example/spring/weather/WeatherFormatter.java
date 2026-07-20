package com.example.spring.weather;

import com.example.spring.weather.WeatherModels.WeatherReport;
import com.example.spring.weather.WeatherModels.WeatherAnswer;
import com.example.spring.weather.WeatherModels.DailyForecastReport;
import java.time.format.DateTimeFormatter;
import org.springframework.stereotype.Component;

@Component
public class WeatherFormatter {
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy年M月d日 EEEE");

    public String format(WeatherAnswer answer) {
        StringBuilder result = new StringBuilder();
        result.append("查询日期：")
            .append(DATE.format(answer.targetDate()));
        if (answer.dateExpression() != null && !answer.dateExpression().isBlank()) {
            result.append("（").append(answer.dateExpression()).append("）");
        }
        if (answer.current() != null) {
            result.append('\n').append(format(answer.current()));
        }
        if (answer.forecast() != null) {
            result.append('\n').append(formatForecast(answer.forecast()));
        }
        return result.toString().strip();
    }

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

    private String formatForecast(DailyForecastReport report) {
        return """
            地区：%s
            最高/最低温度：%.1f°C / %.1f°C
            白天/夜间：%s / %s
            湿度：%d%%
            风向风力：%s %s级，%.1f km/h
            降水量：%.1f mm
            紫外线指数：%s
            预报更新时间：%s
            """.formatted(
                report.location().displayName(),
                report.maximumTemperature(),
                report.minimumTemperature(),
                valueOrUnknown(report.daytimeDescription()),
                valueOrUnknown(report.nighttimeDescription()),
                report.humidity(),
                valueOrUnknown(report.windDirection()),
                valueOrUnknown(report.windScale()),
                report.windSpeed(),
                report.precipitation(),
                valueOrUnknown(report.uvIndex()),
                valueOrUnknown(report.updateTime())).strip();
    }

    private static String valueOrUnknown(String value) {
        return value == null || value.isBlank() ? "未知" : value;
    }
}
