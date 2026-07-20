package com.example.spring.agent;

import com.example.spring.weather.WeatherException;
import com.example.spring.weather.WeatherFormatter;
import com.example.spring.weather.WeatherService;
import com.example.spring.time.DateIntent;
import com.example.spring.time.TemporalExpressionParser;
import com.example.spring.time.TimeContextProvider;
import java.time.LocalDate;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class WeatherAgent implements ModuleAgent {
    private static final Pattern FOLLOW_UP_FILLER = Pattern.compile(
        "^(那|那么|那边|呢|那呢|怎么样|如何|什么情况|情况)$");
    private static final Pattern NOISE = Pattern.compile(
        "(?i)(请|帮我|帮忙|查询|查一下|查查|告诉我|现在|当前|实时|今天|今日|当地|的|"
            + "天气预报|天气|气温|温度|多少度|湿度|风速|怎么样|如何|情况|weather|temperature|[？?！!。,.，])");
    private final WeatherService weatherService;
    private final WeatherFormatter formatter;
    private final TemporalExpressionParser temporalParser;
    private final TimeContextProvider timeContextProvider;

    public WeatherAgent(
        WeatherService weatherService,
        WeatherFormatter formatter,
        TemporalExpressionParser temporalParser,
        TimeContextProvider timeContextProvider) {
        this.weatherService = weatherService;
        this.formatter = formatter;
        this.temporalParser = temporalParser;
        this.timeContextProvider = timeContextProvider;
    }

    @Override
    public AgentType type() {
        return AgentType.WEATHER;
    }

    @Override
    public AgentResponse execute(AgentRequest request) throws Exception {
        LocalDate currentDate = timeContextProvider.now().toLocalDate();
        DateIntent dateIntent = temporalParser.parse(request.text(), currentDate);
        String region = extractRegion(temporalParser.removeDateExpressions(request.text()));
        if (region.isBlank()) {
            region = previousRegion(request);
        }
        if (region.isBlank()) {
            return AgentResponse.text("请告诉我需要查询的城市、区或县，例如：江苏无锡滨湖区天气。" );
        }
        try {
            return AgentResponse.text(formatter.format(weatherService.weather(
                region,
                currentDate,
                dateIntent.targetDate(),
                dateIntent.expression(),
                dateIntent.currentMoment())));
        } catch (WeatherException exception) {
            return AgentResponse.text("天气查询失败：" + exception.getMessage());
        }
    }

    private String previousRegion(AgentRequest request) {
        for (int index = request.history().size() - 1; index >= 0; index--) {
            var message = request.history().get(index);
            if (!"user".equalsIgnoreCase(message.role())) {
                continue;
            }
            String candidate = extractRegion(
                temporalParser.removeDateExpressions(message.content()));
            if (!candidate.isBlank()) {
                return candidate;
            }
        }
        return "";
    }

    static String extractRegion(String text) {
        if (text == null) {
            return "";
        }
        String region = NOISE.matcher(text).replaceAll("").replaceAll("\\s+", "").trim();
        return FOLLOW_UP_FILLER.matcher(region).matches() ? "" : region;
    }
}
