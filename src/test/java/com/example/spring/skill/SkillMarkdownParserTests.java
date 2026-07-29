package com.example.spring.skill;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SkillMarkdownParserTests {

    private final SkillMarkdownParser parser = new SkillMarkdownParser();

    @Test
    void parsesFrontmatterAndPreservesBody() {
        String markdown = """
                ---
                name: meituan-travel
                description: Travel planning skill
                ---

                # Travel

                Use `meituan_travel`.
                """;

        SkillMarkdownParser.ParsedSkillMarkdown parsed = parser.parse(markdown);

        assertThat(parsed.frontmatter())
                .containsEntry("name", "meituan-travel")
                .containsEntry("description", "Travel planning skill");
        assertThat(parsed.body()).contains("# Travel", "Use `meituan_travel`.");
    }

    @Test
    void rejectsMarkdownWithoutFrontmatter() {
        assertThatThrownBy(() -> parser.parse("# missing frontmatter"))
                .isInstanceOf(SkillLoadException.class)
                .hasMessageContaining("frontmatter");
    }
}
