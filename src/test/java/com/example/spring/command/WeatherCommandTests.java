package com.example.spring.command;

import com.example.spring.tool.ToolRegistry;
import com.example.spring.tool.WeatherTool;
import com.example.spring.weather.WeatherClient;
import com.example.spring.weather.WeatherResult;
import com.example.spring.weather.WeatherService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class WeatherCommandTests {

    @Test
    void returnsErrorWhenCityIsMissing() {
        WeatherCommand command = createCommand(city -> sampleResult(city));
        CommandDispatcher dispatcher = new CommandDispatcher(new CommandRegistry(List.of(command)));

        assertThat(dispatcher.dispatch("weather"))
                .isEqualTo("错误：缺少城市名，用法：weather <城市名>");
    }

    @Test
    void printsWeatherReturnedByClient() {
        WeatherCommand command = createCommand(city -> sampleResult(city));

        assertThat(command.execute(List.of("北京")))
                .contains("城市：北京")
                .contains("省份：测试省")
                .contains("天气：晴")
                .contains("温度：25°C")
                .contains("湿度：55%")
                .contains("风向：东南")
                .contains("风力：3级")
                .contains("发布时间：2026-07-16 11:30:00")
                .contains("未来天气：")
                .contains("2026-07-16 周四：白天 晴 30°C 东南风 1-3级；夜间 多云 22°C 东北风 1-3级");
    }

    private WeatherResult sampleResult(String city) {
        return new WeatherResult(
                "测试省",
                city,
                "晴",
                "25",
                "东南",
                "3",
                "55",
                "2026-07-16 11:30:00",
                List.of(new WeatherResult.Forecast(
                        "2026-07-16", "4", "晴", "多云", "30", "22",
                        "东南", "东北", "1-3", "1-3")));
    }

    private WeatherCommand createCommand(WeatherClient client) {
        WeatherService service = new WeatherService(client);
        ToolRegistry tools = new ToolRegistry(List.of(new WeatherTool(service)));
        return new WeatherCommand(tools);
    }
}
