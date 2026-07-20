package com.example.spring.wechat.conversation.tools;


/**
 * CLI 工具封装层，负责统一封装本地工具能力。
 */
import com.example.spring.chat.ChatService;
import com.example.spring.exception.WeatherServiceException;
import com.example.spring.weather.model.WeatherResult;
import com.example.spring.weather.service.WeatherService;
import com.example.spring.wechat.bot.WechatReply;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class WeatherWechatTool implements WechatTool {

    private static final Logger log = LoggerFactory.getLogger(WeatherWechatTool.class);

    private final ChatService chatService;
    private final WeatherService weatherService;

    public WeatherWechatTool(ChatService chatService, WeatherService weatherService) {
        this.chatService = chatService;
        this.weatherService = weatherService;
    }

    @Override
    public String name() {
        return "weather";
    }

    @Override
    public String description() {
        return "查询城市天气，并根据天气数据生成自然语言建议";
    }

    @Override
    public List<String> arguments() {
        return List.of("city", "question");
    }

    @Override
    public WechatReply execute(WechatToolRequest request) {
        String city = firstNonBlank(request.argument("city"), request.argument("location"));
        if (city.isBlank()) {
            return WechatReply.text("你想查哪个城市的天气？");
        }

        try {
            WeatherResult weather = weatherService.query(city);
            StringBuilder output = new StringBuilder();
            chatService.streamReply(buildWeatherPrompt(request, weather), output::append);
            return WechatReply.text(output.toString().strip());
        } catch (WeatherServiceException exception) {
            log.warn("微信天气工具查询失败，city={}, error={}", city, rootMessage(exception));
            return WechatReply.text("天气查询失败：" + rootMessage(exception));
        }
    }

    private String buildWeatherPrompt(WechatToolRequest request, WeatherResult weather) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("你是微信聊天助手，请结合天气数据回答用户问题，并给出实用建议。").append('\n')
                .append("要求：只能依据下面天气数据，不要编造；语气自然，适合微信聊天。").append('\n')
                .append("最近对话：").append('\n')
                .append(request.historyText()).append('\n')
                .append("当前用户需求：").append(firstNonBlank(request.argument("question"), request.userText())).append('\n')
                .append("天气数据：").append('\n')
                .append("省份：").append(valueOrUnknown(weather.province())).append('\n')
                .append("城市：").append(valueOrUnknown(weather.city())).append('\n')
                .append("天气：").append(valueOrUnknown(weather.weather())).append('\n')
                .append("温度：").append(valueOrUnknown(weather.temperature())).append("℃\n")
                .append("湿度：").append(valueOrUnknown(weather.humidity())).append("%\n")
                .append("风向：").append(valueOrUnknown(weather.windDirection())).append('\n')
                .append("风力：").append(valueOrUnknown(weather.windPower())).append('\n')
                .append("发布时间：").append(valueOrUnknown(weather.reportTime())).append('\n');

        if (!weather.forecasts().isEmpty()) {
            prompt.append("未来天气：").append('\n');
            for (WeatherResult.Forecast forecast : weather.forecasts()) {
                prompt.append("- ")
                        .append(forecast.date())
                        .append("：白天 ")
                        .append(valueOrUnknown(forecast.dayWeather()))
                        .append(' ')
                        .append(valueOrUnknown(forecast.dayTemperature()))
                        .append("℃；夜间 ")
                        .append(valueOrUnknown(forecast.nightWeather()))
                        .append(' ')
                        .append(valueOrUnknown(forecast.nightTemperature()))
                        .append("℃")
                        .append('\n');
            }
        }
        return prompt.toString();
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.strip();
            }
        }
        return "";
    }

    private String valueOrUnknown(String value) {
        return value == null || value.isBlank() ? "未知" : value;
    }

    private String rootMessage(Throwable exception) {
        Throwable current = exception;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }
}

