package com.example.spring.wechat.conversation.tools;

import com.example.spring.chat.ChatService;
import com.example.spring.wechat.bot.WechatReply;
import com.example.spring.wechat.model.VoiceSourceType;
import com.example.spring.wechat.model.WechatIncomingVoice;
import com.example.spring.wechat.voice.recognition.model.VoiceRecognitionResult;
import com.example.spring.wechat.voice.recognition.service.VoiceRecognitionService;
import com.example.spring.wechat.voice.synthesis.model.VoiceSynthesisSegment;
import com.example.spring.wechat.voice.synthesis.service.VoiceSynthesisService;
import com.example.spring.wechat.voice.style.service.VoiceCatalog;
import com.example.spring.wechat.voice.style.service.VoicePreferenceService;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class VoiceWechatToolTests {

    @Test
    void voiceRecognitionToolTurnsAttachedWechatVoicesIntoText() {
        VoiceRecognitionService service = mock(VoiceRecognitionService.class);
        WechatIncomingVoice voice = new WechatIncomingVoice(
                VoiceSourceType.WECHAT_ATTACHMENT,
                "wechat://msg-1/voice/1",
                "VOICE".getBytes(StandardCharsets.UTF_8),
                "audio/silk",
                "voice.silk",
                1000,
                16000,
                "silk",
                null);
        when(service.recognize(voice)).thenReturn(new VoiceRecognitionResult("杭州今天天气怎么样", "zh", null, 1000, "TEST"));
        VoiceRecognitionWechatTool tool = new VoiceRecognitionWechatTool(service);

        WechatReply reply = tool.execute(new WechatToolRequest(
                "user-1",
                "",
                Map.of(),
                "无",
                List.of(voice),
                (userText, prompt) -> {
                },
                (userText, prompt) -> {
                }));

        assertThat(reply.text()).isEqualTo("杭州今天天气怎么样");
        verify(service).recognize(voice);
    }

    @Test
    void voiceSynthesisToolReturnsVoiceOnlyPartsWhenTextIsProvided() {
        ChatService chatService = mock(ChatService.class);
        VoiceSynthesisService synthesisService = mock(VoiceSynthesisService.class);
        when(synthesisService.synthesizeForWechat("你好，我会用语音回复你。")).thenReturn(List.of(
                new VoiceSynthesisSegment(
                        "VOICE-1".getBytes(StandardCharsets.UTF_8),
                        "reply-1.silk",
                        "silk",
                        "audio/silk",
                        2200,
                        16000,
                        6,
                        16,
                        "你好，我会用语音回复你。")));
        VoiceSynthesisWechatTool tool = new VoiceSynthesisWechatTool(chatService, synthesisService);

        WechatReply reply = tool.execute(new WechatToolRequest(
                "user-1",
                "请用语音回复我",
                Map.of("text", "你好，我会用语音回复你。"),
                "无",
                List.of(),
                (userText, prompt) -> {
                },
                (userText, prompt) -> {
                }));

        assertThat(reply.parts()).hasSize(1);
        assertThat(reply.parts().get(0).hasVoice()).isTrue();
        assertThat(reply.parts().get(0).voice().fileName()).isEqualTo("reply-1.silk");
        assertThat(reply.text()).isBlank();
        verify(synthesisService).synthesizeForWechat("你好，我会用语音回复你。");
        org.mockito.Mockito.verify(chatService, org.mockito.Mockito.never()).reply(anyString());
    }

    @Test
    void voiceSynthesisToolFallsBackToChatServiceWhenNoTextIsProvided() {
        ChatService chatService = mock(ChatService.class);
        VoiceSynthesisService synthesisService = mock(VoiceSynthesisService.class);
        when(chatService.reply(anyString())).thenReturn("你好，我会用语音回复你。");
        when(synthesisService.synthesizeForWechat("你好，我会用语音回复你。")).thenReturn(List.of(
                new VoiceSynthesisSegment(
                        "VOICE-1".getBytes(StandardCharsets.UTF_8),
                        "reply-1.silk",
                        "silk",
                        "audio/silk",
                        2200,
                        16000,
                        6,
                        16,
                        "你好，我会用语音回复你。")));
        VoiceSynthesisWechatTool tool = new VoiceSynthesisWechatTool(chatService, synthesisService);

        WechatReply reply = tool.execute(new WechatToolRequest(
                "user-1",
                "请用语音回复我",
                Map.of("message", "请介绍一下你自己"),
                "",
                List.of(),
                (userText, prompt) -> {
                },
                (userText, prompt) -> {
                }));

        assertThat(reply.parts()).hasSize(1);
        assertThat(reply.parts().get(0).hasVoice()).isTrue();
        assertThat(reply.parts().get(0).text()).isNull();
        assertThat(reply.text()).isBlank();
        verify(chatService).reply(anyString());
    }
    @Test
    void voiceSynthesisToolUsesPreviousAssistantReplyWhenUserAsksToReadItAgain() {
        ChatService chatService = mock(ChatService.class);
        VoiceSynthesisService synthesisService = mock(VoiceSynthesisService.class);
        when(synthesisService.synthesizeForWechat("上一条回复内容。")).thenReturn(List.of(
                new VoiceSynthesisSegment(
                        "VOICE-1".getBytes(StandardCharsets.UTF_8),
                        "reply-1.silk",
                        "silk",
                        "audio/silk",
                        2200,
                        16000,
                        6,
                        16,
                        "上一条回复内容。")));
        VoiceSynthesisWechatTool tool = new VoiceSynthesisWechatTool(chatService, synthesisService);

        WechatReply reply = tool.execute(new WechatToolRequest(
                "user-1",
                "用语音来读一遍，发给我",
                Map.of(),
                "用户：你好\n助手：上一条回复内容。\n",
                List.of(),
                (userText, prompt) -> {
                },
                (userText, prompt) -> {
                }));

        assertThat(reply.parts()).hasSize(1);
        assertThat(reply.parts().get(0).hasVoice()).isTrue();
        assertThat(reply.text()).isBlank();
        verify(synthesisService).synthesizeForWechat("上一条回复内容。");
        org.mockito.Mockito.verify(chatService, org.mockito.Mockito.never()).reply(anyString());
    }

    @Test
    void voiceSynthesisToolReadsCompletePreviousAssistantBlock() {
        ChatService chatService = mock(ChatService.class);
        VoiceSynthesisService synthesisService = mock(VoiceSynthesisService.class);
        String stories = "Story one: A fox found a lantern.\n\nStory two: A robot learned to whistle.";
        when(synthesisService.synthesizeForWechat(stories)).thenReturn(List.of(
                new VoiceSynthesisSegment(
                        "VOICE-1".getBytes(StandardCharsets.UTF_8),
                        "reply-1.silk",
                        "silk",
                        "audio/silk",
                        6200,
                        16000,
                        6,
                        16,
                        stories)));
        VoiceSynthesisWechatTool tool = new VoiceSynthesisWechatTool(chatService, synthesisService);

        WechatReply reply = tool.execute(new WechatToolRequest(
                "user-1",
                "\u5c06\u8fd9\u4e9b\u6545\u4e8b\u7528\u8bed\u97f3\u6765\u8bfb\u4e00\u904d",
                Map.of("source", "previous"),
                "\u7528\u6237\uff1aWrite two stories\n\u52a9\u624b\uff1a" + stories + "\n\u7528\u6237\uff1aAnother question",
                List.of(),
                (userText, prompt) -> {
                },
                (userText, prompt) -> {
                }));

        assertThat(reply.parts()).hasSize(1);
        assertThat(reply.parts().get(0).hasVoice()).isTrue();
        verify(synthesisService).synthesizeForWechat(stories);
        org.mockito.Mockito.verify(chatService, org.mockito.Mockito.never()).reply(anyString());
    }

    @Test
    void voiceSynthesisFailureDoesNotExposeRawOssXmlToWechatUser() {
        ChatService chatService = mock(ChatService.class);
        VoiceSynthesisService synthesisService = mock(VoiceSynthesisService.class);
        when(chatService.reply(anyString())).thenReturn("你好，我会用语音回复你。");
        when(synthesisService.synthesizeForWechat("你好，我会用语音回复你。"))
                .thenThrow(new RuntimeException("""
                        403 Forbidden: "<?xml version="1.0"?><Error><Code>SignatureDoesNotMatch</Code><Message>The request signature we calculated does not match the signature you provided.</Message><OSSAccessKeyId>LTAI...</OSSAccessKeyId><StringToSign>GET<EOL></StringToSign></Error>"
                        """));
        VoiceSynthesisWechatTool tool = new VoiceSynthesisWechatTool(chatService, synthesisService);

        WechatReply reply = tool.execute(new WechatToolRequest(
                "user-1",
                "请用语音回复我",
                Map.of("message", "请介绍一下你自己"),
                "无",
                List.of(),
                (userText, prompt) -> {
                },
                (userText, prompt) -> {
                }));

        assertThat(reply.text()).contains("语音生成失败");
        assertThat(reply.text()).doesNotContain("SignatureDoesNotMatch");
        assertThat(reply.text()).doesNotContain("<StringToSign>");
        assertThat(reply.text()).doesNotContain("OSSAccessKeyId");
    }

    @Test
    void voiceSynthesisToolUsesSavedUserVoicePreference() {
        ChatService chatService = mock(ChatService.class);
        VoiceSynthesisService synthesisService = mock(VoiceSynthesisService.class);
        VoicePreferenceService preferenceService = new VoicePreferenceService(
                new VoiceCatalog(),
                Clock.fixed(Instant.parse("2026-07-20T09:00:00Z"), ZoneId.of("UTC")));
        preferenceService.savePreference("user-1", preferenceService.catalog().findByVoice("Serena").orElseThrow());
        when(synthesisService.synthesizeForWechat("你好，我会用语音回复你。", "Serena")).thenReturn(List.of(
                new VoiceSynthesisSegment(
                        "VOICE-1".getBytes(StandardCharsets.UTF_8),
                        "reply-1.silk",
                        "silk",
                        "audio/silk",
                        2200,
                        16000,
                        6,
                        16,
                        "你好，我会用语音回复你。")));
        VoiceSynthesisWechatTool tool = new VoiceSynthesisWechatTool(chatService, synthesisService, preferenceService);

        WechatReply reply = tool.execute(new WechatToolRequest(
                "user-1",
                "请用语音回复我",
                Map.of("text", "你好，我会用语音回复你。"),
                "无",
                List.of(),
                (userText, prompt) -> {
                },
                (userText, prompt) -> {
                }));

        assertThat(reply.parts()).hasSize(1);
        verify(synthesisService).synthesizeForWechat("你好，我会用语音回复你。", "Serena");
    }
}
