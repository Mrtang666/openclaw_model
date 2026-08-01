package com.example.spring.skill;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class SkillMarkdownParser {

    public ParsedSkillMarkdown parse(String markdown) {
        if (markdown == null || markdown.isBlank()) {
            throw new SkillLoadException("Skill markdown is empty");
        }

        List<String> lines = markdown.replace("\r\n", "\n").replace('\r', '\n').lines().toList();
        if (lines.isEmpty() || !"---".equals(lines.get(0).trim())) {
            throw new SkillLoadException("Skill markdown must start with frontmatter");
        }

        Map<String, String> frontmatter = new LinkedHashMap<>();
        int index = 1;
        boolean closed = false;
        while (index < lines.size()) {
            String line = lines.get(index);
            if ("---".equals(line.trim())) {
                closed = true;
                index++;
                break;
            }
            if (!line.contains(":")) {
                throw new SkillLoadException("Invalid skill frontmatter line: " + line);
            }
            String[] parts = line.split(":", 2);
            String key = parts[0].trim();
            String value = parts[1].trim();
            if (key.isEmpty() || value.isEmpty()) {
                throw new SkillLoadException("Invalid skill frontmatter line: " + line);
            }
            frontmatter.put(key, value);
            index++;
        }

        if (!closed) {
            throw new SkillLoadException("Skill frontmatter is not closed");
        }

        StringBuilder body = new StringBuilder();
        for (int i = index; i < lines.size(); i++) {
            if (i > index) {
                body.append('\n');
            }
            body.append(lines.get(i));
        }
        return new ParsedSkillMarkdown(frontmatter, body.toString());
    }

    public record ParsedSkillMarkdown(Map<String, String> frontmatter, String body) {
    }
}
