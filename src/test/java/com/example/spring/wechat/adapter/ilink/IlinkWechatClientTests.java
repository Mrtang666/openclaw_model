package com.example.spring.wechat.adapter.ilink;

import com.github.wechat.ilink.sdk.ILinkClient;
import com.github.wechat.ilink.sdk.core.model.CDNMedia;
import com.github.wechat.ilink.sdk.core.model.ImageItem;
import com.github.wechat.ilink.sdk.core.model.MessageItem;
import com.github.wechat.ilink.sdk.core.model.VoiceItem;
import com.github.wechat.ilink.sdk.core.model.WeixinMessage;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

import javax.imageio.ImageIO;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import com.example.spring.wechat.model.ImageSourceType;
import com.example.spring.wechat.model.VoiceSourceType;
import com.example.spring.wechat.model.WechatIncomingMessage;

class IlinkWechatClientTests {

    @Test
    void mapsTextAndImageItemsIntoOneIncomingMessage() throws IOException {
        ILinkClient delegate = mock(ILinkClient.class);
        IlinkWechatClient client = new IlinkWechatClient(delegate);

        MessageItem textItem = MessageItem.text("帮我看看这张图");
        MessageItem imageItem = new MessageItem();
        ImageItem image = new ImageItem();
        image.setMedia(new CDNMedia());
        imageItem.setImage_item(image);

        WeixinMessage message = new WeixinMessage();
        message.setMessage_id(100L);
        message.setFrom_user_id("user-1");
        message.setContext_token("ctx-1");
        message.setItem_list(List.of(textItem, imageItem));

        byte[] imageBytes = samplePngBytes();
        when(delegate.getUpdates()).thenReturn(List.of(message));
        when(delegate.downloadImageFromMessageItem(imageItem)).thenReturn(imageBytes);

        List<WechatIncomingMessage> updates = client.getUpdates();

        assertThat(updates).hasSize(1);
        WechatIncomingMessage incoming = updates.get(0);
        assertThat(incoming.fromUserId()).isEqualTo("user-1");
        assertThat(incoming.text()).contains("帮我看看这张图");
        assertThat(incoming.images()).hasSize(1);
        assertThat(incoming.images().get(0).sourceType()).isEqualTo(ImageSourceType.WECHAT_ATTACHMENT);
        assertThat(incoming.images().get(0).bytes()).isNotNull();
        assertThat(incoming.images().get(0).bytes().length).isEqualTo(imageBytes.length);
    }

    @Test
    void mapsVoiceItemsIntoIncomingMessage() throws IOException {
        ILinkClient delegate = mock(ILinkClient.class);
        IlinkWechatClient client = new IlinkWechatClient(delegate);

        MessageItem voiceItem = new MessageItem();
        VoiceItem voice = new VoiceItem();
        voice.setMedia(new CDNMedia());
        voice.setPlaytime(2600);
        voice.setSample_rate(16000);
        voice.setEncode_type(6);
        voice.setText("杭州今天天气怎么样");
        voiceItem.setVoice_item(voice);

        WeixinMessage message = new WeixinMessage();
        message.setMessage_id(200L);
        message.setFrom_user_id("user-2");
        message.setContext_token("ctx-2");
        message.setItem_list(List.of(voiceItem));

        byte[] voiceBytes = "VOICE-DATA".getBytes();
        when(delegate.getUpdates()).thenReturn(List.of(message));
        when(delegate.downloadVoiceFromMessageItem(voiceItem)).thenReturn(voiceBytes);

        List<WechatIncomingMessage> updates = client.getUpdates();

        assertThat(updates).hasSize(1);
        WechatIncomingMessage incoming = updates.get(0);
        assertThat(incoming.fromUserId()).isEqualTo("user-2");
        assertThat(incoming.voices()).hasSize(1);
        assertThat(incoming.hasVoices()).isTrue();
        assertThat(incoming.voices().get(0).sourceType()).isEqualTo(VoiceSourceType.WECHAT_ATTACHMENT);
        assertThat(incoming.voices().get(0).bytes()).isEqualTo(voiceBytes);
        assertThat(incoming.voices().get(0).durationMs()).isEqualTo(2600);
        assertThat(incoming.voices().get(0).sampleRate()).isEqualTo(16000);
        assertThat(incoming.voices().get(0).embeddedText()).isEqualTo("杭州今天天气怎么样");
    }

    @Test
    void keepsVoiceMessageWhenVoiceDownloadFails() throws IOException {
        ILinkClient delegate = mock(ILinkClient.class);
        IlinkWechatClient client = new IlinkWechatClient(delegate);

        MessageItem voiceItem = new MessageItem();
        VoiceItem voice = new VoiceItem();
        voice.setMedia(new CDNMedia());
        voice.setPlaytime(1800);
        voice.setSample_rate(16000);
        voice.setEncode_type(6);
        voiceItem.setVoice_item(voice);

        WeixinMessage message = new WeixinMessage();
        message.setMessage_id(300L);
        message.setFrom_user_id("user-3");
        message.setContext_token("ctx-3");
        message.setItem_list(List.of(voiceItem));

        when(delegate.getUpdates()).thenReturn(List.of(message));
        when(delegate.downloadVoiceFromMessageItem(voiceItem)).thenThrow(new IOException("download failed"));

        List<WechatIncomingMessage> updates = client.getUpdates();

        assertThat(updates).hasSize(1);
        WechatIncomingMessage incoming = updates.get(0);
        assertThat(incoming.fromUserId()).isEqualTo("user-3");
        assertThat(incoming.hasVoices()).isTrue();
        assertThat(incoming.voices()).hasSize(1);
        assertThat(incoming.voices().get(0).hasBytes()).isFalse();
        assertThat(incoming.voices().get(0).sourceReference()).isEqualTo("wechat://300/voice/1");
    }

    private byte[] samplePngBytes() throws IOException {
        BufferedImage image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
        image.setRGB(0, 0, Color.RED.getRGB());
        image.setRGB(0, 1, Color.GREEN.getRGB());
        image.setRGB(1, 0, Color.BLUE.getRGB());
        image.setRGB(1, 1, Color.WHITE.getRGB());

        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", output);
            return output.toByteArray();
        }
    }
}
