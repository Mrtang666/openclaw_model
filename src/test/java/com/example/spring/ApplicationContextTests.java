package com.example.spring;

import com.example.spring.cli.command.core.CommandRegistry;
import com.example.spring.chat.DashScopeChatClient;
import com.example.spring.wechat.image.generation.client.DashScopeImageGenerationClient;
import com.example.spring.wechat.voice.synthesis.service.DefaultVoiceSynthesisService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(args = "/status")
@ActiveProfiles("test")
class ApplicationContextTests {

    @Autowired
    private CommandRegistry commandRegistry;

    @Autowired
    private Environment environment;

    @Autowired
    private DashScopeChatClient chatClient;

    @Autowired
    private DashScopeImageGenerationClient imageGenerationClient;

    @Autowired
    private DefaultVoiceSynthesisService voiceSynthesisService;

    @Test
    void contextLoadsWithCliRunner() {
    }

    @Test
    void cliDoesNotRegisterImageGenerationCommand() {
        assertThat(commandRegistry.find("image")).isEmpty();
    }

    @Test
    void usesConfiguredDashScopeModels() {
        assertThat(environment.getProperty("openclaw.dashscope.model")).isEqualTo("qwen3.7-max-2026-06-08");
        assertThat(environment.getProperty("openclaw.dashscope.image-model")).isEqualTo("qwen-image-2.0-pro");
        assertThat(environment.getProperty("openclaw.dashscope.tts-model")).isEqualTo("qwen3-tts-flash");
        assertThat(ReflectionTestUtils.getField(chatClient, "model")).isEqualTo("qwen3.7-max-2026-06-08");
        assertThat(ReflectionTestUtils.getField(imageGenerationClient, "model")).isEqualTo("qwen-image-2.0-pro");
        assertThat(ReflectionTestUtils.getField(voiceSynthesisService, "model")).isEqualTo("qwen3-tts-flash");
    }
}
