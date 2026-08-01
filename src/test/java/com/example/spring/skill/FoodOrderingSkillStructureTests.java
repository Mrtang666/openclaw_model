package com.example.spring.skill;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class FoodOrderingSkillStructureTests {

    private static final Path SKILL_DIR = Path.of("skills", "wechat-food-ordering");

    @Test
    void skillHasValidRequiredFilesAndMetadata() throws Exception {
        String skill = readNormalized(SKILL_DIR.resolve("SKILL.md"));
        String agent = Files.readString(SKILL_DIR.resolve("agents").resolve("openai.yaml"), StandardCharsets.UTF_8);

        assertThat(skill).startsWith("---\nname: wechat-food-ordering\n");
        assertThat(skill).containsPattern("(?m)^description: \\S.+$");
        assertThat(skill).doesNotContain("TODO", "[TODO");
        assertThat(skill.lines().count()).isLessThan(500);
        assertThat(agent).contains("display_name:", "short_description:", "$wechat-food-ordering");
    }

    @Test
    void skillReferencesExist() throws Exception {
        String skill = readNormalized(SKILL_DIR.resolve("SKILL.md"));

        assertThat(skill).contains("references/gateway-contract.md", "references/payment-and-status.md");
        assertThat(SKILL_DIR.resolve("references").resolve("gateway-contract.md")).exists();
        assertThat(SKILL_DIR.resolve("references").resolve("payment-and-status.md")).exists();
    }

    private String readNormalized(Path path) throws Exception {
        return Files.readString(path, StandardCharsets.UTF_8).replace("\r\n", "\n");
    }
}
