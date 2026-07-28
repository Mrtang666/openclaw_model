package com.example.spring.skill;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class MeituanTravelSkillStructureTests {

    private static final Path SKILL_DIR = Path.of("skills", "meituan-travel");

    @Test
    void skillHasProjectMetadataAndNoPlaceholders() throws Exception {
        String skill = Files.readString(SKILL_DIR.resolve("SKILL.md"), StandardCharsets.UTF_8);
        String agent = Files.readString(SKILL_DIR.resolve("agents").resolve("openai.yaml"), StandardCharsets.UTF_8);

        assertThat(skill).startsWith("---\nname: meituan-travel\n");
        assertThat(skill).containsPattern("(?m)^description: \\S.+$");
        assertThat(skill).doesNotContain("TODO", "[TODO", "metadata:", "homepage:");
        assertThat(skill.lines().count()).isLessThan(500);
        assertThat(agent).contains("display_name:", "short_description:", "$meituan-travel");
    }

    @Test
    void referencedFilesAndOfficialChannelExist() throws Exception {
        String skill = Files.readString(SKILL_DIR.resolve("SKILL.md"), StandardCharsets.UTF_8);
        String channel = Files.readString(SKILL_DIR.resolve("channel.json"), StandardCharsets.UTF_8);

        assertThat(skill).contains(
                "references/cli-contract.md",
                "references/wechat-output-rules.md");
        assertThat(SKILL_DIR.resolve("references").resolve("cli-contract.md")).exists();
        assertThat(SKILL_DIR.resolve("references").resolve("wechat-output-rules.md")).exists();
        assertThat(channel).contains("meituan-developer");
    }
}
