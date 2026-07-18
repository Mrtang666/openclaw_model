package com.example.spring.image.generation;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ImageGenerationIntentParserTests {

    private final ImageGenerationIntentParser parser = new ImageGenerationIntentParser();

    @Test
    void detectsNaturalLanguageImageGenerationRequest() {
        assertThat(parser.matches("帮我画一只赛博朋克风格的橘猫")).isTrue();
        assertThat(parser.extractPrompt("帮我画一只赛博朋克风格的橘猫"))
                .isPresent()
                .get()
                .isEqualTo("一只赛博朋克风格的橘猫");
    }

    @Test
    void detectsGenerateAnImageRequestWithNaturalChineseSentence() {
        assertThat(parser.matches("帮我生成一张打工人工作的图片")).isTrue();
        assertThat(parser.extractPrompt("帮我生成一张打工人工作的图片"))
                .isPresent()
                .get()
                .isEqualTo("打工人工作的图片");
    }

    @Test
    void detectsNaturalImageRefinementInstruction() {
        assertThat(parser.extractFollowUpInstruction("更精神一点，办公场景更高级一点"))
                .isPresent()
                .get()
                .isEqualTo("更精神一点，办公场景更高级一点");
    }

    @Test
    void ignoresPlainChatWithoutImageIntent() {
        assertThat(parser.matches("你好，今天过得怎么样")).isFalse();
        assertThat(parser.extractPrompt("你好，今天过得怎么样")).isEmpty();
    }

    @Test
    void rejectsVagueImageRequest() {
        assertThat(parser.matches("生成图片")).isTrue();
        assertThat(parser.extractPrompt("生成图片")).isEmpty();
    }
}
