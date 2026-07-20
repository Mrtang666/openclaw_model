package com.example.spring.wechat.image.service;

import com.example.spring.wechat.model.ImageSourceType;
import com.example.spring.wechat.model.WechatIncomingImage;
import com.example.spring.wechat.model.WechatIncomingMessage;
import com.example.spring.wechat.image.model.ImageAnalysisRequest;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

import javax.imageio.ImageIO;

import static org.assertj.core.api.Assertions.assertThat;

class ImageInputResolverTests {

    @Test
    void extractsAttachedImageAndImageUrlFromMessageText() throws IOException {
        ImageInputResolver resolver = new ImageInputResolver();
        WechatIncomingMessage message = new WechatIncomingMessage(
                "msg-1",
                "user-1",
                "ctx-1",
                "请看看这个 https://example.com/demo.png",
                List.of(new WechatIncomingImage(
                        ImageSourceType.WECHAT_ATTACHMENT,
                        "wechat://msg-1/image-1",
                        samplePngBytes(),
                        null,
                        "photo.png",
                        null,
                        null,
                        null)));

        ImageAnalysisRequest request = resolver.resolve(message);

        assertThat(request.userText()).isEqualTo("请看看这个");
        assertThat(request.images()).hasSize(2);
        assertThat(request.images().get(0).sourceType()).isEqualTo(ImageSourceType.WECHAT_ATTACHMENT);
        assertThat(request.images().get(0).width()).isEqualTo(2);
        assertThat(request.images().get(0).height()).isEqualTo(2);
        assertThat(request.images().get(0).colorMode()).isEqualTo("COLOR");
        assertThat(request.images().get(1).sourceType()).isEqualTo(ImageSourceType.TEXT_URL);
        assertThat(request.images().get(1).sourceReference()).isEqualTo("https://example.com/demo.png");
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
