package com.example.spring.wechat.conversation;

import com.example.spring.chat.ChatService;
import com.example.spring.wechat.image.generation.model.ImageGenerationRequest;
import com.example.spring.wechat.image.generation.model.ImageGenerationResult;
import com.example.spring.wechat.image.generation.service.ImageGenerationService;
import com.example.spring.tool.protocol.ToolCallPlanParser;
import com.example.spring.tool.protocol.ToolCallPlanner;
import com.example.spring.wechat.bot.WechatReply;
import com.example.spring.wechat.image.generation.intent.ImageGenerationIntentParser;
import com.example.spring.wechat.image.service.ImageInputResolver;
import com.example.spring.wechat.memory.model.WechatConversationMemory;
import com.example.spring.wechat.memory.service.WechatMemoryService;
import com.example.spring.wechat.model.WechatIncomingMessage;
import com.example.spring.wechat.conversation.tools.ChatWechatTool;
import com.example.spring.wechat.conversation.tools.ImageGenerationWechatTool;
import com.example.spring.wechat.conversation.tools.VoiceSynthesisWechatTool;
import com.example.spring.wechat.conversation.tools.WeatherWechatTool;
import com.example.spring.wechat.conversation.tools.WechatToolRegistry;
import com.example.spring.wechat.voice.synthesis.model.VoiceSynthesisSegment;
import com.example.spring.wechat.voice.synthesis.service.VoiceSynthesisService;
import com.example.spring.weather.model.WeatherResult;
import com.example.spring.weather.service.WeatherService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import com.example.spring.wechat.conversation.intent.WeatherIntentParser;

class WechatConversationServiceTests {

    @Test
    void routesNormalConversationToChatModel() {
        ChatService chatService = mock(ChatService.class);
        WeatherService weatherService = mock(WeatherService.class);
        doAnswer(invocation -> {
            com.example.spring.agent.ReplyEmitter emitter = invocation.getArgument(1);
            emitter.emit("\u6211\u662f\u4f60\u7684 AI \u52a9\u624b");
            return null;
        }).when(chatService).streamReply(any(), any());
        WechatConversationService service = new WechatConversationService(
                chatService, weatherService, new WeatherIntentParser());

        String reply = service.handle("\u4f60\u662f\u8c01");

        assertThat(reply).isEqualTo("\u6211\u662f\u4f60\u7684 AI \u52a9\u624b");
        verify(chatService).streamReply(org.mockito.ArgumentMatchers.argThat(prompt ->
                prompt.contains("\u4f60\u662f\u8c01")), any());
    }

    @Test
    void includesPersistedConversationSummaryInChatPrompt() {
        ChatService chatService = mock(ChatService.class);
        WeatherService weatherService = mock(WeatherService.class);
        WechatMemoryService memoryService = mock(WechatMemoryService.class);
        WechatConversationMemory memory = WechatConversationMemory.empty(
                10,
                "\u7528\u6237\u6b63\u5728\u51c6\u5907\u676d\u5dde\u65c5\u884c\uff0c\u5e0c\u671b\u83b7\u5f97\u7b80\u6d01\u5efa\u8bae\u3002");
        when(memoryService.memoryFor("summary-user")).thenReturn(memory);
        doAnswer(invocation -> {
            com.example.spring.agent.ReplyEmitter emitter = invocation.getArgument(1);
            emitter.emit("\u660e\u5929\u53ef\u4ee5\u7ee7\u7eed\u5173\u6ce8\u676d\u5dde\u5929\u6c14\u3002");
            return null;
        }).when(chatService).streamReply(anyString(), any());

        WechatConversationService service = new WechatConversationService(
                chatService,
                weatherService,
                null,
                null,
                null,
                new ImageInputResolver(),
                new WeatherIntentParser(),
                new ImageGenerationIntentParser(),
                null,
                null,
                memoryService);

        service.handle("summary-user", "\u63a5\u7740\u8bf4");

        verify(chatService).streamReply(org.mockito.ArgumentMatchers.argThat(prompt ->
                prompt.contains("\u676d\u5dde\u65c5\u884c") && prompt.contains("\u63a5\u7740\u8bf4")), any());
    }

