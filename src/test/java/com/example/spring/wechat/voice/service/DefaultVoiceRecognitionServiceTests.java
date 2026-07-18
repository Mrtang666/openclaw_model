package com.example.spring.wechat.voice.service;

import com.example.spring.wechat.client.VoiceSourceType;
import com.example.spring.wechat.client.WechatIncomingVoice;
import com.example.spring.wechat.voice.VoiceRecognitionException;
import com.example.spring.wechat.voice.audio.AudioFormatDetector;
import com.example.spring.wechat.voice.audio.AudioTranscoder;
import com.example.spring.wechat.voice.client.VoiceRecognitionClient;
import com.example.spring.wechat.voice.model.VoiceRecognitionRequest;
import com.example.spring.wechat.voice.model.VoiceRecognitionResult;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class DefaultVoiceRecognitionServiceTests {

    @Test
    void usesEmbeddedWechatTranscriptionWithoutCallingRemoteClient() {
        VoiceRecognitionClient client = mock(VoiceRecognitionClient.class);
        DefaultVoiceRecognitionService service = new DefaultVoiceRecognitionService(client);
        WechatIncomingVoice voice = new WechatIncomingVoice(
                VoiceSourceType.WECHAT_ATTACHMENT,
                "wechat://msg-1/voice/1",
                "VOICE".getBytes(),
                "audio/silk",
                "voice.silk",
                1800,
                16000,
                "silk",
                "杭州今天天气怎么样");

        VoiceRecognitionResult result = service.recognize(voice);

        assertThat(result.text()).isEqualTo("杭州今天天气怎么样");
        assertThat(result.source()).isEqualTo("WECHAT_EMBEDDED");
        verifyNoInteractions(client);
    }

    @Test
    void delegatesToRecognitionClientWhenNoEmbeddedTextExists() {
        VoiceRecognitionClient client = mock(VoiceRecognitionClient.class);
        DefaultVoiceRecognitionService service = new DefaultVoiceRecognitionService(client);
        WechatIncomingVoice voice = new WechatIncomingVoice(
                VoiceSourceType.TEXT_URL,
                "https://cdn.example.com/voice.wav",
                null,
                "audio/wav",
                "voice.wav",
                1800,
                16000,
                "wav",
                null);
        when(client.recognize(any())).thenReturn(new VoiceRecognitionResult(
                "帮我画一只橘猫",
                "zh",
                null,
                1800,
                "DASHSCOPE"));

        VoiceRecognitionResult result = service.recognize(voice);

        assertThat(result.text()).isEqualTo("帮我画一只橘猫");
        verify(client).recognize(org.mockito.ArgumentMatchers.argThat((VoiceRecognitionRequest request) ->
                request.sourceUrl().equals("https://cdn.example.com/voice.wav")
                        && request.format().equals("wav")
                        && request.sampleRate().equals(16000)));
    }

    @Test
    void transcodesUnsupportedLocalVoiceBytesBeforeCallingRecognitionClient() {
        VoiceRecognitionClient client = mock(VoiceRecognitionClient.class);
        AudioTranscoder transcoder = mock(AudioTranscoder.class);
        DefaultVoiceRecognitionService service = new DefaultVoiceRecognitionService(
                client,
                new AudioFormatDetector(),
                transcoder);
        WechatIncomingVoice voice = new WechatIncomingVoice(
                VoiceSourceType.WECHAT_ATTACHMENT,
                "wechat://msg-1/voice/1",
                "SILK".getBytes(),
                "audio/silk",
                "voice.silk",
                1800,
                16000,
                "silk",
                null);
        when(transcoder.convertToWav("SILK".getBytes(), "silk", 16000)).thenReturn("WAV".getBytes());
        when(client.recognize(any())).thenReturn(new VoiceRecognitionResult(
                "北京今天天气怎么样",
                "zh",
                null,
                1800,
                "DASHSCOPE_QWEN_ASR"));

        VoiceRecognitionResult result = service.recognize(voice);

        assertThat(result.text()).isEqualTo("北京今天天气怎么样");
        verify(client).recognize(org.mockito.ArgumentMatchers.argThat((VoiceRecognitionRequest request) ->
                new String(request.audioBytes()).equals("WAV")
                        && request.contentType().equals("audio/wav")
                        && request.fileName().equals("voice.wav")
                        && request.format().equals("wav")));
    }

    @Test
    void rejectsVoiceWithoutBytesUrlOrEmbeddedText() {
        DefaultVoiceRecognitionService service = new DefaultVoiceRecognitionService(mock(VoiceRecognitionClient.class));
        WechatIncomingVoice voice = new WechatIncomingVoice(
                VoiceSourceType.WECHAT_ATTACHMENT,
                "wechat://msg-1/voice/1",
                null,
                null,
                null,
                null,
                null,
                null,
                null);

        assertThatThrownBy(() -> service.recognize(voice))
                .isInstanceOf(VoiceRecognitionException.class)
                .hasMessageContaining("没有读取到语音内容");
    }
}
