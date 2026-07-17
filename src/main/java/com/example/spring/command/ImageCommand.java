package com.example.spring.command;

import com.example.spring.image.generation.ImageGenerationRequest;
import com.example.spring.image.generation.ImageGenerationResult;
import com.example.spring.image.generation.ImageGenerationService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@Component
public class ImageCommand implements Command {

    private final ImageGenerationService imageGenerationService;
    private final Path outputDirectory;

    @Autowired
    public ImageCommand(
            ImageGenerationService imageGenerationService,
            @Value("${image.output-dir:generated-images}") String outputDirectory) {
        this(imageGenerationService, Path.of(outputDirectory));
    }

    ImageCommand(ImageGenerationService imageGenerationService, Path outputDirectory) {
        this.imageGenerationService = imageGenerationService;
        this.outputDirectory = outputDirectory;
    }

    @Override
    public String name() {
        return "image";
    }

    @Override
    public String description() {
        return "生成图片";
    }

    @Override
    public String execute(List<String> arguments) {
        String prompt = arguments == null ? "" : String.join(" ", arguments).strip();
        if (prompt.isBlank()) {
            return """
                    输入有问题：请使用 /image <图片描述>
                    例如：/image 一只赛博朋克风格的橘猫
                    """.strip();
        }

        ImageGenerationResult result = imageGenerationService.generate(new ImageGenerationRequest(prompt));
        Path savedPath = saveImage(result);
        return """
                图片已生成
                提示词：%s
                图片地址：%s
                本地路径：%s
                """.formatted(result.prompt(), result.imageUrl(), savedPath).strip();
    }

    private Path saveImage(ImageGenerationResult result) {
        try {
            Files.createDirectories(outputDirectory);
            Path target = outputDirectory.resolve(result.fileName());
            Files.write(target, result.imageBytes());
            return target.toAbsolutePath();
        } catch (IOException exception) {
            throw new IllegalStateException("图片已生成，但保存到本地失败：" + rootMessage(exception), exception);
        }
    }

    private String rootMessage(Throwable exception) {
        Throwable current = exception;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }
}
