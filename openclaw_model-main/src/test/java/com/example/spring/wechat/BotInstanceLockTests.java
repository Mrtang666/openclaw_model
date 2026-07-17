package com.example.spring.wechat;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BotInstanceLockTests {
    @TempDir
    Path tempDirectory;

    @Test
    void permitsOnlyOneBotProcessForTheSameLockFile() {
        WeChatBotProperties properties = new WeChatBotProperties();
        properties.setLockFile(tempDirectory.resolve("wechat.lock"));
        BotInstanceLock first = new BotInstanceLock(properties);
        BotInstanceLock second = new BotInstanceLock(properties);

        try {
            assertThat(first.tryAcquire()).isTrue();
            assertThat(second.tryAcquire()).isFalse();
            first.close();
            assertThat(second.tryAcquire()).isTrue();
        } finally {
            first.close();
            second.close();
        }
    }
}
