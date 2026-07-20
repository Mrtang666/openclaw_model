package com.example.spring.tool;


/**
 * CLI 工具封装层，负责统一封装本地工具能力。
 */
import com.example.spring.weather.model.WeatherResult;
import com.example.spring.weather.service.WeatherService;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class WeatherTool implements AgentTool {

    private final WeatherService weatherService;

    public WeatherTool(WeatherService weatherService) {
        this.weatherService = weatherService;
    }

    @Override
    public String name() {
        return "weather";
    }

    @Override
    public String execute(Map<String, String> arguments) {
        WeatherResult result = weatherService.query(arguments.get("city"));

        StringBuilder output = new StringBuilder();
        appendLine(output, "省份：%s", result.province());
        appendLine(output, "城市：%s", result.city());
        appendLine(output, "天气：%s", result.weather());
        appendLine(output, "温度：%s°C", result.temperature());
        appendLine(output, "湿度：%s%%", result.humidity());
        appendLine(output, "风向：%s", windDirection(result.windDirection()));
        appendLine(output, "风力：%s", windPower(result.windPower()));
        appendLine(output, "发布时间：%s", result.reportTime());

        if (!result.forecasts().isEmpty()) {
            output.append("未来天气：").append(System.lineSeparator());
            for (WeatherResult.Forecast forecast : result.forecasts()) {
                output.append("%s %s：白天 %s %s°C %s风 %s；夜间 %s %s°C %s风 %s".formatted(
                        forecast.date(),
                        weekName(forecast.week()),
                        forecast.dayWeather(),
                        forecast.dayTemperature(),
                        forecast.dayWind(),
                        windPower(forecast.dayPower()),
                        forecast.nightWeather(),
                        forecast.nightTemperature(),
                        forecast.nightWind(),
                        windPower(forecast.nightPower())))
                        .append(System.lineSeparator());
            }
        }

        return output.toString().stripTrailing();
    }

    private static void appendLine(StringBuilder output, String template, String value) {
        output.append(template.formatted(value == null || value.isBlank() ? "未知" : value))
                .append(System.lineSeparator());
    }

    private static String windDirection(String value) {
        if (value == null || value.isBlank()) {
            return "未知";
        }
        return value.endsWith("风") ? value : value + "风";
    }

    private static String windPower(String value) {
        if (value == null || value.isBlank()) {
            return "未知";
        }
        return value.endsWith("级") ? value : value + "级";
    }

    private static String weekName(String week) {
        return switch (week) {
            case "1" -> "周一";
            case "2" -> "周二";
            case "3" -> "周三";
            case "4" -> "周四";
            case "5" -> "周五";
            case "6" -> "周六";
            case "7" -> "周日";
            default -> "周" + (week == null || week.isBlank() ? "未知" : week);
        };
    }
}