    @Test
    void routesWeatherQuestionToWeatherService() {
        ChatService chatService = mock(ChatService.class);
        WeatherService weatherService = mock(WeatherService.class);
        when(weatherService.query("\u676d\u5dde")).thenReturn(sampleWeatherResult("\u676d\u5dde"));
        doAnswer(invocation -> {
            com.example.spring.agent.ReplyEmitter emitter = invocation.getArgument(1);
            emitter.emit("\u676d\u5dde\u4eca\u5929\u5c0f\u96e8\uff0c\u8bb0\u5f97\u5e26\u4f0e\u3002");
            return null;
        }).when(chatService).streamReply(any(), any());
        WechatConversationService service = new WechatConversationService(
                chatService, weatherService, new WeatherIntentParser());

        String reply = service.handle("\u5e2e\u6211\u67e5\u770b\u4eca\u5929\u676d\u5dde\u7684\u5929\u6c14\u600e\u4e48\u6837");

        assertThat(reply).contains("\u676d\u5dde\u4eca\u5929\u5c0f\u96e8").contains("\u5e26\u4f0e");
        verify(weatherService).query("\u676d\u5dde");
        verify(chatService).streamReply(anyString(), any());
    }

    @Test
    void routesImageGenerationRequestToImageService() {
        ChatService chatService = mock(ChatService.class);
        WeatherService weatherService = mock(WeatherService.class);
        ImageGenerationService imageGenerationService = mock(ImageGenerationService.class);
        when(imageGenerationService.generate(any()))
                .thenReturn(new ImageGenerationResult(
                        "\u4e00\u53ea\u8d5b\u535a\u670b\u514b\u98ce\u683c\u7684\u6a59\u732b",
                        "https://cdn.example.com/generated.png",
                        "PNG".getBytes(),
                        "generated.png",
                        "image/png",
                        null,
                        null));
        WechatConversationService service = new WechatConversationService(
                chatService,
                weatherService,
                null,
                imageGenerationService,
                new WeatherIntentParser());

        WechatReply reply = service.handleWechat(new WechatIncomingMessage("user-1", "\u5e2e\u6211\u753b\u4e00\u53ea\u8d5b\u535a\u670b\u514b\u98ce\u683c\u7684\u6a59\u732b"));

        assertThat(reply.hasImage()).isTrue();
        assertThat(reply.image().imageUrl()).isEqualTo("https://cdn.example.com/generated.png");
        verify(imageGenerationService).generate(org.mockito.ArgumentMatchers.argThat(request ->
                request.prompt().contains("\u8d5b\u535a\u670b\u514b\u98ce\u683c\u7684\u6a59\u732b")));
    }

    @Test
    void asksForMoreImageDetailsWhenImageIntentHasNoUsablePrompt() {
        ChatService chatService = mock(ChatService.class);
        WeatherService weatherService = mock(WeatherService.class);
        ImageGenerationService imageGenerationService = mock(ImageGenerationService.class);
        WechatConversationService service = new WechatConversationService(
                chatService,
                weatherService,
                null,
                imageGenerationService,
                new WeatherIntentParser());

        WechatReply reply = service.handleWechat(new WechatIncomingMessage("user-1", "\u5e2e\u6211\u751f\u6210\u4e00\u5f20\u56fe"));

        assertThat(reply.text()).contains("\u4e3b\u4f53").contains("\u98ce\u683c").contains("\u573a\u666f");
        verify(imageGenerationService, never()).generate(any());
    }

    @Test
    void asksOneCombinedClarificationWhenABundleHasMissingInformation() {
        ChatService chatService = mock(ChatService.class);
        WeatherService weatherService = mock(WeatherService.class);
        ImageGenerationService imageGenerationService = mock(ImageGenerationService.class);
        when(chatService.reply(anyString())).thenReturn("""
                {
                  "needs_clarification": true,
                  "clarification_question": "\u4f60\u60f3\u67e5\u54ea\u4e2a\u57ce\u5e02\u7684\u5929\u6c14\uff1f\u56fe\u7247\u60f3\u8981\u4ec0\u4e48\u98ce\u683c\uff1f",
                  "tasks": [
                    {
                      "tool": "weather",
                      "arguments": {
                        "city": ""
                      }
                    },
                    {
                      "tool": "image_generation",
                      "arguments": {
                        "prompt": "",
                        "optimize_prompt": "true"
                      }
                    }
                  ]
                }
                """);
        WechatToolRegistry toolRegistry = new WechatToolRegistry(List.of(new ChatWechatTool(chatService)));
        ToolCallPlanner planner = new ToolCallPlanner(chatService, new ToolCallPlanParser(new ObjectMapper()));
        WechatConversationService service = new WechatConversationService(
                chatService,
                weatherService,
                null,
                imageGenerationService,
                null,
                new WeatherIntentParser(),
                planner,
                toolRegistry);

        WechatReply reply = service.handleWechat(new WechatIncomingMessage("user-1", "\u5e2e\u6211\u67e5\u5929\u6c14\uff0c\u518d\u751f\u6210\u4e00\u5f20\u56fe"));

        assertThat(reply.hasImage()).isFalse();
        assertThat(reply.text()).contains("\u54ea\u4e2a\u57ce\u5e02").contains("\u4ec0\u4e48\u98ce\u683c");
        verify(weatherService, never()).query(anyString());
        verify(imageGenerationService, never()).generate(any());
        verify(chatService, never()).streamReply(anyString(), any());
    }

