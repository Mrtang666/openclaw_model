package com.example.spring.cli.command.impl;

import com.example.spring.cli.command.core.CommandDispatcher;
import com.example.spring.cli.command.core.CommandRegistry;
import com.example.spring.wechat.knowledge.config.KnowledgeProperties;
import com.example.spring.wechat.knowledge.model.KnowledgeDocument;
import com.example.spring.wechat.knowledge.model.KnowledgeSearchResult;
import com.example.spring.wechat.knowledge.repository.KnowledgeRepository;
import com.example.spring.wechat.knowledge.service.KnowledgeChunkService;
import com.example.spring.wechat.knowledge.service.KnowledgeEmbeddingService;
import com.example.spring.wechat.knowledge.service.KnowledgeIngestionService;
import com.example.spring.wechat.knowledge.service.KnowledgeSearchService;
import com.example.spring.wechat.knowledge.vector.KnowledgeVector;
import com.example.spring.wechat.knowledge.vector.VectorStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class KnowledgeImportCommandTests {

    @TempDir
    Path tempDir;

    @Test
    void importsSingleTextFileAsRealKnowledge() throws Exception {
        Path source = tempDir.resolve("rag-notes.txt");
        Files.writeString(source, "RAG 真实资料：检索、增强、生成都要走正式知识库链路。");
        TestKnowledgeServices services = new TestKnowledgeServices();
        KnowledgeImportCommand command = new KnowledgeImportCommand(services.ingestionService());

        String reply = command.execute(List.of("real-user", source.toString(), "rag,real"));

        assertThat(reply).contains("导入完成", "成功 1 条", "失败 0 条");
        assertThat(services.repository.documents).hasSize(1);
        KnowledgeDocument document = services.repository.documents.get(0);
        assertThat(document.title()).isEqualTo("rag-notes");
        assertThat(document.sourceType()).isEqualTo("file");
        assertThat(document.sourceUrl()).isEqualTo(source.toAbsolutePath().normalize().toString());
        assertThat(document.tags()).isEqualTo("rag,real");
        assertThat(services.searchService().search("real-user", "正式知识库链路", 5, ""))
                .extracting(KnowledgeSearchResult::title)
                .contains("rag-notes");
    }

    @Test
    void importsMarkdownFilesFromDirectoryRecursively() throws Exception {
        Path nested = Files.createDirectories(tempDir.resolve("project").resolve("ops"));
        Files.writeString(tempDir.resolve("project").resolve("README.md"), "# 项目流程\n微信消息先走 RAG，再调用 LLM。");
        Files.writeString(nested.resolve("deploy.txt"), "部署资料：生产数据导入需要记录来源路径。");
        Files.writeString(nested.resolve("ignore.log"), "不应该导入");
        TestKnowledgeServices services = new TestKnowledgeServices();
        KnowledgeImportCommand command = new KnowledgeImportCommand(services.ingestionService());

        String reply = new CommandDispatcher(new CommandRegistry(List.of(command)))
                .dispatch("/knowledge_import real-user " + tempDir.resolve("project"));

        assertThat(reply).contains("成功 2 条", "失败 0 条");
        assertThat(services.repository.documents)
                .extracting(KnowledgeDocument::title)
                .containsExactlyInAnyOrder("README", "deploy");
        assertThat(services.searchService().search("real-user", "生产数据导入", 5, ""))
                .extracting(KnowledgeSearchResult::title)
                .contains("deploy");
    }

    @Test
    void importsJsonArrayAndJsonLinesDocuments() throws Exception {
        Path json = tempDir.resolve("knowledge.json");
        Files.writeString(json, """
                [
                  {
                    "title": "项目部署说明",
                    "content": "真实部署资料需要说明环境变量、数据库和向量库配置。",
                    "sourceType": "file",
                    "sourceUrl": "docs/deploy.md",
                    "tags": "deploy,real"
                  }
                ]
                """);
        Path jsonl = tempDir.resolve("knowledge.jsonl");
        Files.writeString(jsonl, """
                {"title":"Qdrant 调优","content":"Qdrant 调优重点包括 topK、minScore 和 chunk overlap。","sourceType":"note","sourceUrl":"notes/qdrant.md","tags":"qdrant,real"}
                {"title":"微信 RAG","content":"微信问题可以先检索真实知识，再增强上下文。","sourceType":"note","sourceUrl":"notes/wechat-rag.md","tags":"wechat,rag"}
                """);
        TestKnowledgeServices services = new TestKnowledgeServices();
        KnowledgeImportCommand command = new KnowledgeImportCommand(services.ingestionService());

        String jsonReply = command.execute(List.of("real-user", json.toString()));
        String jsonlReply = command.execute(List.of("real-user", jsonl.toString()));

        assertThat(jsonReply).contains("成功 1 条");
        assertThat(jsonlReply).contains("成功 2 条");
        assertThat(services.repository.documents)
                .extracting(KnowledgeDocument::title)
                .contains("项目部署说明", "Qdrant 调优", "微信 RAG");
        assertThat(services.searchService().search("real-user", "chunk overlap", 5, ""))
                .extracting(KnowledgeSearchResult::title)
                .contains("Qdrant 调优");
    }

    @Test
    void continuesImportWhenOneStructuredDocumentFails() throws Exception {
        Path jsonl = tempDir.resolve("mixed.jsonl");
        Files.writeString(jsonl, """
                {"title":"有效资料","content":"这条真实资料可以正常入库。","tags":"real"}
                {"title":"空资料","content":"   ","tags":"real"}
                """);
        TestKnowledgeServices services = new TestKnowledgeServices();
        KnowledgeImportCommand command = new KnowledgeImportCommand(services.ingestionService());

        String reply = command.execute(List.of("real-user", jsonl.toString()));

        assertThat(reply).contains("成功 1 条", "失败 1 条", "空资料");
        assertThat(services.repository.documents)
                .extracting(KnowledgeDocument::title)
                .containsExactly("有效资料");
    }

    private static final class TestKnowledgeServices {

        private final FakeKnowledgeRepository repository = new FakeKnowledgeRepository();
        private final FakeEmbeddingService embeddingService = new FakeEmbeddingService();
        private final FakeVectorStore vectorStore = new FakeVectorStore(embeddingService);

        KnowledgeIngestionService ingestionService() {
            return new KnowledgeIngestionService(
                    repository,
                    new KnowledgeChunkService(new KnowledgeProperties(120, 20, 5, 6000, 0.2)),
                    embeddingService,
                    vectorStore);
        }

        KnowledgeSearchService searchService() {
            return new KnowledgeSearchService(
                    embeddingService,
                    vectorStore,
                    new KnowledgeProperties(120, 20, 5, 6000, 0.2));
        }
    }

    private static final class FakeEmbeddingService implements KnowledgeEmbeddingService {

        private String lastQuery = "";

        @Override
        public List<Float> embed(String text) {
            lastQuery = text == null ? "" : text;
            return List.of(0.1f, 0.2f, 0.3f);
        }
    }

    private static final class FakeVectorStore implements VectorStore {

        private final FakeEmbeddingService embeddingService;
        private final List<KnowledgeVector> vectors = new ArrayList<>();

        private FakeVectorStore(FakeEmbeddingService embeddingService) {
            this.embeddingService = embeddingService;
        }

        @Override
        public void upsert(List<KnowledgeVector> values) {
            vectors.addAll(values);
        }

        @Override
        public List<KnowledgeSearchResult> search(String sessionKey, List<Float> queryVector, int topK, List<String> tags) {
            String query = normalize(embeddingService.lastQuery);
            return vectors.stream()
                    .filter(vector -> vector.sessionKey().equals(sessionKey))
                    .filter(vector -> matches(query, vector))
                    .limit(topK)
                    .map(vector -> new KnowledgeSearchResult(
                            vector.documentId(),
                            vector.title(),
                            vector.chunkIndex(),
                            vector.content(),
                            vector.sourceType(),
                            vector.sourceUrl(),
                            0.93))
                    .toList();
        }

        @Override
        public void deleteDocument(String sessionKey, long documentId) {
            vectors.removeIf(vector -> vector.sessionKey().equals(sessionKey) && vector.documentId() == documentId);
        }

        private boolean matches(String query, KnowledgeVector vector) {
            if (query.isBlank()) {
                return true;
            }
            String haystack = normalize(vector.title() + " " + vector.content());
            for (String token : query.split("[\\s,，。？?；;、]+")) {
                String value = normalize(token);
                if (!value.isBlank() && haystack.contains(value)) {
                    return true;
                }
            }
            return haystack.contains(query);
        }

        private String normalize(String value) {
            return value == null ? "" : value.toLowerCase(java.util.Locale.ROOT).trim();
        }
    }

    private static final class FakeKnowledgeRepository implements KnowledgeRepository {

        private final List<KnowledgeDocument> documents = new ArrayList<>();
        private long nextId = 1;

        @Override
        public Optional<KnowledgeDocument> findActiveByHash(String sessionKey, String contentHash) {
            return documents.stream()
                    .filter(document -> document.sessionKey().equals(sessionKey))
                    .filter(document -> document.contentHash().equals(contentHash))
                    .findFirst();
        }

        @Override
        public KnowledgeDocument createDocument(String sessionKey, String title, String sourceType, String sourceUrl, String tags, String contentHash, int chunkCount, Instant now) {
            KnowledgeDocument document = new KnowledgeDocument(nextId++, sessionKey, title, sourceType, sourceUrl, tags, contentHash, chunkCount, now, now, false);
            documents.add(document);
            return document;
        }

        @Override
        public List<KnowledgeDocument> listDocuments(String sessionKey, String keyword, int limit) {
            return documents.stream().limit(limit).toList();
        }

        @Override
        public Optional<KnowledgeDocument> findDocument(String sessionKey, long documentId) {
            return documents.stream().filter(document -> document.id() == documentId && document.sessionKey().equals(sessionKey)).findFirst();
        }

        @Override
        public boolean softDelete(String sessionKey, long documentId, Instant now) {
            return documents.removeIf(document -> document.id() == documentId && document.sessionKey().equals(sessionKey));
        }

        @Override
        public void log(String sessionKey, String operation, Long documentId, String queryText, String resultSummary, Instant now) {
        }
    }
}
