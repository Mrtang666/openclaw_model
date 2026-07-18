package com.example.spring.task;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.spring.memory.MemoryProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ImageTaskSessionServiceTests {
    @TempDir
    Path tempDirectory;

    @Test
    void persistsActiveTaskAcrossServiceRestartAndClosesItAfterCompletion() throws Exception {
        MemoryProperties properties = new MemoryProperties();
        properties.setDataDirectory(tempDirectory);
        ImageTaskSessionService service = service(properties);
        ImageTaskBrief brief = new ImageTaskBrief(
            "智能水杯", "宣传图", "科技感", "", "", "蓝白色", "方形", "", "", "GENERATE");

        service.save("user-1", TaskStatus.COLLECTING_REQUIREMENTS, brief);

        ImageTaskSessionService restarted = service(properties);
        ImageTaskSession restored = restarted.loadActive("user-1").orElseThrow();
        assertThat(restored.status()).isEqualTo(TaskStatus.COLLECTING_REQUIREMENTS);
        assertThat(restored.brief().subject()).isEqualTo("智能水杯");
        assertThat(restored.brief().aspectRatio()).isEqualTo("方形");

        restarted.complete("user-1");
        assertThat(restarted.loadActive("user-1")).isEmpty();
    }

    private ImageTaskSessionService service(MemoryProperties properties) throws Exception {
        ImageTaskSessionService service = new ImageTaskSessionService(
            properties, new ObjectMapper());
        service.afterPropertiesSet();
        return service;
    }
}
