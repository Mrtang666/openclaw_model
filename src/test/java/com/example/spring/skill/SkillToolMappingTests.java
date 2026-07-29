package com.example.spring.skill;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SkillToolMappingTests {

    @TempDir
    Path tempDir;

    private final SkillToolMapping mapping = new SkillToolMapping();

    @Test
    void readsToolNamesFromSkillJsonWhenPresent() throws Exception {
        Files.writeString(tempDir.resolve("skill.json"), """
                {
                  "tools": ["meituan_travel"]
                }
                """, StandardCharsets.UTF_8);

        SkillDefinition skill = new SkillDefinition(
                "meituan-travel",
                "Travel planning",
                "Use `other_tool` in the body.",
                tempDir,
                List.of());

        assertThat(mapping.toolNamesFor(skill)).containsExactly("meituan_travel");
    }

    @Test
    void fallsBackToBacktickedToolNamesInBody() {
        SkillDefinition skill = new SkillDefinition(
                "wechat-food-ordering",
                "Food ordering",
                "Use `food_delivery` for ordering and `meituan_travel` for travel.",
                tempDir,
                List.of());

        assertThat(mapping.toolNamesFor(skill)).containsExactly("food_delivery", "meituan_travel");
    }
}
