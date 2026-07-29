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

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class KnowledgeSeedCommandTests {

    @Test
    void seedsBuiltInKnowledgeSamplesAndMakesThemSearchable() {
        FakeEmbeddingService embeddingService = new FakeEmbeddingService();
        FakeVectorStore vectorStore = new FakeVectorStore(embeddingService);
        FakeKnowledgeRepository repository = new FakeKnowledgeRepository();
        KnowledgeIngestionService ingestionService = new KnowledgeIngestionService(
                repository,
                new KnowledgeChunkService(new KnowledgeProperties(120, 20, 5, 6000, 0.2)),
                embeddingService,
                vectorStore);
        KnowledgeSearchService searchService = new KnowledgeSearchService(
                embeddingService,
                vectorStore,
                new KnowledgeProperties(120, 20, 5, 6000, 0.2));

        KnowledgeSeedCommand command = new KnowledgeSeedCommand(ingestionService);
        String reply = new CommandDispatcher(new CommandRegistry(List.of(command))).dispatch("/knowledge_seed demo-rag");

        assertThat(reply).contains("导入完成：5 条样本", "session_key=demo-rag");
        assertThat(repository.documents).hasSize(5);
        assertThat(searchService.search("demo-rag", "RAG 工作流", 5, ""))
                .extracting(KnowledgeSearchResult::title)
                .contains("OpenClaw RAG 工作流");
        assertThat(searchService.search("demo-rag", "Qdrant 调优", 5, ""))
                .extracting(KnowledgeSearchResult::title)
                .contains("Qdrant 检索调优笔记");
    }

    private static final class FakeEmbeddingService implements KnowledgeEmbeddingService {

        private String lastQuery = "";
        private final List<String> embeddedTexts = new ArrayList<>();

        @Override
        public List<Float> embed(String text) {
            lastQuery = text == null ? "" : text;
            embeddedTexts.add(lastQuery);
            return List.of(0.1f, 0.2f, 0.3f);
        }
    }

    private static final class FakeVectorStore implements VectorStore {

        private final FakeEmbeddingService embeddingService;
        private final List<KnowledgeVector> vectors = new ArrayList<>();
        private double score = 0.93;

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
                            score))
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
