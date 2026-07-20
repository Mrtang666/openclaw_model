package com.example.spring.speech;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.spring.memory.MemoryProperties;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PendingVoiceReplyStoreTests {
    @TempDir
    Path temporaryDirectory;

    private PendingVoiceReplyStore store;

    @BeforeEach
    void setUp() throws Exception {
        MemoryProperties properties = new MemoryProperties();
        properties.setDataDirectory(temporaryDirectory);
        store = new PendingVoiceReplyStore(properties);
        store.afterPropertiesSet();
    }

    @Test
    void persistsRemainingSegmentsAndTheirOrder() {
        List<VoiceDeliveryAsset> assets = List.of(
            asset("第一段"), asset("第二段"), asset("第三段"));

        assertThat(store.saveRemaining("user", 100L, assets, 1, true)).isTrue();

        List<PendingVoiceReply> pending = store.loadPending();
        assertThat(pending).hasSize(2);
        assertThat(pending).extracting(PendingVoiceReply::sequence)
            .containsExactly(2, 3);
        assertThat(pending.get(0).voiceSent()).isTrue();
        assertThat(pending.get(1).voiceSent()).isFalse();
        assertThat(pending.get(0).asset().text()).isEqualTo("第二段");
    }

    @Test
    void marksAndDeletesCompletedSegments() {
        store.saveRemaining("user", 101L, List.of(asset("内容")), 0, false);
        PendingVoiceReply pending = store.loadPending().get(0);

        store.markVoiceSent(pending.id());
        assertThat(store.loadPending().get(0).voiceSent()).isTrue();

        store.complete(store.loadPending().get(0));
        assertThat(store.loadPending()).isEmpty();
    }

    private static VoiceDeliveryAsset asset(String text) {
        return new VoiceDeliveryAsset(
            text,
            ("silk-" + text).getBytes(StandardCharsets.UTF_8),
            1000,
            ("mp3-" + text).getBytes(StandardCharsets.UTF_8));
    }
}
