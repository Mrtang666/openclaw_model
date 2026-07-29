package com.example.spring.cli.command.impl;

import com.example.spring.cli.command.core.Command;
import com.example.spring.exception.CommandException;
import com.example.spring.wechat.knowledge.model.KnowledgeImportDocument;
import com.example.spring.wechat.knowledge.model.KnowledgeIngestionResult;
import com.example.spring.wechat.knowledge.service.KnowledgeIngestionService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;

@Component
public class KnowledgeImportCommand implements Command {

    private static final Set<String> TEXT_EXTENSIONS = Set.of(".md", ".txt");
    private static final Set<String> STRUCTURED_EXTENSIONS = Set.of(".json", ".jsonl");

    private final KnowledgeIngestionService ingestionService;
    private final ObjectMapper objectMapper;

    @Autowired
    public KnowledgeImportCommand(KnowledgeIngestionService ingestionService) {
        this(ingestionService, new ObjectMapper());
    }

    KnowledgeImportCommand(KnowledgeIngestionService ingestionService, ObjectMapper objectMapper) {
        this.ingestionService = ingestionService;
        this.objectMapper = objectMapper == null ? new ObjectMapper() : objectMapper;
    }

    @Override
    public String name() {
        return "knowledge_import";
    }

    @Override
    public String description() {
        return "导入真实知识文件或 JSON/JSONL 结构化资料";
    }

    @Override
    public String execute(List<String> arguments) {
        ImportArguments parsed = parseArguments(arguments);
        List<KnowledgeImportDocument> documents = readDocuments(parsed.path(), parsed.tags());
        if (documents.isEmpty()) {
            throw new CommandException("没有找到可导入的知识资料，支持 .md、.txt、.json、.jsonl");
        }

        int success = 0;
        int duplicate = 0;
        List<String> failures = new ArrayList<>();
        StringBuilder output = new StringBuilder("开始导入真实知识，session_key=")
                .append(parsed.sessionKey())
                .append(System.lineSeparator());

        for (KnowledgeImportDocument document : documents) {
            try {
                KnowledgeIngestionResult result = ingestionService.add(
                        parsed.sessionKey(),
                        document.title(),
                        document.content(),
                        document.sourceType(),
                        document.sourceUrl(),
                        document.tags());
                success++;
                if (result.alreadyExists()) {
                    duplicate++;
                }
                output.append(String.format(Locale.ROOT,
                        "- 成功 document_id=%d title=%s chunks=%d duplicate=%s%n",
                        result.documentId(),
                        result.title(),
                        result.chunkCount(),
                        result.alreadyExists()));
            } catch (RuntimeException exception) {
                failures.add(document.title() + "：" + rootMessage(exception));
            }
        }

        output.append("导入完成：成功 ").append(success)
                .append(" 条，重复 ").append(duplicate)
                .append(" 条，失败 ").append(failures.size()).append(" 条");
        if (!failures.isEmpty()) {
            output.append(System.lineSeparator()).append("失败项：");
            for (String failure : failures) {
                output.append(System.lineSeparator()).append("- ").append(failure);
            }
        }
        return output.toString().stripTrailing();
    }

    private ImportArguments parseArguments(List<String> arguments) {
        if (arguments == null || arguments.size() < 2) {
            throw new CommandException("用法：/knowledge_import <session_key> <file_or_directory_path> [tags]");
        }
        String sessionKey = safe(arguments.get(0));
        if (sessionKey.isBlank()) {
            throw new CommandException("缺少 session_key，用法：/knowledge_import <session_key> <file_or_directory_path> [tags]");
        }

        List<String> remaining = arguments.subList(1, arguments.size());
        for (int end = remaining.size(); end >= 1; end--) {
            Path candidate = Path.of(unquote(String.join(" ", remaining.subList(0, end))));
            if (Files.exists(candidate)) {
                String tags = end >= remaining.size()
                        ? ""
                        : String.join(" ", remaining.subList(end, remaining.size())).strip();
                return new ImportArguments(sessionKey, candidate.toAbsolutePath().normalize(), tags);
            }
        }
        throw new CommandException("导入路径不存在：" + String.join(" ", remaining));
    }

