package com.example.spring.wechat.voice.recognition.client;

import com.example.spring.wechat.voice.recognition.VoiceRecognitionException;
import com.example.spring.wechat.voice.recognition.model.VoiceRecognitionRequest;
import com.example.spring.wechat.voice.recognition.model.VoiceRecognitionResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class DashScopeVoiceRecognitionClientTests {

    @Test
    void recognizesLocalAudioBytesThroughCompatibleAsrModel() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        DashScopeVoiceRecognitionClient client = new DashScopeVoiceRecognitionClient(
                builder,
                new ObjectMapper(),
                "test-key",
                "https://dashscope.example.com/compatible-mode/v1",
                "qwen3-asr-flash",
                1,
                100);

        server.expect(requestTo("https://dashscope.example.com/compatible-mode/v1/chat/completions"))
                .andExpect(method(POST))
                .andExpect(header(AUTHORIZATION, "Bearer test-key"))
                .andExpect(jsonPath("$.model").value("qwen3-asr-flash"))
                .andExpect(jsonPath("$.stream").value(false))
                .andExpect(jsonPath("$.messages[0].role").value("system"))
                .andExpect(jsonPath("$.messages[1].content[0].type").value("input_audio"))
                .andExpect(jsonPath("$.messages[1].content[0].input_audio.data")
                        .value("data:audio/wav;base64,Vk9JQ0U="))
                .andExpect(jsonPath("$.asr_options.language").value("zh"))
                .andRespond(withSuccess("""
                        {
                          "choices": [
                            {
                              "message": {
                                "content": "杭州今天天气怎么样"
                              }
                            }
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        VoiceRecognitionResult result = client.recognize(new VoiceRecognitionRequest(
                "VOICE".getBytes(),
                null,
                "voice.wav",
                "audio/wav",
                "wav",
                16000,
                1800,
                "zh"));

        assertThat(result.text()).isEqualTo("杭州今天天气怎么样");
        assertThat(result.language()).isEqualTo("zh");
        assertThat(result.durationMs()).isEqualTo(1800);
        assertThat(result.source()).isEqualTo("DASHSCOPE_QWEN_ASR");
        server.verify();
    }

    @Test
    void recognizesAudioUrlThroughCompatibleAsrModel() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        DashScopeVoiceRecognitionClient client = new DashScopeVoiceRecognitionClient(
                builder,
                new ObjectMapper(),
                "test-key",
                "https://dashscope.example.com/compatible-mode/v1",
                "qwen3-asr-flash",
                2,
                100);

        server.expect(requestTo("https://dashscope.example.com/compatible-mode/v1/chat/completions"))
                .andExpect(method(POST))
                .andExpect(header(AUTHORIZATION, "Bearer test-key"))
                .andExpect(jsonPath("$.model").value("qwen3-asr-flash"))
                .andExpect(jsonPath("$.messages[1].content[0].input_audio.data")
                        .value("https://cdn.example.com/voice.wav"))
                .andRespond(withSuccess("""
                        {
                          "choices": [
                            {
                              "message": {
                                "content": "杭州今天会下雨吗"
                              }
                            }
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        VoiceRecognitionResult result = client.recognize(new VoiceRecognitionRequest(
                null,
                "https://cdn.example.com/voice.wav",
                "voice.wav",
                "audio/wav",
                "wav",
                16000,
                1800,
                "zh"));

        assertThat(result.text()).isEqualTo("杭州今天会下雨吗");
        assertThat(result.language()).isEqualTo("zh");
        assertThat(result.source()).isEqualTo("DASHSCOPE_QWEN_ASR");
        server.verify();
    }

    @Test
    void rejectsMissingApiKey() {
        DashScopeVoiceRecognitionClient client = new DashScopeVoiceRecognitionClient(
                RestClient.builder(),
                new ObjectMapper(),
                "",
                "https://dashscope.example.com/compatible-mode/v1",
                "qwen3-asr-flash",
                1,
                100);

        assertThatThrownBy(() -> client.recognize(new VoiceRecognitionRequest(
                null,
                "https://cdn.example.com/voice.wav",
                "voice.wav",
                "audio/wav",
                "wav",
                16000,
                1800,
                "zh")))
                .isInstanceOf(VoiceRecognitionException.class)
                .hasMessage("未配置 DASHSCOPE_API_KEY");
    }
}
