package com.example.spring.skill;

import java.nio.file.Path;
import java.util.List;

public record SkillDefinition(
        String name,
        String description,
        String body,
        Path directory,
        List<SkillReference> references) {
}
