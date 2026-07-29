package com.example.spring.skill;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FileSystemSkillManagerTests {

    @TempDir
    Path tempDir;

    @Test
    void scansSkillsAndRendersMatchingContext() throws Exception {
        writeSkill("meituan-travel", """
                ---
                name: meituan-travel
                description: Travel planning
                ---

                Use `meituan_travel` for trip planning.
                """);
        writeSkill("wechat-food-ordering", """
                ---
                name: wechat-food-ordering
                description: Food ordering
                ---

                Use `food_delivery` for ordering.
                """);
        writeReference("meituan-travel", "references/cli-contract.md", "# contract");

        FileSystemSkillManager manager = new FileSystemSkillManager(tempDir.toString());

        assertThat(manager.list()).extracting(SkillDefinition::name)
                .containsExactly("meituan-travel", "wechat-food-ordering");
        assertThat(manager.findByName("meituan-travel")).isPresent();
        assertThat(manager.findByName("missing")).isEmpty();
        assertThat(manager.renderSkillContext(List.of("meituan-travel")))
                .contains("[Skill: meituan-travel]", "Travel planning", "Use `meituan_travel`");
        assertThat(manager.findByName("meituan-travel").orElseThrow().references())
                .hasSize(1);
        assertThat(manager.findByName("meituan-travel").orElseThrow().references().get(0).relativePath())
                .isEqualTo("references/cli-contract.md");
    }

    @Test
    void ignoresDirectoriesWithoutSkillMarkdown() throws Exception {
        Files.createDirectories(tempDir.resolve("empty-skill"));
        writeSkill("meituan-travel", """
                ---
                name: meituan-travel
                description: Travel planning
                ---

                Use `meituan_travel`.
                """);

        FileSystemSkillManager manager = new FileSystemSkillManager(tempDir.toString());

        assertThat(manager.list()).hasSize(1);
    }

    @Test
    void rejectsDuplicateSkillNames() throws Exception {
        writeSkill("alpha-skill", """
                ---
                name: duplicate-skill
                description: First
                ---

                Use `alpha`.
                """);
        writeSkill("beta-skill", """
                ---
                name: duplicate-skill
                description: Second
                ---

                Use `beta`.
                """);

        assertThatThrownBy(() -> new FileSystemSkillManager(tempDir.toString()).list())
                .isInstanceOf(SkillLoadException.class)
                .hasMessageContaining("duplicate");
    }

    private void writeSkill(String directoryName, String content) throws Exception {
        Path skillDir = tempDir.resolve(directoryName);
        Files.createDirectories(skillDir);
        Files.writeString(skillDir.resolve("SKILL.md"), content, StandardCharsets.UTF_8);
    }

    private void writeReference(String directoryName, String relativePath, String content) throws Exception {
        Path file = tempDir.resolve(directoryName).resolve(relativePath);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content, StandardCharsets.UTF_8);
    }
}