    @Test
    void executesStructuredToolPlanInOriginalOrder() {
        ChatService chatService = mock(ChatService.class);
        WeatherService weatherService = mock(WeatherService.class);
        ImageGenerationService imageGenerationService = mock(ImageGenerationService.class);
        when(chatService.reply(anyString()))
                .thenReturn("""
                        {
                          "needs_clarification": false,
                          "clarification_question": "",
                          "tasks": [
                            {
                              "tool": "image_generation",
                              "arguments": {
                                "prompt": "\u4e00\u53ea\u5c0f\u732b\u8eba\u5728\u5e8a\u4e0a",
                                "optimize_prompt": "true"
                              }
                            },
                            {
                              "tool": "weather",
                              "arguments": {
                                "city": "\u676d\u5dde",
                                "question": "\u67e5\u8be2\u676d\u5dde\u4eca\u5929\u7684\u5929\u6c14"
                              }
                            },
                            {
                              "tool": "chat",
                              "arguments": {
                                "message": "\u6839\u636e\u524d\u9762\u7ed3\u679c\u751f\u6210\u4eca\u65e5\u676d\u5dde\u51fa\u884c\u8ba1\u5212"
                              }
                            }
                          ]
                        }
                        """)
                .thenReturn("\u4e00\u53ea\u6a59\u767d\u76f8\u95f4\u7684\u5c0f\u732b\u8eba\u5728\u67d4\u8f6f\u5e8a\u57ab\u4e0a\uff0c\u6696\u8272\u81ea\u7136\u5149\uff0c\u771f\u5b9e\u6444\u5f71\u98ce\u683c\u3002");
        when(imageGenerationService.generate(any()))
                .thenReturn(new ImageGenerationResult(
                        "\u4e00\u53ea\u6a59\u767d\u76f8\u95f4\u7684\u5c0f\u732b\u8eba\u5728\u67d4\u8f6f\u5e8a\u57ab\u4e0a\uff0c\u6696\u8272\u81ea\u7136\u5149\uff0c\u771f\u5b9e\u6444\u5f71\u98ce\u683c\u3002",
                        "https://cdn.example.com/bed-cat.png",
                        "PNG".getBytes(),
                        "bed-cat.png",
                        "image/png",
                        null,
                        null));
        when(weatherService.query("\u676d\u5dde")).thenReturn(sampleWeatherResult("\u676d\u5dde"));
        doAnswer(invocation -> {
            String prompt = invocation.getArgument(0);
            com.example.spring.agent.ReplyEmitter emitter = invocation.getArgument(1);
            if (prompt.contains("\u5929\u6c14\u6570\u636e")) {
                emitter.emit("\u676d\u5dde\u4eca\u5929\u5c0f\u96e8\uff0c\u5e26\u4f0e\u66f4\u7a33\u59a5\u3002");
            } else {
                emitter.emit("\u4eca\u65e5\u676d\u5dde\u51fa\u884c\u8ba1\u5212\uff1a\u4e0a\u5348\u5ba4\u5185\u5b89\u6392\uff0c\u4e2d\u5348\u987a\u8def\u5403\u996d\uff0c\u4e0b\u5348\u53bb\u897f\u6e56\u6563\u6b65\u3002");
            }
            return null;
        }).when(chatService).streamReply(anyString(), any());
        WechatToolRegistry toolRegistry = new WechatToolRegistry(List.of(
                new ImageGenerationWechatTool(chatService, imageGenerationService),
                new WeatherWechatTool(chatService, weatherService),
                new ChatWechatTool(chatService)));
        ToolCallPlanner planner = new ToolCallPlanner(chatService, new ToolCallPlanParser(new ObjectMapper()));
        WechatConversationService service = new WechatConversationService(
                chatService,
                weatherService,
                null,
                imageGenerationService,
                null,
                new WeatherIntentParser(),
                planner,
                toolRegistry);

        WechatReply reply = service.handleWechat(new WechatIncomingMessage(
                "user-1",
                "\u6211\u60f3\u8981\u751f\u6210\u4e00\u5f20\u56fe\u7247\uff0c\u63d0\u793a\u8bcd\u662f\u4e00\u4e2a\u5c0f\u732b\u8eba\u5728\u5e8a\u4e0a\uff0c\u5148\u4f18\u5316\u63d0\u793a\u8bcd\uff0c\u518d\u751f\u6210\u56fe\u7247\uff0c\u7136\u540e\u5e2e\u6211\u67e5\u8be2\u4e00\u4e0b\u676d\u5dde\u4eca\u5929\u7684\u5929\u6c14\uff0c\u5e2e\u6211\u751f\u6210\u4e00\u4efd\u4eca\u65e5\u676d\u5dde\u51fa\u884c\u8ba1\u5212"));

        assertThat(reply.parts()).hasSize(4);
        assertThat(reply.parts().get(0).text()).contains("\u4f18\u5316\u540e\u7684\u56fe\u7247\u63d0\u793a\u8bcd").contains("\u67d4\u8f6f\u5e8a\u57ab");
        assertThat(reply.parts().get(1).hasImage()).isTrue();
        assertThat(reply.parts().get(1).image().imageUrl()).isEqualTo("https://cdn.example.com/bed-cat.png");
        assertThat(reply.parts().get(2).text()).contains("\u676d\u5dde\u4eca\u5929\u5c0f\u96e8").contains("\u5e26\u4f0e");
        assertThat(reply.parts().get(3).text()).contains("\u4eca\u65e5\u676d\u5dde\u51fa\u884c\u8ba1\u5212").contains("\u897f\u6e56");
        verify(weatherService).query("\u676d\u5dde");
        verify(imageGenerationService).generate(org.mockito.ArgumentMatchers.argThat(request ->
                request.prompt().contains("\u67d4\u8f6f\u5e8a\u57ab")));
        var order = inOrder(chatService, imageGenerationService, weatherService);
        order.verify(chatService).reply(org.mockito.ArgumentMatchers.argThat(prompt -> prompt.contains("\u4efb\u52a1\u62c6\u89e3\u4e0e\u5de5\u5177\u89c4\u5212\u5668")));
        order.verify(chatService).reply(org.mockito.ArgumentMatchers.argThat(prompt -> prompt.contains("\u56fe\u7247\u63d0\u793a\u8bcd\u4f18\u5316\u5668")));
        order.verify(imageGenerationService).generate(any());
        order.verify(weatherService).query("\u676d\u5dde");
    }

