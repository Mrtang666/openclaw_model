package com.example.spring.wechat.voice.synthesis.client;

import com.example.spring.wechat.voice.synthesis.model.VoiceSynthesisAudio;
import com.example.spring.wechat.voice.synthesis.model.VoiceSynthesisRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class DashScopeVoiceSynthesisClientTests {

    @Test
    void usesOfficialMultimodalGenerationEndpointAndDownloadsReturnedAudioUrl() {
        RestClient.Builder apiBuilder = RestClient.builder();
        RestClient.Builder downloadBuilder = RestClient.builder();
        MockRestServiceServer apiServer = MockRestServiceServer.bindTo(apiBuilder).build();
        MockRestServiceServer downloadServer = MockRestServiceServer.bindTo(downloadBuilder).build();
        DashScopeVoiceSynthesisClient client = new DashScopeVoiceSynthesisClient(
                apiBuilder,
                downloadBuilder,
                new ObjectMapper(),
                "test-key",
                "https://dashscope.example.com/api/v1");

        apiServer.expect(requestTo("https://dashscope.example.com/api/v1/services/aigc/multimodal-generation/generation"))
                .andExpect(method(POST))
                .andExpect(header(AUTHORIZATION, "Bearer test-key"))
                .andExpect(jsonPath("$.model").value("qwen3-tts-flash"))
                .andExpect(jsonPath("$.input.text").value("你好"))
                .andExpect(jsonPath("$.input.voice").value("Cherry"))
                .andExpect(jsonPath("$.input.language_type").value("Chinese"))
                .andRespond(withSuccess("""
                        {
                          "output": {
                            "audio": {
                              "url": "https://cdn.example.com/reply.mp3"
                            }
                          }
                        }
                        """, MediaType.APPLICATION_JSON));
        downloadServer.expect(requestTo("https://cdn.example.com/reply.mp3"))
                .andExpect(method(GET))
                .andRespond(withSuccess("MP3".getBytes(), MediaType.parseMediaType("audio/mpeg")));

        VoiceSynthesisAudio audio = client.synthesize(new VoiceSynthesisRequest(
                "你好",
                "qwen3-tts-flash",
                "Cherry",
                "mp3",
                16000));

        assertThat(new String(audio.audioBytes())).isEqualTo("MP3");
        assertThat(audio.format()).isEqualTo("mp3");
        apiServer.verify();
        downloadServer.verify();
    }

    @Test
    void preservesSignedOssUrlExactlyWhenDownloadingGeneratedAudio() {
        RestClient.Builder apiBuilder = RestClient.builder();
        RestClient.Builder downloadBuilder = RestClient.builder();
        MockRestServiceServer apiServer = MockRestServiceServer.bindTo(apiBuilder).build();
        MockRestServiceServer downloadServer = MockRestServiceServer.bindTo(downloadBuilder).build();
        DashScopeVoiceSynthesisClient client = new DashScopeVoiceSynthesisClient(
                apiBuilder,
                downloadBuilder,
                new ObjectMapper(),
                "test-key",
                "https://dashscope.example.com/api/v1");
        String signedUrl = "https://dashscope-result-bj.oss-cn-beijing.aliyuncs.com/prod/qwen3-tts/voice.wav"
                + "?Expires=1784608577"
                + "&OSSAccessKeyId=LTAI5tGzqbGcEmE58b221XQy"
                + "&Signature=IX0D0HMCIzYI52xeCF4KSdknb70%3D"
                + "&response-content-disposition=attachment%3B%20filename%3Dreply.wav";

        apiServer.expect(requestTo("https://dashscope.example.com/api/v1/services/aigc/multimodal-generation/generation"))
                .andRespond(withSuccess("""
                        {
                          "output": {
                            "audio": {
                              "url": "%s"
                            }
                          }
                        }
                        """.formatted(signedUrl.replace("&", "\\u0026")), MediaType.APPLICATION_JSON));
        downloadServer.expect(requestTo(URI.create(signedUrl)))
                .andExpect(method(GET))
                .andRespond(withSuccess("WAV".getBytes(), MediaType.parseMediaType("audio/wav")));

        VoiceSynthesisAudio audio = client.synthesize(new VoiceSynthesisRequest(
                "浣犲ソ",
                "qwen3-tts-flash",
                "Cherry",
                "wav",
                16000));

        assertThat(new String(audio.audioBytes())).isEqualTo("WAV");
        apiServer.verify();
        downloadServer.verify();
    }
}
