package com.example.spring.bailian;

import com.example.spring.weather.WeatherProperties;
import java.net.URI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class BailianConfigurationLogger implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(BailianConfigurationLogger.class);
    private final BailianProperties bailian;
    private final WeatherProperties weather;

    public BailianConfigurationLogger(
        BailianProperties bailian,
        WeatherProperties weather) {
        this.bailian = bailian;
        this.weather = weather;
    }

    @Override
    public void run(ApplicationArguments args) {
        log.info(
            "百炼外部配置：endpointHost={}，chatModel={}，visionModel={}，imageModel={}，imageEditModel={}，apiKeyConfigured={}",
            host(bailian.getCompatibleBaseUrl()),
            bailian.getChatModel(),
            bailian.getVisionModel(),
            bailian.getImageModel(),
            bailian.getImageEditModel(),
            bailian.isConfigured());
        log.info(
            "和风天气外部配置：endpointHost={}，apiKeyConfigured={}",
            host(weather.getApiHost()),
            weather.isConfigured());
    }

    private static String host(String value) {
        try {
            String normalized = value != null && value.contains("://")
                ? value : "https://" + value;
            String host = URI.create(normalized).getHost();
            return host == null ? "invalid" : host;
        } catch (IllegalArgumentException exception) {
            return "invalid";
        }
    }
}
