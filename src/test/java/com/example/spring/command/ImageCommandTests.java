package com.example.spring.command;

import com.example.spring.image.generation.ImageGenerationResult;
import com.example.spring.image.generation.ImageGenerationService;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ImageCommandTests {

    @Test
    void generatesImageAndSavesItToLocalDirectory() throws Exception {
        ImageGenerationService service = mock(ImageGenerationService.class);
        when(service.generate(any())).thenReturn(new ImageGenerationResult(
                "帮我画一只橘猫",
                "https://cdn.example.com/generated.png",
                "PNG-DATA".getBytes(),
                "generated.png",
                "image/png",
                null,
                null));
        Path tempDir = Files.createTempDirectory("image-command-test");
        ImageCommand command = new ImageCommand(service, tempDir);

        String output = command.execute(java.util.List.of("帮我画一只橘猫"));

        assertThat(output).contains("图片已生成");
        assertThat(output).contains("https://cdn.example.com/generated.png");
        assertThat(output).contains(tempDir.toString());
        assertThat(Files.list(tempDir)).hasSize(1);
    }

    @Test
    void rejectsBlankPromptWithUsageMessage() {
        ImageCommand command = new ImageCommand(mock(ImageGenerationService.class), Path.of("generated-images"));

        assertThat(command.execute(java.util.List.of())).contains("/image <图片描述>");
    }
}