    private List<KnowledgeImportDocument> readDocuments(Path path, String fallbackTags) {
        try {
            if (Files.isDirectory(path)) {
                try (Stream<Path> files = Files.walk(path)) {
                    List<Path> supportedFiles = files
                            .filter(Files::isRegularFile)
                            .filter(this::isSupported)
                            .sorted(Comparator.comparing(value -> value.toAbsolutePath().normalize().toString()))
                            .toList();
                    List<KnowledgeImportDocument> documents = new ArrayList<>();
                    for (Path file : supportedFiles) {
                        documents.addAll(readFile(file, fallbackTags));
                    }
                    return documents;
                }
            }
            if (!Files.isRegularFile(path) || !isSupported(path)) {
                throw new CommandException("不支持的导入路径：" + path);
            }
            return readFile(path, fallbackTags);
        } catch (IOException exception) {
            throw new CommandException("读取知识资料失败：" + exception.getMessage());
        }
    }

    private List<KnowledgeImportDocument> readFile(Path file, String fallbackTags) throws IOException {
        String extension = extension(file);
        if (TEXT_EXTENSIONS.contains(extension)) {
            return List.of(textDocument(file, fallbackTags));
        }
        if (".json".equals(extension)) {
            return jsonDocuments(file, fallbackTags);
        }
        if (".jsonl".equals(extension)) {
            return jsonLinesDocuments(file, fallbackTags);
        }
        return List.of();
    }

    private KnowledgeImportDocument textDocument(Path file, String tags) throws IOException {
        Path source = file.toAbsolutePath().normalize();
        return new KnowledgeImportDocument(
                titleFromFile(file),
                Files.readString(file, StandardCharsets.UTF_8),
                "file",
                source.toString(),
                safe(tags));
    }

    private List<KnowledgeImportDocument> jsonDocuments(Path file, String fallbackTags) throws IOException {
        JsonNode root = objectMapper.readTree(Files.readString(file, StandardCharsets.UTF_8));
        List<KnowledgeImportDocument> documents = new ArrayList<>();
        if (root.isArray()) {
            root.forEach(node -> documents.add(fromJsonNode(node, file, fallbackTags)));
            return documents;
        }
        if (root.isObject()) {
            documents.add(fromJsonNode(root, file, fallbackTags));
            return documents;
        }
        throw new CommandException("JSON 文件必须是对象或数组：" + file);
    }

    private List<KnowledgeImportDocument> jsonLinesDocuments(Path file, String fallbackTags) throws IOException {
        List<KnowledgeImportDocument> documents = new ArrayList<>();
        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        for (int index = 0; index < lines.size(); index++) {
            String line = lines.get(index).strip();
            if (line.isBlank()) {
                continue;
            }
            try {
                documents.add(fromJsonNode(objectMapper.readTree(line), file, fallbackTags));
            } catch (IOException exception) {
                throw new CommandException("JSONL 第 " + (index + 1) + " 行解析失败：" + exception.getMessage());
            }
        }
        return documents;
    }

    private KnowledgeImportDocument fromJsonNode(JsonNode node, Path file, String fallbackTags) {
        if (node == null || !node.isObject()) {
            throw new CommandException("结构化知识条目必须是 JSON 对象：" + file);
        }
        String title = text(node, "title");
        String content = text(node, "content");
        String sourceType = defaultValue(text(node, "sourceType"), "text");
        String sourceUrl = text(node, "sourceUrl");
        String tags = defaultValue(text(node, "tags"), fallbackTags);
        if (title.isBlank()) {
            title = titleFromFile(file);
        }
        if (sourceUrl.isBlank()) {
            sourceUrl = file.toAbsolutePath().normalize().toString();
        }
        return new KnowledgeImportDocument(title, content, sourceType, sourceUrl, tags);
    }

    private boolean isSupported(Path path) {
        String extension = extension(path);
        return TEXT_EXTENSIONS.contains(extension) || STRUCTURED_EXTENSIONS.contains(extension);
    }

    private String titleFromFile(Path file) {
        String filename = file.getFileName() == null ? "未命名知识" : file.getFileName().toString();
        int dot = filename.lastIndexOf('.');
        return dot > 0 ? filename.substring(0, dot) : filename;
    }

    private String extension(Path path) {
        String name = path.getFileName() == null ? "" : path.getFileName().toString().toLowerCase(Locale.ROOT);
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot);
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? "" : value.asText("").strip();
    }

    private String defaultValue(String value, String fallback) {
        String text = safe(value);
        return text.isBlank() ? safe(fallback) : text;
    }

    private String unquote(String value) {
        String text = safe(value);
        if (text.length() >= 2 && ((text.startsWith("\"") && text.endsWith("\""))
                || (text.startsWith("'") && text.endsWith("'")))) {
            return text.substring(1, text.length() - 1);
        }
        return text;
    }

    private String safe(String value) {
        return value == null ? "" : value.strip();
    }

    private String rootMessage(Throwable exception) {
        Throwable current = exception;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }

    private record ImportArguments(String sessionKey, Path path, String tags) {
    }
}
