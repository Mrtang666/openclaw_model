package com.example.spring.skill;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface SkillManager {
    List<SkillDefinition> list();

    Optional<SkillDefinition> findByName(String name);

    List<SkillDefinition> findByToolNames(Collection<String> toolNames);

    String renderSkillContext(Collection<String> skillNames);
}
