package com.example.spring.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 运行时配置检查器。
 *
 * <p>应用启动完成后输出关键配置状态，帮助多人协作时快速发现 `.env` 缺项。
 * 日志只展示 Host、模型名和 Key 的脱敏状态，不输出真实密钥。</p>
 */
@Component
public class OpenClawConfigReporter {

    private static final Logger log = LoggerFactory.getLogger(OpenClawConfigReporter.class);

    private final Environment environment;

    public OpenClawConfigReporter(Environment environment) {
        this.environment = environment;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void report() {
        log.info(
                "OpenClaw 配置检查：chatHost={}, chatModel={}, apiKey={}, imageHost={}, imageModel={}, "
                        + "ttsHost={}, ttsModel={}, webSearchProvider={}, webSearchEndpointHost={}, mysql={}, qdrant={}",
                SecretMasker.host(value("dashscope.base-url")),
                value("dashscope.model"),
                SecretMasker.mask(value("dashscope.api-key")),
                SecretMasker.host(value("dashscope.image-base-url")),
                value("dashscope.image-model"),
                SecretMasker.host(value("dashscope.tts-base-url")),
                value("dashscope.tts-model"),
                value("web.search.provider"),
                SecretMasker.host(value("web.search.endpoint")),
                SecretMasker.present(value("spring.datasource.url")),
                value("qdrant.host"));

        missingRequiredConfig().forEach(name ->
                log.warn("OpenClaw 配置缺失：{}。请在 .env 中补充，或确认当前功能不需要它。", name));
    }

    private List<String> missingRequiredConfig() {
        return List.of(
                        missing("DASHSCOPE_API_KEY", "dashscope.api-key"),
                        missing("DASHSCOPE_BASE_URL", "dashscope.base-url"),
                        missing("DASHSCOPE_IMAGE_BASE_URL", "dashscope.image-base-url"),
                        missing("DASHSCOPE_TTS_BASE_URL", "dashscope.tts-base-url"))
                .stream()
                .filter(value -> !value.isBlank())
                .toList();
    }

    private String missing(String envName, String propertyName) {
        return value(propertyName).isBlank() ? envName : "";
    }

    private String value(String propertyName) {
        return environment.getProperty(propertyName, "").strip();
    }
}
