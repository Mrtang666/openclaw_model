package com.example.spring.speech.voice;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.spring.memory.ConversationMemoryService;
import com.example.spring.memory.MemoryProperties;
import com.example.spring.speech.SpeechProperties;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class VoiceSelectionServiceTests {
    @TempDir
    Path temporaryDirectory;

    private ConversationMemoryService memoryService;
    private VoiceSelectionService selectionService;

    @BeforeEach
    void setUp() throws Exception {
        MemoryProperties memoryProperties = new MemoryProperties();
        memoryProperties.setDataDirectory(temporaryDirectory);
        memoryService = new ConversationMemoryService(memoryProperties);
        memoryService.afterPropertiesSet();
        SpeechProperties speechProperties = new SpeechProperties();
        speechProperties.setTtsVoice("Cherry");
        selectionService = new VoiceSelectionService(
            new VoiceCatalog(), memoryService, speechProperties);
    }

    @Test
    void ignoresOrdinaryConversationWithoutVoiceChangeIntent() {
        VoiceSelectionResult result = selectionService.handle(
            "user-1", "帮我查询今天无锡的天气");

        assertThat(result.consumed()).isFalse();
        assertThat(selectionService.hasActiveSession("user-1")).isFalse();
        assertThat(memoryService.getVoicePreference("user-1")).isNull();
    }

    @Test
    void startsOnlyAfterExplicitVoiceChangeIntentAndPersistsSelection() {
        VoiceSelectionResult start = selectionService.handle("user-1", "我想修改音色");
        assertThat(start.consumed()).isTrue();
        assertThat(start.reply()).contains("第1步");

        assertThat(selectionService.handle("user-1", "1").reply()).contains("第2步");
        assertThat(selectionService.handle("user-1", "1").reply()).contains("第3步");
        VoiceSelectionResult candidates = selectionService.handle("user-1", "1");
        assertThat(candidates.reply()).contains("苏瑶（Serena）");

        VoiceSelectionResult selected = selectionService.handle("user-1", "1");
        assertThat(selected.reply()).contains("音色已修改为“苏瑶”");
        assertThat(memoryService.getVoicePreference("user-1"))
            .isEqualTo(new VoicePreference("Serena", "苏瑶", "Chinese"));
        assertThat(selectionService.hasActiveSession("user-1")).isFalse();
    }

    @Test
    void usesCriteriaAlreadyPresentInTheInitialRequest() {
        VoiceSelectionResult result = selectionService.handle(
            "user-2", "换成温柔的普通话女声");

        assertThat(result.consumed()).isTrue();
        assertThat(result.reply()).contains("按推荐热度");
        assertThat(result.reply()).contains("苏瑶（Serena）");
        assertThat(result.reply()).doesNotContain("第1步", "第2步", "第3步");
    }

    @Test
    void negativeIntentDoesNotStartSelection() {
        VoiceSelectionResult result = selectionService.handle(
            "user-3", "我不需要修改音色，继续聊天");

        assertThat(result.consumed()).isFalse();
        assertThat(selectionService.hasActiveSession("user-3")).isFalse();
    }

    @Test
    void canRestoreTheConfiguredDefaultVoice() {
        memoryService.setVoicePreference(
            "user-4", new VoicePreference("Serena", "苏瑶", "Chinese"));

        VoiceSelectionResult result = selectionService.handle("user-4", "恢复默认音色");

        assertThat(result.consumed()).isTrue();
        assertThat(result.reply()).contains("Cherry");
        assertThat(memoryService.getVoicePreference("user-4")).isNull();
    }

    @Test
    void selectedVoiceSurvivesServiceRecreation() throws Exception {
        memoryService.setVoicePreference(
            "user-5", new VoicePreference("Ethan", "晨煦", "Chinese"));

        MemoryProperties memoryProperties = new MemoryProperties();
        memoryProperties.setDataDirectory(temporaryDirectory);
        ConversationMemoryService restarted = new ConversationMemoryService(memoryProperties);
        restarted.afterPropertiesSet();

        assertThat(restarted.getVoicePreference("user-5"))
            .isEqualTo(new VoicePreference("Ethan", "晨煦", "Chinese"));
    }

    @Test
    void recognizesMoreExplicitPhrasesButNotCancellation() {
        assertThat(selectionService.handle("user-6", "换声音").consumed()).isTrue();
        assertThat(selectionService.handle("user-7", "想用男声").consumed()).isTrue();
        assertThat(selectionService.handle("user-8", "取消修改音色").consumed()).isFalse();
    }

    @Test
    void previewDoesNotPersistOrFinishTheSelectionSession() {
        selectionService.handle("user-9", "换成温柔的普通话女声");

        VoiceSelectionResult preview = selectionService.handle("user-9", "试听1");

        assertThat(preview.consumed()).isTrue();
        assertThat(preview.reply()).contains("试听音频");
        assertThat(preview.preview())
            .isEqualTo(new VoicePreference("Serena", "苏瑶", "Chinese"));
        assertThat(memoryService.getVoicePreference("user-9")).isNull();
        assertThat(selectionService.hasActiveSession("user-9")).isTrue();

        selectionService.handle("user-9", "选择1");
        assertThat(memoryService.getVoicePreference("user-9"))
            .isEqualTo(new VoicePreference("Serena", "苏瑶", "Chinese"));
    }

    @Test
    void statusRoutingOnlyClaimsExplicitOrActiveVoiceSelectionMessages() {
        assertThat(selectionService.shouldHandle("user-10", "普通聊天内容")).isFalse();
        assertThat(selectionService.shouldHandle("user-10", "修改音色")).isTrue();

        selectionService.handle("user-10", "修改音色");
        assertThat(selectionService.shouldHandle("user-10", "3")).isTrue();
        assertThat(selectionService.shouldHandle("another-user", "3")).isFalse();
    }

    @Test
    void acceptsChineseOrdinalAnswersAndPreviewCommands() {
        selectionService.handle("user-11", "修改音色");
        assertThat(selectionService.handle("user-11", "第一个").reply()).contains("第2步");
        assertThat(selectionService.handle("user-11", "第二个").reply()).contains("第3步");
        VoiceSelectionResult candidates = selectionService.handle("user-11", "第三个");
        assertThat(candidates.reply()).contains("按推荐热度");

        VoiceSelectionResult preview = selectionService.handle("user-11", "试听第三个");
        assertThat(preview.preview()).isNotNull();
        assertThat(memoryService.getVoicePreference("user-11")).isNull();
    }
}
