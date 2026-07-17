package com.example.spring.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.spring.agent.ImageAsset;
import com.example.spring.bailian.BailianChatService;
import com.example.spring.bailian.BailianImageGenerationService;
import com.example.spring.bailian.BailianImageEditService;
import com.example.spring.bailian.BailianProperties;
import com.example.spring.bailian.BailianVisionService;
import com.example.spring.weather.WeatherModels.WeatherReport;
import com.example.spring.weather.WeatherProperties;
import com.example.spring.weather.WeatherService;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.List;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
    "ilink.bot.enabled=false",
    "memory.data-directory=target/live-test-memory"
})
@EnabledIfSystemProperty(named = "live.api.tests", matches = "true")
class LiveApiIntegrationTests {
    @Autowired
    private BailianProperties bailianProperties;

    @Autowired
    private WeatherProperties weatherProperties;

    @Autowired
    private BailianChatService chatService;

    @Autowired
    private BailianVisionService visionService;

    @Autowired
    private BailianImageGenerationService imageGenerationService;

    @Autowired
    private BailianImageEditService imageEditService;

    @Autowired
    private WeatherService weatherService;

    @Test
    void verifiesConfiguredExternalServicesEndToEnd() throws Exception {
        assertThat(bailianProperties.isConfigured()).isTrue();
        assertThat(weatherProperties.isConfigured()).isTrue();

        String chatReply = chatService.chat(
            "live-smoke-test",
            "这是连接测试，请只回复四个字：连接成功");
        assertThat(chatReply).isNotBlank();

        WeatherReport weather = weatherService.currentWeather("北京市朝阳区");
        assertThat(weather.location().name()).isNotBlank();
        assertThat(weather.description()).isNotBlank();

        String visionReply = visionService.analyze(
            "请简短说明图片中的主要颜色。",
            List.of(testImage()));
        assertThat(visionReply).isNotBlank();

        ImageAsset generated = imageGenerationService.generate(
            "一张简洁的蓝天白云插画，画面干净，不包含文字");
        assertThat(generated.data()).isNotEmpty();
        assertThat(generated.mediaType()).startsWith("image/");

        ImageAsset edited = imageEditService.edit(
            "保留白色圆形，把蓝色背景修改成深色夜空。",
            List.of(testImage()));
        assertThat(edited.data()).isNotEmpty();
        assertThat(edited.mediaType()).startsWith("image/");
    }

    private static ImageAsset testImage() throws Exception {
        BufferedImage image = new BufferedImage(128, 128, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setColor(new Color(32, 120, 220));
            graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
            graphics.setColor(Color.WHITE);
            graphics.fillOval(36, 36, 56, 56);
        } finally {
            graphics.dispose();
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        return new ImageAsset(output.toByteArray(), "image/png", "live-test.png");
    }
}
