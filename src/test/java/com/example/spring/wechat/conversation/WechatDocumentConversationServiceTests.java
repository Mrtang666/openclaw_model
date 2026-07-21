package com.example.spring.wechat.conversation;

import com.example.spring.chat.ChatService;
import com.example.spring.tool.protocol.ConversationIntentDecision;
import com.example.spring.tool.protocol.ToolCall;
import com.example.spring.tool.protocol.ToolCallPlanner;
import com.example.spring.wechat.bot.WechatReply;
import com.example.spring.wechat.conversation.intent.WeatherIntentParser;
import com.example.spring.wechat.conversation.tools.DocumentAnalysisWechatTool;
import com.example.spring.wechat.conversation.tools.DocumentGenerationWechatTool;
import com.example.spring.wechat.conversation.tools.WechatToolRegistry;
import com.example.spring.wechat.document.service.DefaultDocumentGenerationService;
import com.example.spring.wechat.document.service.DocumentParseService;
import com.example.spring.wechat.model.WechatIncomingFile;
import com.example.spring.wechat.model.WechatIncomingMessage;
import com.example.spring.weather.service.WeatherService;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WechatDocumentConversationServiceTests {

    @Test
    void asksForRequirementWhenUserOnlySendsAFile() {
        WechatConversationService service = new WechatConversationService(
                mock(ChatService.class),
                mock(WeatherService.class));
        WechatIncomingFile file = new WechatIncomingFile(
                "wechat://1/file/1",
                "需求.md",
                "text/markdown",
                "# 项目目标\n\n实现微信端文档解析工具。".getBytes(StandardCharsets.UTF_8),
                null,
                null,
                null);

        WechatReply reply = service.handleWechat(new WechatIncomingMessage(
                "user-1",
                "",
                List.of(),
                List.of(),
                List.of(file)));

        assertThat(reply.text()).contains("我已经收到文件《需求.md》", "总结全文", "生成新的 Word / PDF / Markdown 文档");
    }

    @Test
    void sendsOnlyGeneratedDocumentWhenPlanAnalyzesThenGeneratesDocument() {
        ChatService chatService = mock(ChatService.class);
        when(chatService.reply(anyString())).thenReturn("""
                计组实验 4 实验报告总结

                本次实验主要总结 cache 直接相联映射的实现过程和实验结论。
                """);

        ToolCallPlanner planner = mock(ToolCallPlanner.class);
        when(planner.planDecision(anyString(), anyList(), anyString())).thenReturn(Optional.of(
                new ConversationIntentDecision(
                        List.of(
                                new ToolCall("document_analysis", Map.of("operation", "summary")),
                                new ToolCall("document_generation", Map.of(
                                        "format", "pdf",
                                        "title", "计组实验4实验报告总结",
                                        "content", "上一步总结的结果"))),
                        false,
                        "")));

        WechatToolRegistry registry = new WechatToolRegistry(List.of(
                new DocumentAnalysisWechatTool(DocumentParseService.defaultService()),
                new DocumentGenerationWechatTool(chatService, new DefaultDocumentGenerationService())));

        WechatConversationService service = new WechatConversationService(
                chatService,
                mock(WeatherService.class),
                null,
                null,
                null,
                new WeatherIntentParser(),
                planner,
                registry);

        WechatIncomingFile file = new WechatIncomingFile(
                "wechat://1/file/1",
                "计组实验4实验报告.docx",
                "text/markdown",
                "# Cache 实验\n\n实验内容包括 cache 直接相联映射、地址划分和命中判断。".getBytes(StandardCharsets.UTF_8),
                null,
                null,
                null);
        service.handleWechat(new WechatIncomingMessage("user-1", "", List.of(), List.of(), List.of(file)));

        WechatReply reply = service.handleWechat(new WechatIncomingMessage(
                "user-1",
                "总结一下然后给我以 pdf 的形式发给我"));

        assertThat(reply.parts()).hasSize(1);
        assertThat(reply.parts().get(0).hasFile()).isTrue();
        assertThat(reply.parts().get(0).text()).doesNotContain("可用文件上下文", "最近文件");
        assertThat(reply.parts().get(0).file().fileName()).endsWith(".pdf");
    }
}
