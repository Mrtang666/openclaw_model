package com.example.spring.skill;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SkillToolMapping {

    private static final Pattern BACKTICK_PATTERN = Pattern.compile("`([a-z0-9][a-z0-9_-]*)`");
    private final ObjectMapper objectMapper;

    public SkillToolMapping() {
        this(new ObjectMapper());
    }

    SkillToolMapping(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper == null ? new ObjectMapper() : objectMapper;
    }

    public List<String> toolNamesFor(SkillDefinition skill) {
        if (skill == null) {
            return List.of();
        }
        Path json = skill.directory() == null ? null : skill.directory().resolve("skill.json");
        if (json != null && Files.exists(json)) {
            List<String> tools = readToolsFromJson(json);
            if (!tools.isEmpty()) {
                return tools;
            }
        }
        return toolsFromBody(skill.body());
    }

    private List<String> readToolsFromJson(Path json) {
        try {
            JsonNode root = objectMapper.readTree(Files.readString(json, StandardCharsets.UTF_8));
            JsonNode tools = root.get("tools");
            if (tools == null || !tools.isArray()) {
                return List.of();
            }
            Set<String> values = new LinkedHashSet<>();
            for (JsonNode tool : tools) {
                if (tool != null && tool.isTextual() && !tool.asText().isBlank()) {
                    values.add(tool.asText().trim());
                }
            }
            return List.copyOf(values);
        } catch (IOException exception) {
            throw new SkillLoadException("Failed to parse skill.json: " + json, exception);
        }
    }

    private List<String> toolsFromBody(String body) {
        if (body == null || body.isBlank()) {
            return List.of();
        }
        Set<String> values = new LinkedHashSet<>();
        Matcher matcher = BACKTICK_PATTERN.matcher(body);
        while (matcher.find()) {
            String tool = matcher.group(1);
            if (tool != null && !tool.isBlank()) {
                values.add(tool.trim().toLowerCase(Locale.ROOT));
            }
        }
        return List.copyOf(new ArrayList<>(values));
    }
}
