package com.example.spring.wechat;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class QrCodeImageTests {
    @TempDir
    Path tempDir;

    @Test
    void rendersHttpUrlAsPngQrCode() throws Exception {
        Path output =
            QrCodeImage.save(
                "https://liteapp.weixin.qq.com/q/example?qrcode=test",
                tempDir.resolve("login.png"));

        assertThat(Files.exists(output)).isTrue();
        BufferedImage image = ImageIO.read(output.toFile());
        assertThat(image).isNotNull();
        assertThat(image.getWidth()).isEqualTo(480);
        assertThat(image.getHeight()).isEqualTo(480);
    }
}
