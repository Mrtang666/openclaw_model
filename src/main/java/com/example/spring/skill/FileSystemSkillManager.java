package com.example.spring.skill;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Component
public class FileSystemSkillManager implements SkillManager {

    private static final Logger log = LoggerFactory.getLogger(FileSystemSkillManager.class);

    private final Path skillsRoot;
    private final SkillMarkdownParser parser;
    private final SkillToolMapping toolMapping;
    private final Map<String, SkillDefinition> registry;

    @Autowired
    public FileSystemSkillManager(@Value("${agent.skills.path:skills}") String skillsPath) {
        this(skillsPath, new SkillMarkdownParser());
    }

    FileSystemSkillManager(String skillsPath, SkillMarkdownParser parser) {
        this(skillsPath, parser, new SkillToolMapping());
    }

    FileSystemSkillManager(String skillsPath, SkillMarkdownParser parser, SkillToolMapping toolMapping) {
        this.skillsRoot = resolveSkillsRoot(skillsPath);
        this.parser = parser == null ? new SkillMarkdownParser() : parser;
        this.toolMapping = toolMapping == null ? new SkillToolMapping() : toolMapping;
        this.registry = loadRegistry();
    }

    @Override
    public List<SkillDefinition> list() {
        return List.copyOf(registry.values());
    }

    @Override
    public Optional<SkillDefinition> findByName(String name) {
        if (name == null || name.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(registry.get(name.trim()));
    }

    @Override
    public List<SkillDefinition> findByToolNames(Collection<String> toolNames) {
        if (toolNames == null || toolNames.isEmpty()) {
            return List.of();
        }
        List<String> normalized = toolNames.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(value -> value.trim().toLowerCase(Locale.ROOT))
                .toList();
        if (normalized.isEmpty()) {
            return List.of();
        }
        return registry.values().stream()
                .filter(skill -> {
                    if (skill.name() != null && normalized.contains(skill.name().toLowerCase(Locale.ROOT))) {
                        return true;
                    }
                    List<String> skillTools = toolMapping.toolNamesFor(skill);
                    return skillTools.stream()
                            .map(value -> value.toLowerCase(Locale.ROOT))
                            .anyMatch(normalized::contains);
                })
                .toList();
    }

    @Override
    public String renderSkillContext(Collection<String> skillNames) {
        if (skillNames == null || skillNames.isEmpty()) {
            return "";
        }
        List<SkillDefinition> skills = skillNames.stream()
                .map(this::findByName)
                .flatMap(Optional::stream)
                .toList();
        if (skills.isEmpty()) {
            return "";
        }

        StringBuilder out = new StringBuilder();
        for (SkillDefinition skill : skills) {
            if (out.length() > 0) {
                out.append(System.lineSeparator()).append(System.lineSeparator());
            }
            out.append("[Skill: ").append(skill.name()).append("]").append(System.lineSeparator())
                    .append("description: ").append(skill.description()).append(System.lineSeparator())
                    .append("instructions:").append(System.lineSeparator())
                    .append(skill.body().strip());
            if (!skill.references().isEmpty()) {
                out.append(System.lineSeparator()).append("references:").append(System.lineSeparator());
                for (SkillReference reference : skill.references()) {
                    out.append("- ").append(reference.relativePath()).append(System.lineSeparator());
                }
            }
        }
        return out.toString().strip();
    }

    private Map<String, SkillDefinition> loadRegistry() {
        if (skillsRoot == null || !Files.exists(skillsRoot) || !Files.isDirectory(skillsRoot)) {
            log.info("Skill root not found or not a directory: {}", skillsRoot);
            return Map.of();
        }

        try {
            Map<String, SkillDefinition> loaded = new LinkedHashMap<>();
            try (var stream = Files.list(skillsRoot)) {
                List<Path> directories = stream
                        .filter(Files::isDirectory)
                        .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                        .toList();
                for (Path directory : directories) {
                    loadSkillDirectory(loaded, directory);
                }
            }
            return Collections.unmodifiableMap(new LinkedHashMap<>(loaded));
        } catch (IOException exception) {
            throw new SkillLoadException("Failed to scan skills from " + skillsRoot, exception);
        }
    }

    private void loadSkillDirectory(Map<String, SkillDefinition> loaded, Path directory) {
        Path skillMarkdown = directory.resolve("SKILL.md");
        if (!Files.exists(skillMarkdown)) {
            log.debug("Skipping skill directory without SKILL.md: {}", directory);
            return;
        }

        try {
            String markdown = Files.readString(skillMarkdown, StandardCharsets.UTF_8);
            SkillMarkdownParser.ParsedSkillMarkdown parsed = parser.parse(markdown);
            String skillName = require(parsed.frontmatter(), "name", directory);
            String description = require(parsed.frontmatter(), "description", directory);
            if (!directory.getFileName().toString().equals(skillName)) {
                throw new SkillLoadException("Skill name mismatch for " + directory + ": expected "
                        + directory.getFileName() + ", got " + skillName);
            }
            if (loaded.containsKey(skillName)) {
                throw new SkillLoadException("duplicate skill name: " + skillName);
            }
            List<SkillReference> references = loadReferences(directory);
            loaded.put(skillName, new SkillDefinition(skillName, description, parsed.body(), directory, references));
        } catch (IOException exception) {
            throw new SkillLoadException("Failed to read skill from " + directory, exception);
        }
    }

    private List<SkillReference> loadReferences(Path directory) throws IOException {
        Path referencesRoot = directory.resolve("references");
        if (!Files.exists(referencesRoot) || !Files.isDirectory(referencesRoot)) {
            return List.of();
        }

        List<SkillReference> references = new ArrayList<>();
        try (var stream = Files.walk(referencesRoot)) {
            stream.filter(Files::isRegularFile)
                    .sorted(Comparator.comparing(path -> path.toString().toLowerCase(Locale.ROOT)))
                    .forEach(path -> references.add(new SkillReference(
                            directory.relativize(path).toString().replace('\\', '/'),
                            path)));
        }
        return List.copyOf(references);
    }

    private String require(Map<String, String> frontmatter, String key, Path directory) {
        String value = frontmatter == null ? null : frontmatter.get(key);
        if (value == null || value.isBlank()) {
            throw new SkillLoadException("Missing " + key + " in " + directory);
        }
        return value.trim();
    }

    private Path resolveSkillsRoot(String skillsPath) {
        if (skillsPath != null && !skillsPath.isBlank()) {
            Path path = Path.of(skillsPath.trim());
            if (Files.exists(path)) {
                return path;
            }
            URL resource = getClass().getClassLoader().getResource(skillsPath.trim());
            if (resource != null && "file".equalsIgnoreCase(resource.getProtocol())) {
                try {
                    return Path.of(resource.toURI());
                } catch (URISyntaxException exception) {
                    throw new SkillLoadException("Invalid skills path resource: " + skillsPath, exception);
                }
            }
            return path;
        }
        return Path.of("skills");
    }
}
