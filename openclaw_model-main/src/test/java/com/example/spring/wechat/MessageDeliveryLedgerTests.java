package com.example.spring.wechat;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.spring.memory.MemoryProperties;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MessageDeliveryLedgerTests {
    @TempDir
    Path temporaryDirectory;

    private MessageDeliveryLedger ledger;

    @BeforeEach
    void setUp() throws Exception {
        MemoryProperties properties = new MemoryProperties();
        properties.setDataDirectory(temporaryDirectory);
        ledger = new MessageDeliveryLedger(properties);
        ledger.afterPropertiesSet();
    }

    @Test
    void persistsAndMovesARecoverableMessageThroughConfirmation() {
        assertThat(ledger.register("user-1", 101L, "TEXT", "原始问题")).isTrue();
        assertThat(ledger.register("user-1", 101L, "TEXT", "重复问题")).isFalse();

        ledger.ready("user-1", 101L, "标准化问题");
        assertThat(ledger.findRecoverable()).singleElement().satisfies(record -> {
            assertThat(record.status()).isEqualTo(MessageReplyStatus.READY);
            assertThat(record.replayText()).isEqualTo("标准化问题");
        });

        ledger.markRecoverableWaiting("user-1");
        assertThat(ledger.findWaitingForUser("user-1"))
            .extracting(PendingMessageRecord::status)
            .containsExactly(MessageReplyStatus.WAITING_CONFIRM);

        ledger.resolveWaiting("user-1", MessageReplyStatus.READY);
        ledger.mark("user-1", 101L, MessageReplyStatus.REPLYING);
        ledger.complete("user-1", 101L, DeliveryResult.sent());

        assertThat(ledger.findRecoverable()).isEmpty();
        assertThat(ledger.findWaitingForUser("user-1")).isEmpty();
    }

    @Test
    void keepsFailedMessagesRecoverableAndDeletesVoiceAfterSuccess() {
        byte[] voice = "voice-data".getBytes(StandardCharsets.UTF_8);
        ledger.register("user-2", 202L, "VOICE", "");
        ledger.ready("user-2", 202L, "");
        ledger.saveVoicePayload("user-2", 202L, 0, voice);
        assertThat(ledger.loadVoicePayloads("user-2", 202L)).containsExactly(voice);

        ledger.complete("user-2", 202L, DeliveryResult.failed("network"));
        assertThat(ledger.findRecoverable()).singleElement()
            .extracting(PendingMessageRecord::status)
            .isEqualTo(MessageReplyStatus.FAILED);
        assertThat(ledger.loadVoicePayloads("user-2", 202L)).containsExactly(voice);

        ledger.complete("user-2", 202L, DeliveryResult.sent());
        assertThat(ledger.loadVoicePayloads("user-2", 202L)).isEmpty();
    }
}
