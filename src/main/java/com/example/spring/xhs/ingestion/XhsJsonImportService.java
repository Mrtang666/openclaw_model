package com.example.spring.xhs.ingestion;

import com.example.spring.xhs.model.XhsCommentImport;
import com.example.spring.xhs.model.XhsImportResult;
import com.example.spring.xhs.model.XhsMetrics;
import com.example.spring.xhs.model.XhsPostImport;
import com.example.spring.xhs.model.XhsSourceType;
import com.example.spring.xhs.link.XhsAccessUrlPolicy;
import com.example.spring.xhs.link.XhsImageUrlPolicy;
import com.example.spring.xhs.repository.XhsOpinionRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class XhsJsonImportService {

    private static final ZoneId DEFAULT_ZONE = ZoneId.of("Asia/Shanghai");
    private static final Set<String> SENSITIVE_FIELDS = Set.of(
            "user_id", "authorId", "nickname", "avatar", "home_url", "red_id",
            "cookie", "cookies", "web_session", "xsec_token", "phone");

    private final ObjectMapper objectMapper;
    private final XhsOpinionRepository repository;
    private final XhsAuthorKeyHasher authorKeyHasher;

    public XhsJsonImportService(
            ObjectMapper objectMapper,
            XhsOpinionRepository repository,
            XhsAuthorKeyHasher authorKeyHasher) {
        this.objectMapper = objectMapper;
        this.repository = repository;
        this.authorKeyHasher = authorKeyHasher;
    }

    @Transactional
    public XhsImportResult importJson(String projectKey, String projectName, InputStream inputStream) {
        return importJson(projectKey, projectName, "", "", inputStream);
    }

    @Transactional
    public XhsImportResult importJson(String projectKey, String projectName, String jobKey,
                                      String keyword, InputStream inputStream) {
        if (inputStream == null) {
            throw new IllegalArgumentException("导入文件不能为空");
        }
        JsonNode root = read(inputStream);
        XhsSourceType sourceType = XhsSourceType.from(text(root, "source", "sourceType"));
        Instant batchCollectedAt = instant(root, "collectedAt", "collected_at");
        if (batchCollectedAt == null) {
            batchCollectedAt = Instant.now();
        }
        long projectId = repository.ensureProject(projectKey, projectName, batchCollectedAt);

        int postCount = 0;
        int commentCount = 0;
        int skippedCount = 0;
        for (JsonNode postNode : postNodes(root)) {
            try {
                XhsPostImport post = toPost(postNode, sourceType, batchCollectedAt);
                long postId = repository.upsertPost(projectId, post);
                repository.saveMetricSnapshot(postId, post);
                repository.recordSearchHit(postId, jobKey, keyword, batchCollectedAt);
                postCount++;
                int postCommentCount = 0;
                for (JsonNode commentNode : commentNodes(postNode)) {
                    try {
                        XhsCommentImport comment = toComment(commentNode, post.sourcePostId(), batchCollectedAt);
                        repository.upsertComment(postId, comment);
                        XhsCommentRiskClassifier.Result commentRisk =
                                XhsCommentRiskClassifier.classify(comment.content());
                        repository.recordCommentAnalysis(postId, comment.sourceCommentId(),
                                commentRisk.sentiment(), commentRisk.riskScore(),
                                commentRisk.negative(), batchCollectedAt);
                        commentCount++;
                        postCommentCount++;
                    } catch (IllegalArgumentException ignored) {
                        skippedCount++;
                    }
                }
                List<String> images = imageUrls(postNode);
                for (int imageOrder = 0; imageOrder < images.size(); imageOrder++) {
                    repository.recordPostImage(
                            postId, imageOrder, images.get(imageOrder), batchCollectedAt);
                }
                repository.updateCollectionCompleteness(postId, post.metrics().commentCount(),
                        postCommentCount, images.size(), batchCollectedAt);
            } catch (IllegalArgumentException ignored) {
                skippedCount++;
            }
        }
        return new XhsImportResult(projectKey.strip(), sourceType, postCount, commentCount, skippedCount);
    }

    private List<String> imageUrls(JsonNode post) {
        JsonNode images = first(post, "images", "image_list", "imageList");
        if (images == null || !images.isArray()) {
            return List.of();
        }
        LinkedHashSet<String> values = new LinkedHashSet<>();
        for (JsonNode image : images) {
            String value = image.isTextual() ? image.asText("") :
                    text(image, "url", "url_default", "urlDefault", "url_pre", "urlPre");
            addImageUrl(values, value);
            JsonNode variants = first(image, "info_list", "infoList");
            if (variants != null && variants.isArray()) {
                for (JsonNode variant : variants) {
                    addImageUrl(values, text(variant, "url", "url_default", "url_pre"));
                    if (!values.isEmpty()) {
                        break;
                    }
                }
            }
        }
        return List.copyOf(values);
    }

    private void addImageUrl(Set<String> values, String value) {
        String safe = XhsImageUrlPolicy.sanitize(value);
        if (!safe.isBlank()) {
            values.add(safe);
        }
    }

    private XhsPostImport toPost(JsonNode node, XhsSourceType sourceType, Instant collectedAt) {
        return new XhsPostImport(
                sourceType,
                text(node, "sourcePostId", "note_id", "id"),
                canonicalSourceUrl(text(node, "sourceUrl", "note_url", "url"), text(node, "sourcePostId", "note_id", "id")),
                XhsAccessUrlPolicy.sanitize(text(node, "accessUrl", "access_url"),
                        text(node, "sourcePostId", "note_id", "id")),
                authorKeyHasher.hash(text(node, "authorId", "user_id")),
                text(node, "title"),
                text(node, "content", "desc"),
                text(node, "noteType", "note_type", "type"),
                stringList(node, "tags"),
                instant(node, "publishedAt", "upload_time", "create_time"),
                collectedAt,
                new XhsMetrics(
                        number(node, "likedCount", "liked_count"),
                        number(node, "collectedCount", "collected_count"),
                        number(node, "commentCount", "comment_count"),
                        number(node, "shareCount", "share_count")),
                sanitizedJson(node));
    }

    private XhsCommentImport toComment(JsonNode node, String postId, Instant collectedAt) {
        return new XhsCommentImport(
                firstNonBlank(text(node, "sourcePostId", "note_id"), postId),
                text(node, "sourceCommentId", "comment_id", "id"),
                text(node, "parentCommentId", "parent_comment_id", "root_comment_id"),
                authorKeyHasher.hash(text(node, "authorId", "user_id")),
                text(node, "content"),
                number(node, "likedCount", "like_count"),
                instant(node, "publishedAt", "upload_time", "create_time"),
                collectedAt,
                sanitizedJson(node));
    }

    private JsonNode read(InputStream inputStream) {
        try {
            return objectMapper.readTree(inputStream);
        } catch (IOException exception) {
            throw new IllegalArgumentException("小红书导入文件不是有效 JSON", exception);
        }
    }

    private List<JsonNode> postNodes(JsonNode root) {
        if (root == null || root.isNull()) {
            return List.of();
        }
        JsonNode posts = root.isArray() ? root : first(root, "posts", "records", "items");
        if (posts == null || !posts.isArray()) {
            return List.of();
        }
        List<JsonNode> result = new ArrayList<>();
        posts.forEach(result::add);
        return result;
    }

    private List<JsonNode> commentNodes(JsonNode post) {
        JsonNode comments = first(post, "comments", "comment_list");
        if (comments == null || !comments.isArray()) {
            return List.of();
        }
        List<JsonNode> result = new ArrayList<>();
        comments.forEach(comment -> {
            result.add(comment);
            JsonNode children = first(comment, "sub_comments", "children");
            if (children != null && children.isArray()) {
                children.forEach(result::add);
            }
        });
        return result;
    }

    private List<String> stringList(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        if (value == null || value.isNull()) {
            return List.of();
        }
        if (value.isArray()) {
            List<String> values = new ArrayList<>();
            value.forEach(item -> {
                String text = item.isTextual() ? item.asText().strip() : item.toString();
                if (!text.isBlank()) {
                    values.add(text);
                }
            });
            return values;
        }
        String text = value.asText("").strip();
        if (text.isBlank()) {
            return List.of();
        }
        return java.util.Arrays.stream(text.split("[,，#]"))
                .map(String::strip)
                .filter(item -> !item.isBlank())
                .distinct()
                .toList();
    }

    private long number(JsonNode node, String... names) {
        JsonNode value = first(node, names);
        if (value == null || value.isNull()) {
            return 0;
        }
        if (value.isNumber()) {
            return Math.max(0, value.asLong());
        }
        String text = value.asText("").replace(",", "").strip();
        if (text.endsWith("万")) {
            return decimalMultiplier(text.substring(0, text.length() - 1), 10_000);
        }
        if (text.endsWith("k") || text.endsWith("K")) {
            return decimalMultiplier(text.substring(0, text.length() - 1), 1_000);
        }
        try {
            return Math.max(0, Long.parseLong(text));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private long decimalMultiplier(String value, long multiplier) {
        try {
            return Math.max(0, Math.round(Double.parseDouble(value) * multiplier));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private Instant instant(JsonNode node, String... names) {
        JsonNode value = first(node, names);
        if (value == null || value.isNull()) {
            return null;
        }
        if (value.isNumber()) {
            long epoch = value.asLong();
            return epoch > 10_000_000_000L ? Instant.ofEpochMilli(epoch) : Instant.ofEpochSecond(epoch);
        }
        String text = value.asText("").strip();
        if (text.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(text);
        } catch (DateTimeParseException ignored) {
            // Spider_XHS exports local timestamps without an offset.
        }
        for (DateTimeFormatter formatter : List.of(
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
                DateTimeFormatter.ISO_LOCAL_DATE_TIME)) {
            try {
                return LocalDateTime.parse(text, formatter).atZone(DEFAULT_ZONE).toInstant();
            } catch (DateTimeParseException ignored) {
                // Try the next supported format.
            }
        }
        try {
            long epoch = Long.parseLong(text);
            return epoch > 10_000_000_000L ? Instant.ofEpochMilli(epoch) : Instant.ofEpochSecond(epoch);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private String text(JsonNode node, String... names) {
        JsonNode value = first(node, names);
        return value == null || value.isNull() ? "" : value.asText("").strip();
    }

    private JsonNode first(JsonNode node, String... names) {
        if (node == null || names == null) {
            return null;
        }
        for (String name : names) {
            JsonNode value = node.get(name);
            if (value != null && !value.isNull()) {
                return value;
            }
        }
        return null;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.strip();
            }
        }
        return "";
    }

    private String canonicalSourceUrl(String sourceUrl, String sourcePostId) {
        if (sourceUrl == null || sourceUrl.isBlank()) {
            return sourcePostId == null || sourcePostId.isBlank()
                    ? ""
                    : "https://www.xiaohongshu.com/explore/" + sourcePostId.strip();
        }
        try {
            java.net.URI uri = java.net.URI.create(sourceUrl.strip());
            if (uri.getHost() != null && uri.getHost().toLowerCase(java.util.Locale.ROOT).endsWith("xiaohongshu.com")) {
                return new java.net.URI(uri.getScheme(), uri.getAuthority(), uri.getPath(), null, null).toString();
            }
        } catch (IllegalArgumentException | java.net.URISyntaxException ignored) {
            // Preserve non-URL source references as supplied by an authorized provider.
        }
        return sourceUrl.strip();
    }

    private String sanitizedJson(JsonNode value) {
        JsonNode copy = value == null ? objectMapper.createObjectNode() : value.deepCopy();
        sanitize(copy);
        return copy.toString();
    }

    private void sanitize(JsonNode node) {
        if (node instanceof ObjectNode objectNode) {
            Iterator<Map.Entry<String, JsonNode>> fields = objectNode.fields();
            List<String> remove = new ArrayList<>();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                if (SENSITIVE_FIELDS.contains(field.getKey())) {
                    remove.add(field.getKey());
                } else if (field.getValue().isTextual()
                        && (field.getKey().endsWith("url") || field.getKey().endsWith("_url"))) {
                    objectNode.put(field.getKey(), canonicalSourceUrl(field.getValue().asText(), ""));
                } else {
                    sanitize(field.getValue());
                }
            }
            remove.forEach(objectNode::remove);
        } else if (node instanceof ArrayNode arrayNode) {
            arrayNode.forEach(this::sanitize);
        }
    }
}
