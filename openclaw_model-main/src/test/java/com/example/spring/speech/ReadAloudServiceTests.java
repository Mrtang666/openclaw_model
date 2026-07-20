package com.example.spring.speech;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.spring.agent.AgentRequest;
import com.example.spring.agent.AgentResponse;
import com.example.spring.agent.AgentType;
import com.example.spring.memory.ConversationMemoryService;
import com.example.spring.memory.MemoryProperties;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ReadAloudServiceTests {
    @TempDir
    Path temporaryDirectory;

    private ConversationMemoryService memoryService;
    private ReadAloudService service;

    @BeforeEach
    void setUp() throws Exception {
        MemoryProperties properties = new MemoryProperties();
        properties.setDataDirectory(temporaryDirectory);
        memoryService = new ConversationMemoryService(properties);
        memoryService.afterPropertiesSet();
        service = new ReadAloudService(memoryService);
    }

    @Test
    void readsExplicitInlineText() {
        ReadAloudRequest request = service.resolve(
            "user-1", "请朗读下面的文字：今天是个好天气");

        assertThat(request.requested()).isTrue();
        assertThat(request.targetText()).isEqualTo("今天是个好天气");
    }

    @Test
    void resolvesTheLatestAssistantReplyForHistoryReferences() {
        AgentRequest original = new AgentRequest(
            "user-2", 1L, "讲一个笑话", List.of(), 0);
        memoryService.rememberAgentResult(
            AgentType.CHAT, original, AgentResponse.text("这是上一条完整笑话。"));

        ReadAloudRequest request = service.resolve("user-2", "帮我朗读上面的笑话");

        assertThat(request.targetText()).isEqualTo("这是上一条完整笑话。");
    }

    @Test
    void asksForContentWhenNoHistoryExists() {
        ReadAloudRequest request = service.resolve("user-3", "朗读刚才的内容");

        assertThat(request.requested()).isTrue();
        assertThat(request.targetText()).isBlank();
        assertThat(request.errorReply()).contains("没有找到");
    }

    @Test
    void ignoresNegativeAndOrdinaryConversation() {
        assertThat(service.resolve("user-4", "不要朗读，只要文字").requested()).isFalse();
        assertThat(service.resolve("user-4", "查询今天的天气").requested()).isFalse();
        assertThat(service.resolve("user-4", "什么是朗读功能").requested()).isFalse();
    }
}
