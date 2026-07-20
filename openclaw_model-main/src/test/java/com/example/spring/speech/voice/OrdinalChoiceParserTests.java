package com.example.spring.speech.voice;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class OrdinalChoiceParserTests {
    @Test
    void parsesArabicAndChineseOrdinals() {
        assertThat(OrdinalChoiceParser.parse("3")).hasValue(3);
        assertThat(OrdinalChoiceParser.parse("第3个")).hasValue(3);
        assertThat(OrdinalChoiceParser.parse("选择第3项")).hasValue(3);
        assertThat(OrdinalChoiceParser.parse("第三个")).hasValue(3);
        assertThat(OrdinalChoiceParser.parse("就用第十个")).hasValue(10);
    }

    @Test
    void rejectsUnrelatedText() {
        assertThat(OrdinalChoiceParser.parse("今天3点查询天气")).isEmpty();
        assertThat(OrdinalChoiceParser.parse("第0个")).isEmpty();
    }
}
