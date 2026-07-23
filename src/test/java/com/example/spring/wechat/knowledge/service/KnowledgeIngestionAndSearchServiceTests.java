package com.example.spring.wechat.knowledge.service;

import com.example.spring.wechat.knowledge.config.KnowledgeProperties;
import com.example.spring.wechat.knowledge.model.KnowledgeDocument;
import com.example.spring.wechat.knowledge.model.KnowledgeSearchResult;
import com.example.spring.wechat.knowledge.repository.KnowledgeRepository;
import com.example.spring.wechat.knowledge.vector.KnowledgeVector;
import com.example.spring.wechat.knowledge.vector.VectorStore;
import com.example.spring.chat.ChatService;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class KnowledgeIngestionAndSearchServiceTests {

    @Test
    void ingestsTextAndSearchesOnlyCurrentSessionKnowledge() {
        FakeKnowledgeRepository repository = new FakeKnowledgeRepository();
        FakeVectorStore vectorStore = new FakeVectorStore();
        FakeEmbeddingService embeddingService = new FakeEmbeddingService();
        KnowledgeChunkService chunkService = new KnowledgeChunkService(new KnowledgeProperties(80, 10, 5, 6000, 0.2));
        KnowledgeIngestionService ingestionService = new KnowledgeIngestionService(
                repository,
                chunkService,
                embeddingService,
                vectorStore);
        KnowledgeSearchService searchService = new KnowledgeSearchService(
                embeddingService,
                vectorStore,
                new KnowledgeProperties(80, 10, 5, 6000, 0.2));

        var addResult = ingestionService.add("user-1", "项目说明", "OpenClaw 使用 Function Calling 调用工具。", "text", "", "agent");
        vectorStore.vectors.add(new KnowledgeVector(
                "other-1",
                List.of(0.1f, 0.2f, 0.3f),
                "user-2",
                99,
                "其他资料",
                0,
                "不应该被 user-1 搜到",
                "text",
                "",
                List.of()));

        var results = searchService.search("user-1", "Function Calling 工具调用", 5, "");

        assertThat(addResult.alreadyExists()).isFalse();
        assertThat(repository.documents).hasSize(1);
        assertThat(results).hasSize(1);
        assertThat(results.get(0).title()).isEqualTo("项目说明");
        assertThat(results.get(0).content()).contains("Function Calling");
    }

    @Test
    void enhancesMetadataForLongWebKnowledgeBeforeIngestion() {
        FakeKnowledgeRepository repository = new FakeKnowledgeRepository();
        FakeVectorStore vectorStore = new FakeVectorStore();
        FakeEmbeddingService embeddingService = new FakeEmbeddingService();
        KnowledgeChunkService chunkService = new KnowledgeChunkService(new KnowledgeProperties(80, 10, 5, 6000, 0.2));
        ChatService chatService = mock(ChatService.class);
        when(chatService.reply(org.mockito.ArgumentMatchers.contains("Qdrant Java")))
                .thenReturn("""
                        {"title":"Qdrant Java 接入指南","summary":"介绍 Qdrant Java 客户端接入流程","tags":["Qdrant","Java","向量数据库"]}
                        """);
        KnowledgeIngestionService ingestionService = new KnowledgeIngestionService(
                repository,
                chunkService,
                embeddingService,
                vectorStore,
                new KnowledgeMetadataEnhancer(chatService));

        ingestionService.add(
                "user-1",
                "原始网页标题",
                "Qdrant Java ".repeat(80),
                "web",
                "https://example.com/qdrant",
                "web");

        assertThat(repository.documents).hasSize(1);
        assertThat(repository.documents.get(0).title()).isEqualTo("Qdrant Java 接入指南");
        assertThat(repository.documents.get(0).tags()).contains("Qdrant", "Java", "向量数据库");
    }

    @Test
    void keepsRuleBasedMetadataForShortTextKnowledge() {
        FakeKnowledgeRepository repository = new FakeKnowledgeRepository();
        FakeVectorStore vectorStore = new FakeVectorStore();
        FakeEmbeddingService embeddingService = new FakeEmbeddingService();
        KnowledgeChunkService chunkService = new KnowledgeChunkService(new KnowledgeProperties(80, 10, 5, 6000, 0.2));
        ChatService chatService = mock(ChatService.class);
        KnowledgeIngestionService ingestionService = new KnowledgeIngestionService(
                repository,
                chunkService,
                embeddingService,
                vectorStore,
                new KnowledgeMetadataEnhancer(chatService));

        ingestionService.add("user-1", "我的偏好", "我喜欢简洁回答", "text", "", "preference");

        assertThat(repository.documents.get(0).title()).isEqualTo("我的偏好");
        assertThat(repository.documents.get(0).tags()).isEqualTo("preference");
    }

    @Test
    void searchUsesMultiplePlannedQueriesAndRemovesDuplicateChunks() {
        FakeVectorStore vectorStore = new FakeVectorStore();
        FakeEmbeddingService embeddingService = new FakeEmbeddingService();
        KnowledgeQueryPlanner planner = mock(KnowledgeQueryPlanner.class);
        when(planner.planQueries("Qdrant 怎么接入 Java")).thenReturn(List.of(
                "Qdrant Java 接入",
                "Qdrant Client 配置"));
        vectorStore.vectors.add(new KnowledgeVector(
                "doc-1-0",
                List.of(0.1f, 0.2f, 0.3f),
                "user-1",
                1,
                "Qdrant 指南",
                0,
                "Qdrant Java Client 配置步骤",
                "web",
                "https://example.com/qdrant",
                List.of("Qdrant")));

        KnowledgeSearchService searchService = new KnowledgeSearchService(
                embeddingService,
                vectorStore,
                new KnowledgeProperties(80, 10, 5, 6000, 0.7),
                planner);

        var results = searchService.search("user-1", "Qdrant 怎么接入 Java", 5, "");

        assertThat(results).hasSize(1);
        assertThat(embeddingService.embeddedTexts).containsExactly("Qdrant Java 接入", "Qdrant Client 配置");
        assertThat(results.get(0).sourceUrl()).isEqualTo("https://example.com/qdrant");
    }

    @Test
    void searchFiltersResultsBelowMinimumScore() {
        FakeVectorStore vectorStore = new FakeVectorStore();
        vectorStore.score = 0.41;
        FakeEmbeddingService embeddingService = new FakeEmbeddingService();
        vectorStore.vectors.add(new KnowledgeVector(
                "doc-1-0",
                List.of(0.1f, 0.2f, 0.3f),
                "user-1",
                1,
                "低相关资料",
                0,
                "内容",
                "text",
                "",
                List.of()));
        KnowledgeSearchService searchService = new KnowledgeSearchService(
                embeddingService,
                vectorStore,
                new KnowledgeProperties(80, 10, 5, 6000, 0.7),
                new KnowledgeQueryPlanner(null));

        var results = searchService.search("user-1", "完全不相关的问题", 5, "");

        assertThat(results).isEmpty();
    }

    private static final class FakeEmbeddingService implements KnowledgeEmbeddingService {

        private final List<String> embeddedTexts = new ArrayList<>();

        @Override
        public List<Float> embed(String text) {
            embeddedTexts.add(text);
            return List.of(0.1f, 0.2f, 0.3f);
        }
    }

    private static final class FakeVectorStore implements VectorStore {

        private final List<KnowledgeVector> vectors = new ArrayList<>();
        private double score = 0.99;

        @Override
        public void upsert(List<KnowledgeVector> values) {
            vectors.addAll(values);
        }

        @Override
        public List<KnowledgeSearchResult> search(String sessionKey, List<Float> queryVector, int topK, List<String> tags) {
            return vectors.stream()
                    .filter(vector -> vector.sessionKey().equals(sessionKey))
                    .limit(topK)
                    .map(vector -> new KnowledgeSearchResult(
                            vector.documentId(),
                            vector.title(),
                            vector.chunkIndex(),
                            vector.content(),
                            vector.sourceType(),
                            vector.sourceUrl(),
                            score))
                    .toList();
        }

        @Override
        public void deleteDocument(String sessionKey, long documentId) {
            vectors.removeIf(vector -> vector.sessionKey().equals(sessionKey) && vector.documentId() == documentId);
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
