package com.example.spring.wechat.conversation.tools;

import com.example.spring.chat.ChatService;
import com.example.spring.wechat.bot.WechatReply;
import com.example.spring.wechat.voice.style.service.VoiceCatalog;
import com.example.spring.wechat.voice.style.service.VoicePreferenceService;
import com.example.spring.wechat.voice.synthesis.model.VoiceSynthesisSegment;
import com.example.spring.wechat.voice.synthesis.service.VoiceSynthesisService;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class VoiceStyleWechatToolTests {

    @Test
    void asksForMorePreferenceWhenUserOnlySaysChangeVoice() {
        VoiceStyleWechatTool tool = toolWith(mock(ChatService.class), mock(VoiceSynthesisService.class), preferenceService());

        WechatReply reply = tool.execute(request("user-1", "修改音色"));

        assertThat(reply.text()).contains("什么感觉").contains("温柔女声").contains("沉稳男声");
    }

    @Test
    void showsMatchedCandidatesWhenUserAsksForGentleFemaleVoice() {
        VoiceStyleWechatTool tool = toolWith(mock(ChatService.class), mock(VoiceSynthesisService.class), preferenceService());

        WechatReply reply = tool.execute(request("user-1", "换一个温柔的女声"));

        assertThat(reply.text()).contains("Serena").contains("试听第一个").contains("选第二个");
    }

    @Test
    void previewsCandidateVoiceAndRemembersItForShortConfirmation() {
        ChatService chatService = mock(ChatService.class);
        VoiceSynthesisService synthesisService = mock(VoiceSynthesisService.class);
        VoicePreferenceService preferenceService = preferenceService();
        VoiceStyleWechatTool tool = toolWith(chatService, synthesisService, preferenceService);
        tool.execute(request("user-1", "换一个温柔的女声"));
        when(synthesisService.synthesizeForWechat("你好，我是你的 AI 助手，很高兴用这个声音陪你聊天。", "Serena"))
                .thenReturn(List.of(new VoiceSynthesisSegment(
                        "VOICE".getBytes(StandardCharsets.UTF_8),
                        "preview-1.silk",
                        "silk",
                        "audio/silk",
                        3000,
                        16000,
                        6,
                        16,
                        "你好，我是你的 AI 助手，很高兴用这个声音陪你聊天。")));

        WechatReply reply = tool.execute(request("user-1", "试听第一个"));

        assertThat(reply.parts()).hasSize(1);
        assertThat(reply.parts().get(0).hasVoice()).isTrue();
        assertThat(preferenceService.recentPreview("user-1")).isPresent();
        verify(synthesisService).synthesizeForWechat("你好，我是你的 AI 助手，很高兴用这个声音陪你聊天。", "Serena");
    }

    @Test
    void confirmsRecentlyPreviewedVoiceWhenUserSaysUseThisOne() {
        ChatService chatService = mock(ChatService.class);
        VoiceSynthesisService synthesisService = mock(VoiceSynthesisService.class);
        VoicePreferenceService preferenceService = preferenceService();
        VoiceStyleWechatTool tool = toolWith(chatService, synthesisService, preferenceService);
        tool.execute(request("user-1", "换一个温柔的女声"));
        when(synthesisService.synthesizeForWechat("你好，我是你的 AI 助手，很高兴用这个声音陪你聊天。", "Serena"))
                .thenReturn(List.of(new VoiceSynthesisSegment(
                        "VOICE".getBytes(StandardCharsets.UTF_8),
                        "preview-1.silk",
                        "silk",
                        "audio/silk",
                        3000,
                        16000,
                        6,
                        16,
                        "你好，我是你的 AI 助手，很高兴用这个声音陪你聊天。")));
        tool.execute(request("user-1", "试听第一个"));

        WechatReply reply = tool.execute(request("user-1", "就用这个"));

        assertThat(reply.text()).contains("已切换为").contains("Serena");
        assertThat(preferenceService.effectiveVoice("user-1")).isEqualTo("Serena");
    }

    @Test
    void confirmsDisplayedCandidateWhenUserNaturallyRefersToItsOrdinal() {
        VoicePreferenceService preferenceService = preferenceService();
        VoiceStyleWechatTool tool = toolWith(mock(ChatService.class), mock(VoiceSynthesisService.class), preferenceService);
        tool.execute(request("user-1", "换一个女声"));
        String fifthVoice = preferenceService.candidateByOrdinal("user-1", 5)
                .orElseThrow()
                .voice();

        WechatReply reply = tool.execute(request("user-1", "把第五个当成音色"));

        assertThat(reply.text()).contains("已切换为").contains(fifthVoice);
        assertThat(preferenceService.effectiveVoice("user-1")).isEqualTo(fifthVoice);
    }

    @Test
    void refinesCandidatesWithPreviousVoiceStyleContextWhenUserAddsPreference() {
        VoicePreferenceService preferenceService = preferenceService();
        VoiceStyleWechatTool tool = toolWith(mock(ChatService.class), mock(VoiceSynthesisService.class), preferenceService);
        tool.execute(request("user-1", "温柔女声"));

        WechatReply reply = tool.execute(request("user-1", "再成熟一点"));

        assertThat(reply.text()).doesNotContain("男声");
        assertThat(preferenceService.candidateByOrdinal("user-1", 1))
                .isPresent()
                .get()
                .extracting(profile -> profile.gender())
                .isEqualTo("女声");
    }

    private static VoiceStyleWechatTool toolWith(
            ChatService chatService,
            VoiceSynthesisService synthesisService,
            VoicePreferenceService preferenceService) {
        return new VoiceStyleWechatTool(chatService, synthesisService, preferenceService);
    }

    private static VoicePreferenceService preferenceService() {
        return new VoicePreferenceService(
                new VoiceCatalog(),
                Clock.fixed(Instant.parse("2026-07-20T09:00:00Z"), ZoneId.of("UTC")));
    }

    private static WechatToolRequest request(String userId, String text) {
        return new WechatToolRequest(
                userId,
                text,
                Map.of(),
                "无",
                List.of(),
                (userText, prompt) -> {
                },
                (userText, prompt) -> {
                });
    }
}
