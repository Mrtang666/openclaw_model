package com.example.spring.memory;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.spring.agent.AgentRequest;
import com.example.spring.agent.AgentResponse;
import com.example.spring.agent.AgentType;
import com.example.spring.agent.ImageAsset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ConversationMemoryServiceTests {
    @TempDir
    Path tempDirectory;

    @Test
    void persistsTextAndResolvesLatestHistoricalImage() throws Exception {
        MemoryProperties properties = properties();
        ConversationMemoryService service = new ConversationMemoryService(properties);
        service.afterPropertiesSet();

        AgentRequest request = new AgentRequest(
            "user-1",
            100L,
            "这是我的产品图片",
            List.of(new ImageAsset(new byte[] {1, 2, 3}, "image/png", "product.png")),
            1);
        service.rememberUserRequest(request);
        service.rememberAgentResult(
            AgentType.VISION,
            request,
            AgentResponse.text("一只白色水杯，正面有蓝色标志"));
        service.rememberAgentResult(
            AgentType.CHAT,
            request,
            AgentResponse.text("我已经记住这张产品图。"));

        ConversationMemoryService restarted = new ConversationMemoryService(properties);
        restarted.afterPropertiesSet();
        AgentRequest prepared = restarted.prepare(new AgentRequest(
            "user-1", 101L, "把上一张图片的背景改成夜景", List.of(), 0));

        assertThat(prepared.history())
            .extracting(MemoryMessage::content)
            .anyMatch(value -> value.contains("白色水杯"))
            .anyMatch(value -> value.contains("已经记住"));
        assertThat(prepared.referencedImages()).hasSize(1);
        assertThat(prepared.referencedImages().get(0).data()).containsExactly(1, 2, 3);

        AgentRequest activeEditReply = restarted.attachLatestImage(new AgentRequest(
            "user-1", 102L, "科技感，方形", List.of(), 0));
        assertThat(activeEditReply.referencedImages()).hasSize(1);
        assertThat(activeEditReply.referencedImages().get(0).fileName())
            .isEqualTo("product.png");

        AgentRequest semanticEdit = restarted.prepare(new AgentRequest(
            "user-1", 103L, "去除里面的人群", List.of(), 0));
        assertThat(semanticEdit.referencedImages()).hasSize(1);
    }

    @Test
    void prunesOldImagesToKeepStartupAndStorageBounded() throws Exception {
        MemoryProperties properties = properties();
        properties.setMaxImagesPerUser(2);
        properties.setMaxImageBytesPerUser(6);
        ConversationMemoryService service = new ConversationMemoryService(properties);
        service.afterPropertiesSet();

        for (long messageId = 1; messageId <= 3; messageId++) {
            service.rememberUserRequest(new AgentRequest(
                "user-2",
                messageId,
                "图片 " + messageId,
                List.of(new ImageAsset(
                    new byte[] {(byte) messageId, 2, 3},
                    "image/png",
                    "image-" + messageId + ".png")),
                1));
        }

        long fileCount;
        try (var paths = Files.walk(tempDirectory.resolve("images"))) {
            fileCount = paths.filter(Files::isRegularFile).count();
        }
        assertThat(fileCount).isEqualTo(2);
    }

    private MemoryProperties properties() {
        MemoryProperties properties = new MemoryProperties();
        properties.setDataDirectory(tempDirectory);
        properties.setMaxEntriesPerUser(20);
        properties.setPromptEntries(10);
        properties.setMaxImagesPerUser(10);
        properties.setMaxImageBytesPerUser(1024);
        return properties;
    }
}