    @Test
    void voiceSynthesisUsesFullPreviousToolResultInsteadOfInstructionText() {
        ChatService chatService = mock(ChatService.class);
        WeatherService weatherService = mock(WeatherService.class);
        VoiceSynthesisService voiceSynthesisService = mock(VoiceSynthesisService.class);
        String stories = "Story one: A fox found a lantern.\n\nStory two: A robot learned to whistle.";
        String badVoiceInstruction = "Synthesize the stories generated just now as voice.";
        when(chatService.reply(anyString())).thenReturn("""
                {
                  "needs_clarification": false,
                  "clarification_question": "",
                  "tasks": [
                    {"tool": "chat", "arguments": {"message": "generate random stories"}},
                    {"tool": "voice_synthesis", "arguments": {"source": "previous", "text": "Synthesize the stories generated just now as voice."}}
                  ]
                }
                """);
        doAnswer(invocation -> {
            com.example.spring.agent.ReplyEmitter emitter = invocation.getArgument(1);
            emitter.emit(stories);
            return null;
        }).when(chatService).streamReply(anyString(), any());
        when(voiceSynthesisService.synthesizeForWechat(anyString())).thenAnswer(invocation -> {
            String text = invocation.getArgument(0);
            return List.of(new VoiceSynthesisSegment(
                    ("VOICE:" + text).getBytes(StandardCharsets.UTF_8),
                    "reply-1.silk",
                    "silk",
                    "audio/silk",
                    2000,
                    16000,
                    6,
                    16,
                    text));
        });

        WechatToolRegistry toolRegistry = new WechatToolRegistry(List.of(
                new ChatWechatTool(chatService),
                new VoiceSynthesisWechatTool(chatService, voiceSynthesisService)));
        ToolCallPlanner planner = new ToolCallPlanner(chatService, new ToolCallPlanParser(new ObjectMapper()));
        WechatConversationService service = new WechatConversationService(
                chatService,
                weatherService,
                null,
                null,
                null,
                new WeatherIntentParser(),
                planner,
                toolRegistry);

        WechatReply reply = service.handleWechat(new WechatIncomingMessage(
                "user-1",
                "generate random stories and read them aloud"));

        assertThat(reply.parts()).hasSize(2);
        assertThat(reply.parts().get(0).text()).isEqualTo(stories);
        assertThat(reply.parts().get(1).hasVoice()).isTrue();
        verify(voiceSynthesisService).synthesizeForWechat(stories);
        verify(voiceSynthesisService, never()).synthesizeForWechat(badVoiceInstruction);
    }

    @Test
    void followUpVoiceSynthesisReadsPreviousTextOnceWithoutDuplicatingVoiceTranscript() {
        ChatService chatService = mock(ChatService.class);
        WeatherService weatherService = mock(WeatherService.class);
        VoiceSynthesisService voiceSynthesisService = mock(VoiceSynthesisService.class);
        String stories = "Story one: A fox found a lantern.\n\nStory two: A robot learned to whistle.";
        String duplicatedStories = stories + "\n" + stories;
        when(chatService.reply(anyString())).thenReturn("""
                {
                  "needs_clarification": false,
                  "clarification_question": "",
                  "tasks": [
                    {"tool": "chat", "arguments": {"message": "generate random stories"}},
                    {"tool": "voice_synthesis", "arguments": {"source": "previous"}}
                  ]
                }
                """, """
                {
                  "needs_clarification": false,
                  "clarification_question": "",
                  "tasks": [
                    {"tool": "voice_synthesis", "arguments": {"source": "previous"}}
                  ]
                }
                """);
        doAnswer(invocation -> {
            com.example.spring.agent.ReplyEmitter emitter = invocation.getArgument(1);
            emitter.emit(stories);
            return null;
        }).when(chatService).streamReply(anyString(), any());
        when(voiceSynthesisService.synthesizeForWechat(anyString())).thenAnswer(invocation -> {
            String text = invocation.getArgument(0);
            return List.of(new VoiceSynthesisSegment(
                    ("VOICE:" + text).getBytes(StandardCharsets.UTF_8),
                    "reply-1.silk",
                    "silk",
                    "audio/silk",
                    2000,
                    16000,
                    6,
                    16,
                    text));
        });

        WechatToolRegistry toolRegistry = new WechatToolRegistry(List.of(
                new ChatWechatTool(chatService),
                new VoiceSynthesisWechatTool(chatService, voiceSynthesisService)));
        ToolCallPlanner planner = new ToolCallPlanner(chatService, new ToolCallPlanParser(new ObjectMapper()));
        WechatConversationService service = new WechatConversationService(
                chatService,
                weatherService,
                null,
                null,
                null,
                new WeatherIntentParser(),
                planner,
                toolRegistry);

        service.handleWechat(new WechatIncomingMessage("user-1", "generate random stories and read them aloud"));
        WechatReply followUpReply = service.handleWechat(new WechatIncomingMessage(
                "user-1",
                "\u5c06\u8fd9\u4e9b\u6545\u4e8b\u7528\u8bed\u97f3\u6765\u8bfb\u4e00\u904d"));

        assertThat(followUpReply.parts()).hasSize(1);
        assertThat(followUpReply.parts().get(0).hasVoice()).isTrue();
        verify(voiceSynthesisService, times(2)).synthesizeForWechat(stories);
        verify(voiceSynthesisService, never()).synthesizeForWechat(duplicatedStories);
    }

    private WeatherResult sampleWeatherResult(String city) {
        return new WeatherResult(
                "\u6d59\u6c5f",
                city,
                "\u5c0f\u96e8",
                "27",
                "\u4e1c\u5357",
                "3",
                "68",
                "2026-07-17 10:00:00",
                List.of(new WeatherResult.Forecast(
                        "2026-07-17", "4", "\u5c0f\u96e8", "\u591a\u4e91", "29", "24",
                        "\u4e1c\u5357", "\u4e1c\u5317", "3-4", "1-2")));
    }
}
