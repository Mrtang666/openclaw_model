# Memory Graph Context Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a Memory Graph based WeChat Agent context system that classifies topic relevance, preserves strong-topic recent turns, summarizes older turns into active extracts, retrieves long-term memory from RAG, and keeps final context within 80% of the model input budget.

**Architecture:** Add a new `com.example.spring.wechat.context` package that owns context policy, relevance classification, graph retrieval, budget planning, compression, and final assembly. Keep existing `WechatConversationMemory`, `MySqlWechatMemoryService`, `WechatAgentMemoryContextBuilder`, and `FunctionCallingAgentLoop` compatible by introducing the new orchestrator behind a feature flag with fallback to the current builder.

**Tech Stack:** Java 17, Spring Boot, JDBC/Flyway MySQL migrations, existing `ChatService`, existing Knowledge/RAG services backed by Qdrant, JUnit 5, AssertJ, Mockito.

---

## File Structure

### New package: `src/main/java/com/example/spring/wechat/context`

- `WechatContextProperties.java`  
  Configuration for feature flags, strong/weak windows, summary windows, model budget, and context ratio.

- `RelevanceLevel.java`  
  `STRONG` and `WEAK` relevance enum.

- `ConversationRelevanceDecision.java`  
  Structured result from the relevance classifier.

- `ConversationRelevanceClassifier.java`  
  Interface for relevance classification.

- `RuleBasedRelevanceFallback.java`  
  Safe deterministic fallback for short replies and model failures.

- `ModelConversationRelevanceClassifier.java`  
  Model-backed classifier using `ChatService`, falling back to `RuleBasedRelevanceFallback`.

- `MemoryNodeType.java`  
  Memory graph node type enum.

- `MemoryEdgeType.java`  
  Memory graph edge type enum.

- `MemoryGraphNode.java`  
  Immutable node model.

- `MemoryGraphEdge.java`  
  Immutable edge model.

- `MemoryGraphNodeDraft.java`  
  Input model for inserting nodes.

- `MemoryGraphEdgeDraft.java`  
  Input model for inserting edges.

- `MemoryGraphRepository.java`  
  Repository interface for Memory Graph metadata.

- `MySqlMemoryGraphRepository.java`  
  JDBC implementation.

- `ContextBuildRequest.java`  
  Input model for the context orchestrator.

- `ContextBudgetReport.java`  
  Budget diagnostics included in final context package.

- `WechatContextPackage.java`  
  Final orchestrator output.

- `MemoryGraphRetriever.java`  
  Retrieves recent turns, summaries, active extracts, topics, and graph nodes.

- `LongTermMemoryRetriever.java`  
  Retrieves long-term memory and historical topics from existing RAG/Knowledge services.

- `TokenEstimator.java`  
  Token estimation interface.

- `ConservativeTokenEstimator.java`  
  Safe char-based estimator.

- `ContextBudgetManager.java`  
  Calculates budget and per-section allocations.

- `ContextCompressor.java`  
  Compresses sections by priority when over budget.

- `SlidingWindowSummaryService.java`  
  Creates conversation summaries for older windows.

- `ActiveExtractService.java`  
  Creates active extracts from older summaries and messages.

- `LongTermMemoryExtractor.java`  
  Extracts stable long-term memories from completed/threshold conversations.

- `MemoryGraphMaintenanceService.java`  
  Coordinates summary, topic, active extract, and long-term memory maintenance.

- `MemoryGraphMaintenanceScheduler.java`  
  Periodic background task gated by configuration.

- `WechatContextAssembler.java`  
  Formats selected sections into final structured context text.

- `WechatContextOrchestrator.java`  
  Main integration point used by `WechatConversationService`.

### Existing files to modify

- `src/main/java/com/example/spring/AgentClawApplication.java`  
  Register `WechatContextProperties` with `@EnableConfigurationProperties`.

- `src/main/resources/application.properties`  
  Add context graph configuration defaults.

- `src/main/java/com/example/spring/wechat/conversation/WechatConversationService.java`  
  Inject `WechatContextOrchestrator` through `ObjectProvider`; use it in `conversationContext(...)` with fallback to existing builder.

- `src/main/java/com/example/spring/wechat/knowledge/service/KnowledgeSearchService.java`  
  No signature change required; callers will pass memory tags through the existing `tags` argument.

- `src/main/java/com/example/spring/wechat/knowledge/service/KnowledgeIngestionService.java`  
  No signature change required; memory ingestion will use existing `add(...)`.

### New migration

- `src/main/resources/db/migration/V34__create_memory_graph_tables.sql`

### New tests

- `src/test/java/com/example/spring/wechat/context/WechatContextPropertiesTests.java`
- `src/test/java/com/example/spring/wechat/context/RuleBasedRelevanceFallbackTests.java`
- `src/test/java/com/example/spring/wechat/context/ModelConversationRelevanceClassifierTests.java`
- `src/test/java/com/example/spring/wechat/context/MySqlMemoryGraphRepositoryTests.java`
- `src/test/java/com/example/spring/wechat/context/MemoryGraphRetrieverTests.java`
- `src/test/java/com/example/spring/wechat/context/LongTermMemoryRetrieverTests.java`
- `src/test/java/com/example/spring/wechat/context/ContextBudgetManagerTests.java`
- `src/test/java/com/example/spring/wechat/context/ContextCompressorTests.java`
- `src/test/java/com/example/spring/wechat/context/WechatContextAssemblerTests.java`
- `src/test/java/com/example/spring/wechat/context/WechatContextOrchestratorTests.java`
- Extend `src/test/java/com/example/spring/wechat/conversation/WechatConversationServiceTests.java`

---

## Task 1: Add Context Configuration and Core DTOs

**Files:**
- Create: `src/main/java/com/example/spring/wechat/context/WechatContextProperties.java`
- Create: `src/main/java/com/example/spring/wechat/context/RelevanceLevel.java`
- Create: `src/main/java/com/example/spring/wechat/context/ConversationRelevanceDecision.java`
- Create: `src/main/java/com/example/spring/wechat/context/MemoryNodeType.java`
- Create: `src/main/java/com/example/spring/wechat/context/MemoryEdgeType.java`
- Create: `src/main/java/com/example/spring/wechat/context/MemoryGraphNode.java`
- Create: `src/main/java/com/example/spring/wechat/context/MemoryGraphEdge.java`
- Create: `src/main/java/com/example/spring/wechat/context/MemoryGraphNodeDraft.java`
- Create: `src/main/java/com/example/spring/wechat/context/MemoryGraphEdgeDraft.java`
- Create: `src/main/java/com/example/spring/wechat/context/ContextBuildRequest.java`
- Create: `src/main/java/com/example/spring/wechat/context/ContextBudgetReport.java`
- Create: `src/main/java/com/example/spring/wechat/context/WechatContextPackage.java`
- Modify: `src/main/java/com/example/spring/AgentClawApplication.java`
- Modify: `src/main/resources/application.properties`
- Test: `src/test/java/com/example/spring/wechat/context/WechatContextPropertiesTests.java`

- [ ] **Step 1: Write the failing configuration test**

Create `src/test/java/com/example/spring/wechat/context/WechatContextPropertiesTests.java`:

```java
package com.example.spring.wechat.context;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WechatContextPropertiesTests {

    @Test
    void normalizesInvalidValuesToSafeDefaults() {
        WechatContextProperties properties = new WechatContextProperties(
                true,
                true,
                true,
                0,
                -3,
                0,
                0,
                -1,
                0,
                -100,
                -200,
                0.0);

        assertThat(properties.memoryGraphEnabled()).isTrue();
        assertThat(properties.relevanceClassifierEnabled()).isTrue();
        assertThat(properties.longTermMemoryIngestionEnabled()).isTrue();
        assertThat(properties.strongRecentTurns()).isEqualTo(5);
        assertThat(properties.weakRecentTurns()).isEqualTo(1);
        assertThat(properties.minRecentTurns()).isEqualTo(2);
        assertThat(properties.summaryWindowSize()).isEqualTo(5);
        assertThat(properties.summaryOverlapTurns()).isEqualTo(1);
        assertThat(properties.modelWindowTokens()).isEqualTo(128_000);
        assertThat(properties.outputReserveTokens()).isEqualTo(8_000);
        assertThat(properties.toolLoopReserveTokens()).isEqualTo(12_000);
        assertThat(properties.maxInputRatio()).isEqualTo(0.8);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```powershell
mvn "-Dtest=WechatContextPropertiesTests" test
```

Expected: FAIL because `WechatContextProperties` does not exist.

- [ ] **Step 3: Add core enums and properties**

Create `src/main/java/com/example/spring/wechat/context/RelevanceLevel.java`:

```java
package com.example.spring.wechat.context;

public enum RelevanceLevel {
    STRONG,
    WEAK
}
```

Create `src/main/java/com/example/spring/wechat/context/MemoryNodeType.java`:

```java
package com.example.spring.wechat.context;

public enum MemoryNodeType {
    CONVERSATION_TURN,
    CONVERSATION_SUMMARY,
    ACTIVE_EXTRACT,
    CONVERSATION_TOPIC,
    LONG_TERM_MEMORY,
    TOOL_RESULT_MEMORY,
    RESOURCE_REFERENCE
}
```

Create `src/main/java/com/example/spring/wechat/context/MemoryEdgeType.java`:

```java
package com.example.spring.wechat.context;

public enum MemoryEdgeType {
    NEXT,
    SUMMARIZES,
    DERIVED_FROM,
    SAME_TOPIC,
    REFERENCES,
    SUPERSEDES
}
```

Create `src/main/java/com/example/spring/wechat/context/WechatContextProperties.java`:

```java
package com.example.spring.wechat.context;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "wechat.agent.context")
public record WechatContextProperties(
        boolean memoryGraphEnabled,
        boolean relevanceClassifierEnabled,
        boolean longTermMemoryIngestionEnabled,
        int strongRecentTurns,
        int weakRecentTurns,
        int minRecentTurns,
        int summaryWindowSize,
        int summaryOverlapTurns,
        int modelWindowTokens,
        int outputReserveTokens,
        int toolLoopReserveTokens,
        double maxInputRatio) {

    public WechatContextProperties {
        strongRecentTurns = strongRecentTurns <= 0 ? 5 : strongRecentTurns;
        weakRecentTurns = weakRecentTurns <= 0 ? 1 : weakRecentTurns;
        minRecentTurns = minRecentTurns <= 0 ? 2 : minRecentTurns;
        summaryWindowSize = summaryWindowSize <= 0 ? 5 : summaryWindowSize;
        summaryOverlapTurns = summaryOverlapTurns < 0 ? 1 : summaryOverlapTurns;
        if (summaryOverlapTurns >= summaryWindowSize) {
            summaryOverlapTurns = Math.max(0, summaryWindowSize - 1);
        }
        modelWindowTokens = modelWindowTokens <= 0 ? 128_000 : modelWindowTokens;
        outputReserveTokens = outputReserveTokens <= 0 ? 8_000 : outputReserveTokens;
        toolLoopReserveTokens = toolLoopReserveTokens <= 0 ? 12_000 : toolLoopReserveTokens;
        maxInputRatio = maxInputRatio <= 0 || maxInputRatio > 1 ? 0.8 : maxInputRatio;
    }
}
```

- [ ] **Step 4: Add immutable DTO records**

Create `src/main/java/com/example/spring/wechat/context/ConversationRelevanceDecision.java`:

```java
package com.example.spring.wechat.context;

import java.util.List;

public record ConversationRelevanceDecision(
        RelevanceLevel relevance,
        double confidence,
        String currentTopic,
        String reason,
        List<String> relatedTopics) {

    public ConversationRelevanceDecision {
        relevance = relevance == null ? RelevanceLevel.WEAK : relevance;
        confidence = Math.max(0, Math.min(1, confidence));
        currentTopic = currentTopic == null ? "" : currentTopic.strip();
        reason = reason == null ? "" : reason.strip();
        relatedTopics = relatedTopics == null
                ? List.of()
                : relatedTopics.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(String::strip)
                .distinct()
                .toList();
    }

    public static ConversationRelevanceDecision weak(String reason) {
        return new ConversationRelevanceDecision(RelevanceLevel.WEAK, 0, "", reason, List.of());
    }

    public static ConversationRelevanceDecision strong(String topic, String reason) {
        return new ConversationRelevanceDecision(RelevanceLevel.STRONG, 1, topic, reason, List.of(topic));
    }
}
```

Create `src/main/java/com/example/spring/wechat/context/MemoryGraphNode.java`:

```java
package com.example.spring.wechat.context;

import java.time.Instant;

public record MemoryGraphNode(
        long id,
        String sessionKey,
        Long conversationId,
        MemoryNodeType nodeType,
        String topicKey,
        String title,
        String content,
        String summary,
        double importanceScore,
        double relevanceScore,
        double confidenceScore,
        Long sourceMessageStartId,
        Long sourceMessageEndId,
        String sourceType,
        String sourceRef,
        String tags,
        Instant createdAt,
        Instant updatedAt,
        Instant expiresAt,
        boolean deleted) {

    public MemoryGraphNode {
        sessionKey = clean(sessionKey);
        topicKey = clean(topicKey);
        title = clean(title);
        content = clean(content);
        summary = clean(summary);
        sourceType = clean(sourceType);
        sourceRef = clean(sourceRef);
        tags = clean(tags);
        nodeType = nodeType == null ? MemoryNodeType.CONVERSATION_SUMMARY : nodeType;
    }

    private static String clean(String value) {
        return value == null ? "" : value.strip();
    }
}
```

Create `src/main/java/com/example/spring/wechat/context/MemoryGraphEdge.java`:

```java
package com.example.spring.wechat.context;

import java.time.Instant;

public record MemoryGraphEdge(
        long id,
        String sessionKey,
        long sourceNodeId,
        long targetNodeId,
        MemoryEdgeType edgeType,
        double weight,
        Instant createdAt) {

    public MemoryGraphEdge {
        sessionKey = sessionKey == null ? "" : sessionKey.strip();
        edgeType = edgeType == null ? MemoryEdgeType.REFERENCES : edgeType;
        weight = weight <= 0 ? 1 : weight;
    }
}
```

Create `src/main/java/com/example/spring/wechat/context/MemoryGraphNodeDraft.java`:

```java
package com.example.spring.wechat.context;

import java.time.Instant;

public record MemoryGraphNodeDraft(
        String sessionKey,
        Long conversationId,
        MemoryNodeType nodeType,
        String topicKey,
        String title,
        String content,
        String summary,
        double importanceScore,
        double relevanceScore,
        double confidenceScore,
        Long sourceMessageStartId,
        Long sourceMessageEndId,
        String sourceType,
        String sourceRef,
        String tags,
        Instant expiresAt) {

    public MemoryGraphNodeDraft {
        sessionKey = sessionKey == null ? "" : sessionKey.strip();
        nodeType = nodeType == null ? MemoryNodeType.CONVERSATION_SUMMARY : nodeType;
        topicKey = topicKey == null ? "" : topicKey.strip();
        title = title == null || title.isBlank() ? "未命名记忆" : title.strip();
        content = content == null ? "" : content.strip();
        summary = summary == null ? "" : summary.strip();
        sourceType = sourceType == null ? "" : sourceType.strip();
        sourceRef = sourceRef == null ? "" : sourceRef.strip();
        tags = tags == null ? "" : tags.strip();
        importanceScore = clamp(importanceScore);
        relevanceScore = clamp(relevanceScore);
        confidenceScore = clamp(confidenceScore);
    }

    private static double clamp(double value) {
        return Math.max(0, Math.min(1, value));
    }
}
```

Create `src/main/java/com/example/spring/wechat/context/MemoryGraphEdgeDraft.java`:

```java
package com.example.spring.wechat.context;

public record MemoryGraphEdgeDraft(
        String sessionKey,
        long sourceNodeId,
        long targetNodeId,
        MemoryEdgeType edgeType,
        double weight) {

    public MemoryGraphEdgeDraft {
        sessionKey = sessionKey == null ? "" : sessionKey.strip();
        edgeType = edgeType == null ? MemoryEdgeType.REFERENCES : edgeType;
        weight = weight <= 0 ? 1 : weight;
    }
}
```

Create `src/main/java/com/example/spring/wechat/context/ContextBuildRequest.java`:

```java
package com.example.spring.wechat.context;

import com.example.spring.wechat.conversation.WechatConversationMode;
import com.example.spring.wechat.memory.model.WechatConversationMemory;

public record ContextBuildRequest(
        String sessionKey,
        String userText,
        WechatConversationMemory memory,
        String resourceContext,
        String ragContext,
        WechatConversationMode conversationMode) {

    public ContextBuildRequest {
        sessionKey = sessionKey == null ? "" : sessionKey.strip();
        userText = userText == null ? "" : userText.strip();
        resourceContext = resourceContext == null ? "" : resourceContext.strip();
        ragContext = ragContext == null ? "" : ragContext.strip();
        conversationMode = conversationMode == null ? WechatConversationMode.GENERAL : conversationMode;
    }
}
```

Create `src/main/java/com/example/spring/wechat/context/ContextBudgetReport.java`:

```java
package com.example.spring.wechat.context;

public record ContextBudgetReport(
        int modelWindowTokens,
        int outputReserveTokens,
        int toolLoopReserveTokens,
        int availableInputTokens,
        int contextBudgetTokens,
        int actualTokens,
        boolean compressed) {
}
```

Create `src/main/java/com/example/spring/wechat/context/WechatContextPackage.java`:

```java
package com.example.spring.wechat.context;

import java.util.List;

public record WechatContextPackage(
        RelevanceLevel relevance,
        String finalContextText,
        List<MemoryGraphNode> selectedNodes,
        ContextBudgetReport budgetReport) {

    public WechatContextPackage {
        relevance = relevance == null ? RelevanceLevel.WEAK : relevance;
        finalContextText = finalContextText == null ? "" : finalContextText.strip();
        selectedNodes = selectedNodes == null ? List.of() : List.copyOf(selectedNodes);
    }
}
```

- [ ] **Step 5: Register properties and add defaults**

Modify `src/main/java/com/example/spring/AgentClawApplication.java`. Add `WechatContextProperties.class` to the existing `@EnableConfigurationProperties` list:

```java
WechatContextProperties.class
```

Add this import:

```java
import com.example.spring.wechat.context.WechatContextProperties;
```

Append to `src/main/resources/application.properties`:

```properties
# WeChat Memory Graph context
wechat.agent.context.memory-graph-enabled=${WECHAT_AGENT_CONTEXT_MEMORY_GRAPH_ENABLED:true}
wechat.agent.context.relevance-classifier-enabled=${WECHAT_AGENT_CONTEXT_RELEVANCE_CLASSIFIER_ENABLED:true}
wechat.agent.context.long-term-memory-ingestion-enabled=${WECHAT_AGENT_CONTEXT_LONG_TERM_MEMORY_INGESTION_ENABLED:true}
wechat.agent.context.strong-recent-turns=${WECHAT_AGENT_CONTEXT_STRONG_RECENT_TURNS:5}
wechat.agent.context.weak-recent-turns=${WECHAT_AGENT_CONTEXT_WEAK_RECENT_TURNS:1}
wechat.agent.context.min-recent-turns=${WECHAT_AGENT_CONTEXT_MIN_RECENT_TURNS:2}
wechat.agent.context.summary-window-size=${WECHAT_AGENT_CONTEXT_SUMMARY_WINDOW_SIZE:5}
wechat.agent.context.summary-overlap-turns=${WECHAT_AGENT_CONTEXT_SUMMARY_OVERLAP_TURNS:1}
wechat.agent.context.model-window-tokens=${WECHAT_AGENT_CONTEXT_MODEL_WINDOW_TOKENS:128000}
wechat.agent.context.output-reserve-tokens=${WECHAT_AGENT_CONTEXT_OUTPUT_RESERVE_TOKENS:8000}
wechat.agent.context.tool-loop-reserve-tokens=${WECHAT_AGENT_CONTEXT_TOOL_LOOP_RESERVE_TOKENS:12000}
wechat.agent.context.max-input-ratio=${WECHAT_AGENT_CONTEXT_MAX_INPUT_RATIO:0.8}
```

- [ ] **Step 6: Run tests**

Run:

```powershell
mvn "-Dtest=WechatContextPropertiesTests,ApplicationContextTests" test
```

Expected: PASS.

- [ ] **Step 7: Commit**

```powershell
git add src/main/java/com/example/spring/AgentClawApplication.java src/main/java/com/example/spring/wechat/context src/main/resources/application.properties src/test/java/com/example/spring/wechat/context/WechatContextPropertiesTests.java
git commit -m "feat(context): add memory graph context models"
```

---

## Task 2: Implement Topic Relevance Classification

**Files:**
- Create: `src/main/java/com/example/spring/wechat/context/ConversationRelevanceClassifier.java`
- Create: `src/main/java/com/example/spring/wechat/context/RuleBasedRelevanceFallback.java`
- Create: `src/main/java/com/example/spring/wechat/context/ModelConversationRelevanceClassifier.java`
- Test: `src/test/java/com/example/spring/wechat/context/RuleBasedRelevanceFallbackTests.java`
- Test: `src/test/java/com/example/spring/wechat/context/ModelConversationRelevanceClassifierTests.java`

- [ ] **Step 1: Write failing tests for rule fallback**

Create `src/test/java/com/example/spring/wechat/context/RuleBasedRelevanceFallbackTests.java`:

```java
package com.example.spring.wechat.context;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RuleBasedRelevanceFallbackTests {

    private final RuleBasedRelevanceFallback fallback = new RuleBasedRelevanceFallback();

    @Test
    void treatsShortReferencesAsStrong() {
        ConversationRelevanceDecision decision = fallback.classify(
                "继续",
                List.of("用户：我们在设计 Memory Graph\n助手：好的"),
                List.of("OpenClaw Memory Graph 上下文设计"));

        assertThat(decision.relevance()).isEqualTo(RelevanceLevel.STRONG);
        assertThat(decision.reason()).contains("指代");
    }

    @Test
    void treatsClearlyDifferentTopicAsWeak() {
        ConversationRelevanceDecision decision = fallback.classify(
                "今天杭州天气怎么样",
                List.of("用户：帮我设计 Java 单测\n助手：可以"),
                List.of("Spring Boot 单元测试设计"));

        assertThat(decision.relevance()).isEqualTo(RelevanceLevel.WEAK);
    }
}
```

- [ ] **Step 2: Run rule tests to verify failure**

Run:

```powershell
mvn "-Dtest=RuleBasedRelevanceFallbackTests" test
```

Expected: FAIL because `RuleBasedRelevanceFallback` does not exist.

- [ ] **Step 3: Implement rule fallback**

Create `src/main/java/com/example/spring/wechat/context/ConversationRelevanceClassifier.java`:

```java
package com.example.spring.wechat.context;

import java.util.List;

public interface ConversationRelevanceClassifier {

    ConversationRelevanceDecision classify(
            String userText,
            List<String> recentTurns,
            List<String> recentTopics);
}
```

Create `src/main/java/com/example/spring/wechat/context/RuleBasedRelevanceFallback.java`:

```java
package com.example.spring.wechat.context;

import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class RuleBasedRelevanceFallback implements ConversationRelevanceClassifier {

    private static final List<String> STRONG_REFERENCE_MARKERS = List.of(
            "继续", "可以", "确认", "按刚才", "照刚才", "刚才那个", "刚刚那个",
            "上一个", "第二个", "这个", "那个", "改一下", "再优化", "就这样");

    @Override
    public ConversationRelevanceDecision classify(
            String userText,
            List<String> recentTurns,
            List<String> recentTopics) {
        String text = clean(userText);
        if (text.isBlank()) {
            return ConversationRelevanceDecision.weak("空消息默认弱相关");
        }
        if (containsAny(text, STRONG_REFERENCE_MARKERS)) {
            return ConversationRelevanceDecision.strong(
                    firstTopic(recentTopics),
                    "用户消息包含上下文指代表达，按强相关处理");
        }
        if (sharesTopicKeyword(text, recentTopics) || sharesTopicKeyword(text, recentTurns)) {
            return ConversationRelevanceDecision.strong(
                    firstTopic(recentTopics),
                    "用户消息与近期主题存在关键词重合，按强相关处理");
        }
        return ConversationRelevanceDecision.weak("未发现当前消息与近期主题的明显连续性");
    }

    private boolean sharesTopicKeyword(String text, List<String> values) {
        if (values == null || values.isEmpty()) {
            return false;
        }
        for (String keyword : roughKeywords(text)) {
            if (keyword.length() < 2) {
                continue;
            }
            for (String value : values) {
                if (value != null && value.contains(keyword)) {
                    return true;
                }
            }
        }
        return false;
    }

    private List<String> roughKeywords(String text) {
        return java.util.Arrays.stream(text.split("[\\s，。！？、,.!?：:；;（）()【】\\[\\]\"']+"))
                .map(String::strip)
                .filter(value -> !value.isBlank())
                .toList();
    }

    private boolean containsAny(String text, List<String> markers) {
        for (String marker : markers) {
            if (text.contains(marker)) {
                return true;
            }
        }
        return false;
    }

    private String firstTopic(List<String> topics) {
        return topics == null || topics.isEmpty() ? "" : clean(topics.get(0));
    }

    private String clean(String value) {
        return value == null ? "" : value.strip();
    }
}
```

- [ ] **Step 4: Run rule tests**

Run:

```powershell
mvn "-Dtest=RuleBasedRelevanceFallbackTests" test
```

Expected: PASS.

- [ ] **Step 5: Write failing tests for model classifier**

Create `src/test/java/com/example/spring/wechat/context/ModelConversationRelevanceClassifierTests.java`:

```java
package com.example.spring.wechat.context;

import com.example.spring.chat.ChatService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ModelConversationRelevanceClassifierTests {

    @Test
    void parsesStrongModelDecision() {
        ChatService chatService = mock(ChatService.class);
        when(chatService.reply(contains("只输出 JSON"))).thenReturn("""
                {"relevance":"STRONG","confidence":0.91,"currentTopic":"Memory Graph","reason":"同一主题","relatedTopics":["Memory Graph"]}
                """);
        ModelConversationRelevanceClassifier classifier = new ModelConversationRelevanceClassifier(
                chatService,
                new RuleBasedRelevanceFallback(),
                new WechatContextProperties(true, true, true, 5, 1, 2, 5, 1, 128000, 8000, 12000, 0.8));

        ConversationRelevanceDecision decision = classifier.classify(
                "继续讲活摘",
                List.of("用户：Memory Graph 怎么做\n助手：我们拆成节点"),
                List.of("Memory Graph"));

        assertThat(decision.relevance()).isEqualTo(RelevanceLevel.STRONG);
        assertThat(decision.confidence()).isEqualTo(0.91);
        assertThat(decision.currentTopic()).isEqualTo("Memory Graph");
    }

    @Test
    void fallsBackWhenModelThrows() {
        ChatService chatService = mock(ChatService.class);
        when(chatService.reply(contains("只输出 JSON"))).thenThrow(new IllegalStateException("model down"));
        ModelConversationRelevanceClassifier classifier = new ModelConversationRelevanceClassifier(
                chatService,
                new RuleBasedRelevanceFallback(),
                new WechatContextProperties(true, true, true, 5, 1, 2, 5, 1, 128000, 8000, 12000, 0.8));

        ConversationRelevanceDecision decision = classifier.classify("继续", List.of(), List.of("上下文设计"));

        assertThat(decision.relevance()).isEqualTo(RelevanceLevel.STRONG);
    }
}
```

- [ ] **Step 6: Run model tests to verify failure**

Run:

```powershell
mvn "-Dtest=ModelConversationRelevanceClassifierTests" test
```

Expected: FAIL because `ModelConversationRelevanceClassifier` does not exist.

- [ ] **Step 7: Implement model classifier**

Create `src/main/java/com/example/spring/wechat/context/ModelConversationRelevanceClassifier.java`:

```java
package com.example.spring.wechat.context;

import com.example.spring.chat.ChatService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class ModelConversationRelevanceClassifier implements ConversationRelevanceClassifier {

    private static final Logger log = LoggerFactory.getLogger(ModelConversationRelevanceClassifier.class);

    private final ChatService chatService;
    private final RuleBasedRelevanceFallback fallback;
    private final WechatContextProperties properties;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ModelConversationRelevanceClassifier(
            ChatService chatService,
            RuleBasedRelevanceFallback fallback,
            WechatContextProperties properties) {
        this.chatService = chatService;
        this.fallback = fallback;
        this.properties = properties;
    }

    @Override
    public ConversationRelevanceDecision classify(
            String userText,
            List<String> recentTurns,
            List<String> recentTopics) {
        if (properties == null || !properties.relevanceClassifierEnabled() || chatService == null) {
            return fallback.classify(userText, recentTurns, recentTopics);
        }
        try {
            String reply = chatService.reply(prompt(userText, recentTurns, recentTopics));
            return parse(reply);
        } catch (RuntimeException exception) {
            log.warn("上下文相关性模型判断失败，error={}", rootMessage(exception));
            return fallback.classify(userText, recentTurns, recentTopics);
        }
    }

    private String prompt(String userText, List<String> recentTurns, List<String> recentTopics) {
        return """
                你是微信 Agent 的上下文主题相关性分类器。
                判断“当前用户消息”和“近期上下文主题”是否属于同一主题。
                只输出 JSON，不要输出解释文字。
                JSON 字段：
                - relevance: STRONG 或 WEAK
                - confidence: 0 到 1
                - currentTopic: 当前主题，不能确定则为空字符串
                - reason: 一句话理由
                - relatedTopics: 字符串数组

                判定标准：
                - 强相关：当前消息明显延续近期主题，或通过“继续、刚才、这个、第二个”等表达依赖近期上下文。
                - 弱相关：新话题、不相关、主题跨度很大，或无法可靠判断为同一主题。

                当前用户消息：
                %s

                最近对话：
                %s

                最近主题：
                %s
                """.formatted(clean(userText), join(recentTurns), join(recentTopics));
    }

    private ConversationRelevanceDecision parse(String reply) {
        JsonNode root = objectMapper.readTree(cleanJson(reply));
        RelevanceLevel level = "STRONG".equalsIgnoreCase(root.path("relevance").asText(""))
                ? RelevanceLevel.STRONG
                : RelevanceLevel.WEAK;
        List<String> topics = new ArrayList<>();
        JsonNode related = root.path("relatedTopics");
        if (related.isArray()) {
            for (JsonNode item : related) {
                if (item != null && item.isTextual() && !item.asText().isBlank()) {
                    topics.add(item.asText().strip());
                }
            }
        }
        return new ConversationRelevanceDecision(
                level,
                root.path("confidence").asDouble(0),
                root.path("currentTopic").asText(""),
                root.path("reason").asText(""),
                topics);
    }

    private String cleanJson(String value) {
        String text = clean(value);
        if (text.startsWith("```")) {
            text = text.replaceFirst("^```[a-zA-Z]*", "").replaceFirst("```$", "").strip();
        }
        return text;
    }

    private String join(List<String> values) {
        if (values == null || values.isEmpty()) {
            return "无";
        }
        return String.join(System.lineSeparator(), values.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(String::strip)
                .limit(8)
                .toList());
    }

    private String clean(String value) {
        return value == null ? "" : value.strip();
    }

    private String rootMessage(Throwable exception) {
        Throwable current = exception;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }
}
```

- [ ] **Step 8: Run relevance tests**

Run:

```powershell
mvn "-Dtest=RuleBasedRelevanceFallbackTests,ModelConversationRelevanceClassifierTests" test
```

Expected: PASS.

- [ ] **Step 9: Commit**

```powershell
git add src/main/java/com/example/spring/wechat/context src/test/java/com/example/spring/wechat/context
git commit -m "feat(context): classify conversation topic relevance"
```

---

## Task 3: Add Memory Graph MySQL Storage

**Files:**
- Create: `src/main/resources/db/migration/V34__create_memory_graph_tables.sql`
- Create: `src/main/java/com/example/spring/wechat/context/MemoryGraphRepository.java`
- Create: `src/main/java/com/example/spring/wechat/context/MySqlMemoryGraphRepository.java`
- Test: `src/test/java/com/example/spring/wechat/context/MySqlMemoryGraphRepositoryTests.java`

- [ ] **Step 1: Check migration version**

Run:

```powershell
Get-ChildItem src/main/resources/db/migration -Filter 'V*__*.sql' | Sort-Object Name | Select-Object -Last 5 -ExpandProperty Name
```

Expected: FAIL because `V34__create_memory_graph_tables.sql` does not exist.

- [ ] **Step 2: Write migration**

Create `src/main/resources/db/migration/V34__create_memory_graph_tables.sql`:

```sql
CREATE TABLE memory_graph_nodes (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    session_key VARCHAR(191) NOT NULL,
    conversation_id BIGINT NULL,
    node_type VARCHAR(32) NOT NULL,
    topic_key VARCHAR(191) NULL,
    title VARCHAR(255) NOT NULL,
    content MEDIUMTEXT NOT NULL,
    summary TEXT NULL,
    importance_score DOUBLE NOT NULL DEFAULT 0,
    relevance_score DOUBLE NOT NULL DEFAULT 0,
    confidence_score DOUBLE NOT NULL DEFAULT 0,
    source_message_start_id BIGINT NULL,
    source_message_end_id BIGINT NULL,
    source_type VARCHAR(64) NULL,
    source_ref TEXT NULL,
    tags VARCHAR(512) NULL,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    expires_at DATETIME(3) NULL,
    deleted TINYINT NOT NULL DEFAULT 0,
    KEY idx_memory_nodes_session_type_created (session_key, node_type, created_at),
    KEY idx_memory_nodes_session_topic (session_key, topic_key),
    KEY idx_memory_nodes_conversation_type (conversation_id, node_type),
    KEY idx_memory_nodes_deleted_expires (deleted, expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE memory_graph_edges (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    session_key VARCHAR(191) NOT NULL,
    source_node_id BIGINT NOT NULL,
    target_node_id BIGINT NOT NULL,
    edge_type VARCHAR(32) NOT NULL,
    weight DOUBLE NOT NULL DEFAULT 1,
    created_at DATETIME(3) NOT NULL,
    UNIQUE KEY uk_memory_edges_unique (source_node_id, target_node_id, edge_type),
    KEY idx_memory_edges_source (source_node_id, edge_type),
    KEY idx_memory_edges_target (target_node_id, edge_type),
    KEY idx_memory_edges_session_type (session_key, edge_type),
    CONSTRAINT fk_memory_edges_source
        FOREIGN KEY (source_node_id) REFERENCES memory_graph_nodes(id),
    CONSTRAINT fk_memory_edges_target
        FOREIGN KEY (target_node_id) REFERENCES memory_graph_nodes(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

- [ ] **Step 3: Write failing repository test**

Create `src/test/java/com/example/spring/wechat/context/MySqlMemoryGraphRepositoryTests.java`:

```java
package com.example.spring.wechat.context;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class MySqlMemoryGraphRepositoryTests {

    @Autowired
    private MemoryGraphRepository repository;

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    @BeforeEach
    void clean() {
        jdbcTemplate.update("DELETE FROM memory_graph_edges");
        jdbcTemplate.update("DELETE FROM memory_graph_nodes");
    }

    @Test
    void createsNodesEdgesAndQueriesByType() {
        MemoryGraphNode summary = repository.createNode(new MemoryGraphNodeDraft(
                "session-1",
                42L,
                MemoryNodeType.CONVERSATION_SUMMARY,
                "memory-graph",
                "摘要",
                "第 1-6 轮摘要",
                "摘要",
                0.8,
                0.7,
                0.9,
                1L,
                12L,
                "conversation",
                "conversation://42",
                "memory_type:conversation_summary",
                null));
        MemoryGraphNode extract = repository.createNode(new MemoryGraphNodeDraft(
                "session-1",
                42L,
                MemoryNodeType.ACTIVE_EXTRACT,
                "memory-graph",
                "活摘",
                "和当前主题相关的历史重点",
                "活摘",
                0.9,
                0.95,
                0.9,
                1L,
                12L,
                "conversation_summary",
                "memory://summary/" + summary.id(),
                "memory_type:active_extract",
                null));

        repository.createEdge(new MemoryGraphEdgeDraft(
                "session-1",
                extract.id(),
                summary.id(),
                MemoryEdgeType.DERIVED_FROM,
                1));

        List<MemoryGraphNode> extracts = repository.findRecentNodes(
                "session-1",
                MemoryNodeType.ACTIVE_EXTRACT,
                5);
        List<MemoryGraphEdge> edges = repository.findOutgoingEdges(extract.id(), MemoryEdgeType.DERIVED_FROM);

        assertThat(extracts).extracting(MemoryGraphNode::content)
                .containsExactly("和当前主题相关的历史重点");
        assertThat(edges).hasSize(1);
        assertThat(edges.get(0).targetNodeId()).isEqualTo(summary.id());
    }
}
```

- [ ] **Step 4: Run repository test to verify failure**

Run:

```powershell
mvn "-Dtest=MySqlMemoryGraphRepositoryTests" test
```

Expected: FAIL because repository interface/implementation does not exist or migration is not applied.

- [ ] **Step 5: Implement repository interface**

Create `src/main/java/com/example/spring/wechat/context/MemoryGraphRepository.java`:

```java
package com.example.spring.wechat.context;

import java.util.List;

public interface MemoryGraphRepository {

    MemoryGraphNode createNode(MemoryGraphNodeDraft draft);

    MemoryGraphEdge createEdge(MemoryGraphEdgeDraft draft);

    List<MemoryGraphNode> findRecentNodes(String sessionKey, MemoryNodeType nodeType, int limit);

    List<MemoryGraphNode> findRecentNodesByTopic(String sessionKey, MemoryNodeType nodeType, String topicKey, int limit);

    List<MemoryGraphEdge> findOutgoingEdges(long sourceNodeId, MemoryEdgeType edgeType);

    void softDeleteNode(long nodeId);
}
```

- [ ] **Step 6: Implement MySQL repository**

Create `src/main/java/com/example/spring/wechat/context/MySqlMemoryGraphRepository.java`:

```java
package com.example.spring.wechat.context;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCreator;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.List;

@Repository
public class MySqlMemoryGraphRepository implements MemoryGraphRepository {

    private final JdbcTemplate jdbcTemplate;
    private final Clock clock;

    public MySqlMemoryGraphRepository(JdbcTemplate jdbcTemplate) {
        this(jdbcTemplate, Clock.systemUTC());
    }

    MySqlMemoryGraphRepository(JdbcTemplate jdbcTemplate, Clock clock) {
        this.jdbcTemplate = jdbcTemplate;
        this.clock = clock == null ? Clock.systemUTC() : clock;
    }

    @Override
    public MemoryGraphNode createNode(MemoryGraphNodeDraft draft) {
        Instant now = Instant.now(clock);
        GeneratedKeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(insertNodeStatement(draft, now), keyHolder);
        Number key = keyHolder.getKey();
        long id = key == null ? 0L : key.longValue();
        return new MemoryGraphNode(
                id,
                draft.sessionKey(),
                draft.conversationId(),
                draft.nodeType(),
                draft.topicKey(),
                draft.title(),
                draft.content(),
                draft.summary(),
                draft.importanceScore(),
                draft.relevanceScore(),
                draft.confidenceScore(),
                draft.sourceMessageStartId(),
                draft.sourceMessageEndId(),
                draft.sourceType(),
                draft.sourceRef(),
                draft.tags(),
                now,
                now,
                draft.expiresAt(),
                false);
    }

    private PreparedStatementCreator insertNodeStatement(MemoryGraphNodeDraft draft, Instant now) {
        return connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    """
                            INSERT INTO memory_graph_nodes
                            (session_key, conversation_id, node_type, topic_key, title, content, summary,
                             importance_score, relevance_score, confidence_score, source_message_start_id,
                             source_message_end_id, source_type, source_ref, tags, created_at, updated_at, expires_at, deleted)
                            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)
                            """,
                    Statement.RETURN_GENERATED_KEYS);
            statement.setString(1, draft.sessionKey());
            setLong(statement, 2, draft.conversationId());
            statement.setString(3, draft.nodeType().name());
            statement.setString(4, emptyToNull(draft.topicKey()));
            statement.setString(5, draft.title());
            statement.setString(6, draft.content());
            statement.setString(7, emptyToNull(draft.summary()));
            statement.setDouble(8, draft.importanceScore());
            statement.setDouble(9, draft.relevanceScore());
            statement.setDouble(10, draft.confidenceScore());
            setLong(statement, 11, draft.sourceMessageStartId());
            setLong(statement, 12, draft.sourceMessageEndId());
            statement.setString(13, emptyToNull(draft.sourceType()));
            statement.setString(14, emptyToNull(draft.sourceRef()));
            statement.setString(15, emptyToNull(draft.tags()));
            statement.setTimestamp(16, Timestamp.from(now));
            statement.setTimestamp(17, Timestamp.from(now));
            statement.setTimestamp(18, draft.expiresAt() == null ? null : Timestamp.from(draft.expiresAt()));
            return statement;
        };
    }

    @Override
    public MemoryGraphEdge createEdge(MemoryGraphEdgeDraft draft) {
        Instant now = Instant.now(clock);
        GeneratedKeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    """
                            INSERT INTO memory_graph_edges
                            (session_key, source_node_id, target_node_id, edge_type, weight, created_at)
                            VALUES (?, ?, ?, ?, ?, ?)
                            """,
                    Statement.RETURN_GENERATED_KEYS);
            statement.setString(1, draft.sessionKey());
            statement.setLong(2, draft.sourceNodeId());
            statement.setLong(3, draft.targetNodeId());
            statement.setString(4, draft.edgeType().name());
            statement.setDouble(5, draft.weight());
            statement.setTimestamp(6, Timestamp.from(now));
            return statement;
        }, keyHolder);
        Number key = keyHolder.getKey();
        return new MemoryGraphEdge(
                key == null ? 0L : key.longValue(),
                draft.sessionKey(),
                draft.sourceNodeId(),
                draft.targetNodeId(),
                draft.edgeType(),
                draft.weight(),
                now);
    }

    @Override
    public List<MemoryGraphNode> findRecentNodes(String sessionKey, MemoryNodeType nodeType, int limit) {
        return jdbcTemplate.query(
                """
                        SELECT *
                        FROM memory_graph_nodes
                        WHERE session_key = ? AND node_type = ? AND deleted = 0
                        ORDER BY created_at DESC, id DESC
                        LIMIT ?
                        """,
                this::mapNode,
                clean(sessionKey),
                nodeType.name(),
                safeLimit(limit));
    }

    @Override
    public List<MemoryGraphNode> findRecentNodesByTopic(String sessionKey, MemoryNodeType nodeType, String topicKey, int limit) {
        return jdbcTemplate.query(
                """
                        SELECT *
                        FROM memory_graph_nodes
                        WHERE session_key = ? AND node_type = ? AND topic_key = ? AND deleted = 0
                        ORDER BY relevance_score DESC, created_at DESC, id DESC
                        LIMIT ?
                        """,
                this::mapNode,
                clean(sessionKey),
                nodeType.name(),
                clean(topicKey),
                safeLimit(limit));
    }

    @Override
    public List<MemoryGraphEdge> findOutgoingEdges(long sourceNodeId, MemoryEdgeType edgeType) {
        return jdbcTemplate.query(
                """
                        SELECT *
                        FROM memory_graph_edges
                        WHERE source_node_id = ? AND edge_type = ?
                        ORDER BY weight DESC, id DESC
                        """,
                this::mapEdge,
                sourceNodeId,
                edgeType.name());
    }

    @Override
    public void softDeleteNode(long nodeId) {
        jdbcTemplate.update(
                "UPDATE memory_graph_nodes SET deleted = 1, updated_at = ? WHERE id = ?",
                Timestamp.from(Instant.now(clock)),
                nodeId);
    }

    private MemoryGraphNode mapNode(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        return new MemoryGraphNode(
                rs.getLong("id"),
                rs.getString("session_key"),
                nullableLong(rs, "conversation_id"),
                MemoryNodeType.valueOf(rs.getString("node_type")),
                rs.getString("topic_key"),
                rs.getString("title"),
                rs.getString("content"),
                rs.getString("summary"),
                rs.getDouble("importance_score"),
                rs.getDouble("relevance_score"),
                rs.getDouble("confidence_score"),
                nullableLong(rs, "source_message_start_id"),
                nullableLong(rs, "source_message_end_id"),
                rs.getString("source_type"),
                rs.getString("source_ref"),
                rs.getString("tags"),
                instant(rs.getTimestamp("created_at")),
                instant(rs.getTimestamp("updated_at")),
                instant(rs.getTimestamp("expires_at")),
                rs.getBoolean("deleted"));
    }

    private MemoryGraphEdge mapEdge(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        return new MemoryGraphEdge(
                rs.getLong("id"),
                rs.getString("session_key"),
                rs.getLong("source_node_id"),
                rs.getLong("target_node_id"),
                MemoryEdgeType.valueOf(rs.getString("edge_type")),
                rs.getDouble("weight"),
                instant(rs.getTimestamp("created_at")));
    }

    private Long nullableLong(java.sql.ResultSet rs, String column) throws java.sql.SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private Instant instant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }

    private void setLong(PreparedStatement statement, int index, Long value) throws java.sql.SQLException {
        if (value == null) {
            statement.setObject(index, null);
        } else {
            statement.setLong(index, value);
        }
    }

    private String emptyToNull(String value) {
        String text = clean(value);
        return text.isBlank() ? null : text;
    }

    private String clean(String value) {
        return value == null ? "" : value.strip();
    }

    private int safeLimit(int limit) {
        return limit <= 0 ? 10 : Math.min(limit, 100);
    }
}
```

- [ ] **Step 7: Run repository tests**

Run:

```powershell
mvn "-Dtest=MySqlMemoryGraphRepositoryTests" test
```

Expected: PASS.

- [ ] **Step 8: Run migration health tests**

Run:

```powershell
mvn "-Dtest=FlywayMigrationVersionTests,ApplicationContextTests" test
```

Expected: PASS.

- [ ] **Step 9: Commit**

```powershell
git add src/main/resources/db/migration src/main/java/com/example/spring/wechat/context src/test/java/com/example/spring/wechat/context/MySqlMemoryGraphRepositoryTests.java
git commit -m "feat(context): persist memory graph nodes"
```

---

## Task 4: Implement Budget Manager and Compressor

**Files:**
- Create: `src/main/java/com/example/spring/wechat/context/TokenEstimator.java`
- Create: `src/main/java/com/example/spring/wechat/context/ConservativeTokenEstimator.java`
- Create: `src/main/java/com/example/spring/wechat/context/ContextSection.java`
- Create: `src/main/java/com/example/spring/wechat/context/ContextBudgetManager.java`
- Create: `src/main/java/com/example/spring/wechat/context/ContextCompressor.java`
- Test: `src/test/java/com/example/spring/wechat/context/ContextBudgetManagerTests.java`
- Test: `src/test/java/com/example/spring/wechat/context/ContextCompressorTests.java`

- [ ] **Step 1: Write failing budget tests**

Create `src/test/java/com/example/spring/wechat/context/ContextBudgetManagerTests.java`:

```java
package com.example.spring.wechat.context;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ContextBudgetManagerTests {

    @Test
    void calculatesContextBudgetAfterReservesAndRatio() {
        WechatContextProperties properties = new WechatContextProperties(
                true, true, true, 5, 1, 2, 5, 1,
                128_000, 8_000, 12_000, 0.8);
        ContextBudgetManager manager = new ContextBudgetManager(properties, new ConservativeTokenEstimator());

        ContextBudgetReport report = manager.report("x".repeat(1_000), false);

        assertThat(report.availableInputTokens()).isEqualTo(108_000);
        assertThat(report.contextBudgetTokens()).isEqualTo(86_400);
        assertThat(report.actualTokens()).isEqualTo(1_000);
        assertThat(report.compressed()).isFalse();
    }
}
```

- [ ] **Step 2: Write failing compressor tests**

Create `src/test/java/com/example/spring/wechat/context/ContextCompressorTests.java`:

```java
package com.example.spring.wechat.context;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ContextCompressorTests {

    private final ConservativeTokenEstimator estimator = new ConservativeTokenEstimator();

    @Test
    void compressesLowerPrioritySectionsBeforeRecentTurns() {
        ContextCompressor compressor = new ContextCompressor(estimator);
        List<ContextSection> sections = List.of(
                new ContextSection("recent_turns", "最近完整对话", "R".repeat(80), 10, true),
                new ContextSection("long_term_memory", "长期记忆", "L".repeat(200), 80, true),
                new ContextSection("active_extract", "活摘", "A".repeat(160), 60, true));

        List<ContextSection> compressed = compressor.compress(sections, 180);

        String joined = compressed.stream().map(ContextSection::content).reduce("", String::concat);
        assertThat(estimator.estimate(joined)).isLessThanOrEqualTo(180);
        assertThat(compressed.get(0).content()).contains("R");
        assertThat(joined).contains("[已压缩]");
    }
}
```

- [ ] **Step 3: Run tests to verify failure**

Run:

```powershell
mvn "-Dtest=ContextBudgetManagerTests,ContextCompressorTests" test
```

Expected: FAIL because budget/compressor classes do not exist.

- [ ] **Step 4: Implement token estimation and section model**

Create `src/main/java/com/example/spring/wechat/context/TokenEstimator.java`:

```java
package com.example.spring.wechat.context;

public interface TokenEstimator {
    int estimate(String text);
}
```

Create `src/main/java/com/example/spring/wechat/context/ConservativeTokenEstimator.java`:

```java
package com.example.spring.wechat.context;

import org.springframework.stereotype.Component;

@Component
public class ConservativeTokenEstimator implements TokenEstimator {

    @Override
    public int estimate(String text) {
        return text == null ? 0 : text.length();
    }
}
```

Create `src/main/java/com/example/spring/wechat/context/ContextSection.java`:

```java
package com.example.spring.wechat.context;

public record ContextSection(
        String key,
        String title,
        String content,
        int compressionPriority,
        boolean compressible) {

    public ContextSection {
        key = key == null ? "" : key.strip();
        title = title == null ? "" : title.strip();
        content = content == null ? "" : content.strip();
    }

    public String formatted() {
        if (content.isBlank()) {
            return "";
        }
        return "【" + title + "】" + System.lineSeparator() + content;
    }

    public ContextSection withContent(String newContent) {
        return new ContextSection(key, title, newContent, compressionPriority, compressible);
    }
}
```

- [ ] **Step 5: Implement budget manager**

Create `src/main/java/com/example/spring/wechat/context/ContextBudgetManager.java`:

```java
package com.example.spring.wechat.context;

import org.springframework.stereotype.Component;

@Component
public class ContextBudgetManager {

    private final WechatContextProperties properties;
    private final TokenEstimator tokenEstimator;

    public ContextBudgetManager(WechatContextProperties properties, TokenEstimator tokenEstimator) {
        this.properties = properties;
        this.tokenEstimator = tokenEstimator;
    }

    public int contextBudgetTokens() {
        int available = Math.max(1,
                properties.modelWindowTokens()
                        - properties.outputReserveTokens()
                        - properties.toolLoopReserveTokens());
        return Math.max(1, (int) Math.floor(available * properties.maxInputRatio()));
    }

    public ContextBudgetReport report(String contextText, boolean compressed) {
        int available = Math.max(1,
                properties.modelWindowTokens()
                        - properties.outputReserveTokens()
                        - properties.toolLoopReserveTokens());
        int budget = Math.max(1, (int) Math.floor(available * properties.maxInputRatio()));
        return new ContextBudgetReport(
                properties.modelWindowTokens(),
                properties.outputReserveTokens(),
                properties.toolLoopReserveTokens(),
                available,
                budget,
                tokenEstimator.estimate(contextText),
                compressed);
    }
}
```

- [ ] **Step 6: Implement compressor**

Create `src/main/java/com/example/spring/wechat/context/ContextCompressor.java`:

```java
package com.example.spring.wechat.context;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Component
public class ContextCompressor {

    private static final String MARKER = "...[已压缩]";

    private final TokenEstimator tokenEstimator;

    public ContextCompressor(TokenEstimator tokenEstimator) {
        this.tokenEstimator = tokenEstimator;
    }

    public List<ContextSection> compress(List<ContextSection> sections, int budgetTokens) {
        List<ContextSection> result = new ArrayList<>(sections == null ? List.of() : sections);
        if (estimate(result) <= budgetTokens) {
            return result;
        }
        List<Integer> indexes = new ArrayList<>();
        for (int index = 0; index < result.size(); index++) {
            if (result.get(index).compressible()) {
                indexes.add(index);
            }
        }
        indexes.sort(Comparator.comparingInt((Integer index) -> result.get(index).compressionPriority()).reversed());
        for (Integer index : indexes) {
            if (estimate(result) <= budgetTokens) {
                break;
            }
            ContextSection section = result.get(index);
            int currentTotal = estimate(result);
            int over = currentTotal - budgetTokens;
            int currentLength = tokenEstimator.estimate(section.content());
            int targetLength = Math.max(24, currentLength - over - MARKER.length());
            result.set(index, section.withContent(truncate(section.content(), targetLength)));
        }
        return result;
    }

    private int estimate(List<ContextSection> sections) {
        return tokenEstimator.estimate(sections.stream()
                .map(ContextSection::formatted)
                .filter(value -> !value.isBlank())
                .reduce("", (left, right) -> left + System.lineSeparator() + right));
    }

    private String truncate(String value, int maxChars) {
        String text = value == null ? "" : value.strip();
        if (text.length() <= maxChars) {
            return text;
        }
        if (maxChars <= MARKER.length()) {
            return MARKER.substring(0, maxChars);
        }
        return text.substring(0, Math.max(0, maxChars - MARKER.length())).stripTrailing() + MARKER;
    }
}
```

- [ ] **Step 7: Run budget/compressor tests**

Run:

```powershell
mvn "-Dtest=ContextBudgetManagerTests,ContextCompressorTests" test
```

Expected: PASS.

- [ ] **Step 8: Commit**

```powershell
git add src/main/java/com/example/spring/wechat/context src/test/java/com/example/spring/wechat/context
git commit -m "feat(context): add budget-aware context compression"
```

---

## Task 5: Implement Memory Graph Retrieval and RAG Long-Term Retrieval

**Files:**
- Create: `src/main/java/com/example/spring/wechat/context/MemoryGraphRetriever.java`
- Create: `src/main/java/com/example/spring/wechat/context/LongTermMemoryRetriever.java`
- Test: `src/test/java/com/example/spring/wechat/context/MemoryGraphRetrieverTests.java`
- Test: `src/test/java/com/example/spring/wechat/context/LongTermMemoryRetrieverTests.java`

- [ ] **Step 1: Write failing graph retriever test**

Create `src/test/java/com/example/spring/wechat/context/MemoryGraphRetrieverTests.java`:

```java
package com.example.spring.wechat.context;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MemoryGraphRetrieverTests {

    @Test
    void retrievesStrongTopicExtractsByTopic() {
        MemoryGraphRepository repository = mock(MemoryGraphRepository.class);
        MemoryGraphNode node = node(MemoryNodeType.ACTIVE_EXTRACT, "topic-a", "活摘内容");
        when(repository.findRecentNodesByTopic("session", MemoryNodeType.ACTIVE_EXTRACT, "topic-a", 5))
                .thenReturn(List.of(node));

        MemoryGraphRetriever retriever = new MemoryGraphRetriever(repository);

        assertThat(retriever.activeExtracts("session", "topic-a", 5))
                .extracting(MemoryGraphNode::content)
                .containsExactly("活摘内容");
    }

    private MemoryGraphNode node(MemoryNodeType type, String topic, String content) {
        return new MemoryGraphNode(1, "session", 1L, type, topic, topic, content, "",
                1, 1, 1, null, null, "", "", "", null, null, null, false);
    }
}
```

- [ ] **Step 2: Write failing long-term retriever test**

Create `src/test/java/com/example/spring/wechat/context/LongTermMemoryRetrieverTests.java`:

```java
package com.example.spring.wechat.context;

import com.example.spring.wechat.knowledge.model.KnowledgeSearchResult;
import com.example.spring.wechat.knowledge.service.KnowledgeSearchService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LongTermMemoryRetrieverTests {

    @Test
    void searchesLongTermMemoryWithMemoryTypeTag() {
        KnowledgeSearchService searchService = mock(KnowledgeSearchService.class);
        when(searchService.search("session", "上下文优化", 5, "memory_type:long_term_memory"))
                .thenReturn(List.of(result("用户偏好先看设计")));

        LongTermMemoryRetriever retriever = new LongTermMemoryRetriever(searchService);

        assertThat(retriever.longTermMemories("session", "上下文优化", 5))
                .contains("用户偏好先看设计");
    }

    @Test
    void searchesConversationTopicsWithMemoryTypeTag() {
        KnowledgeSearchService searchService = mock(KnowledgeSearchService.class);
        when(searchService.search("session", "天气", 5, "memory_type:conversation_topic"))
                .thenReturn(List.of(result("历史主题：OpenClaw 上下文机制")));

        LongTermMemoryRetriever retriever = new LongTermMemoryRetriever(searchService);

        assertThat(retriever.conversationTopics("session", "天气", 5))
                .contains("历史主题：OpenClaw 上下文机制");
    }

    private KnowledgeSearchResult result(String content) {
        return new KnowledgeSearchResult(1L, "title", 0, content, "memory_graph", "memory://1", 0.9);
    }
}
```

- [ ] **Step 3: Run retriever tests to verify failure**

Run:

```powershell
mvn "-Dtest=MemoryGraphRetrieverTests,LongTermMemoryRetrieverTests" test
```

Expected: FAIL because retriever classes do not exist.

- [ ] **Step 4: Implement retrievers**

Create `src/main/java/com/example/spring/wechat/context/MemoryGraphRetriever.java`:

```java
package com.example.spring.wechat.context;

import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class MemoryGraphRetriever {

    private final MemoryGraphRepository repository;

    public MemoryGraphRetriever(MemoryGraphRepository repository) {
        this.repository = repository;
    }

    public List<MemoryGraphNode> activeExtracts(String sessionKey, String topicKey, int limit) {
        if (repository == null || isBlank(topicKey)) {
            return List.of();
        }
        return repository.findRecentNodesByTopic(sessionKey, MemoryNodeType.ACTIVE_EXTRACT, topicKey, limit);
    }

    public List<MemoryGraphNode> recentTopics(String sessionKey, int limit) {
        if (repository == null) {
            return List.of();
        }
        return repository.findRecentNodes(sessionKey, MemoryNodeType.CONVERSATION_TOPIC, limit);
    }

    public List<MemoryGraphNode> recentSummaries(String sessionKey, int limit) {
        if (repository == null) {
            return List.of();
        }
        return repository.findRecentNodes(sessionKey, MemoryNodeType.CONVERSATION_SUMMARY, limit);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
```

Create `src/main/java/com/example/spring/wechat/context/LongTermMemoryRetriever.java`:

```java
package com.example.spring.wechat.context;

import com.example.spring.wechat.knowledge.model.KnowledgeSearchResult;
import com.example.spring.wechat.knowledge.service.KnowledgeSearchService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class LongTermMemoryRetriever {

    private static final String LONG_TERM_TAG = "memory_type:long_term_memory";
    private static final String TOPIC_TAG = "memory_type:conversation_topic";

    private final KnowledgeSearchService searchService;

    public LongTermMemoryRetriever(KnowledgeSearchService searchService) {
        this.searchService = searchService;
    }

    public List<String> longTermMemories(String sessionKey, String query, int limit) {
        return search(sessionKey, query, limit, LONG_TERM_TAG);
    }

    public List<String> conversationTopics(String sessionKey, String query, int limit) {
        return search(sessionKey, query, limit, TOPIC_TAG);
    }

    private List<String> search(String sessionKey, String query, int limit, String tags) {
        if (searchService == null || query == null || query.isBlank()) {
            return List.of();
        }
        return searchService.search(sessionKey, query, limit, tags).stream()
                .map(KnowledgeSearchResult::content)
                .filter(value -> value != null && !value.isBlank())
                .map(String::strip)
                .toList();
    }
}
```

- [ ] **Step 5: Run retriever tests**

Run:

```powershell
mvn "-Dtest=MemoryGraphRetrieverTests,LongTermMemoryRetrieverTests" test
```

Expected: PASS.

- [ ] **Step 6: Commit**

```powershell
git add src/main/java/com/example/spring/wechat/context src/test/java/com/example/spring/wechat/context
git commit -m "feat(context): retrieve graph and long-term memory"
```

---

## Task 6: Implement Context Assembly and Orchestration

**Files:**
- Create: `src/main/java/com/example/spring/wechat/context/WechatContextAssembler.java`
- Create: `src/main/java/com/example/spring/wechat/context/WechatContextOrchestrator.java`
- Test: `src/test/java/com/example/spring/wechat/context/WechatContextAssemblerTests.java`
- Test: `src/test/java/com/example/spring/wechat/context/WechatContextOrchestratorTests.java`

- [ ] **Step 1: Write failing assembler test**

Create `src/test/java/com/example/spring/wechat/context/WechatContextAssemblerTests.java`:

```java
package com.example.spring.wechat.context;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class WechatContextAssemblerTests {

    @Test
    void formatsStructuredContextWithPolicyFirst() {
        WechatContextAssembler assembler = new WechatContextAssembler();
        String text = assembler.assemble(
                RelevanceLevel.STRONG,
                "保留最近 5 轮完整对话 + 当前主题活摘 + 长期记忆",
                List.of(
                        new ContextSection("recent_turns", "recent_turns / 最近完整对话", "用户：你好\n助手：你好", 10, true),
                        new ContextSection("active_extract", "active_extract / 当前主题活摘", "已确认方案 C", 60, true)),
                new ContextBudgetReport(128000, 8000, 12000, 108000, 86400, 100, false));

        assertThat(text)
                .contains("context_policy")
                .contains("相关性：STRONG")
                .contains("recent_turns")
                .contains("active_extract");
    }
}
```

- [ ] **Step 2: Write failing orchestrator test**

Create `src/test/java/com/example/spring/wechat/context/WechatContextOrchestratorTests.java`:

```java
package com.example.spring.wechat.context;

import com.example.spring.wechat.memory.model.WechatConversationMemory;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WechatContextOrchestratorTests {

    @Test
    void strongRelevanceKeepsFiveRecentTurnsAndActiveExtract() {
        WechatConversationMemory memory = WechatConversationMemory.empty(20, "更早摘要");
        for (int index = 1; index <= 11; index++) {
            memory.record("user-" + index, "assistant-" + index);
        }
        ConversationRelevanceClassifier classifier = mock(ConversationRelevanceClassifier.class);
        when(classifier.classify(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyList(), org.mockito.ArgumentMatchers.anyList()))
                .thenReturn(ConversationRelevanceDecision.strong("topic-a", "same topic"));
        MemoryGraphRetriever graphRetriever = mock(MemoryGraphRetriever.class);
        when(graphRetriever.activeExtracts("session", "topic-a", 5))
                .thenReturn(List.of(node(MemoryNodeType.ACTIVE_EXTRACT, "topic-a", "活摘内容")));
        LongTermMemoryRetriever longTermRetriever = mock(LongTermMemoryRetriever.class);
        when(longTermRetriever.longTermMemories("session", "继续", 5))
                .thenReturn(List.of("长期偏好"));

        WechatContextOrchestrator orchestrator = new WechatContextOrchestrator(
                new WechatContextProperties(true, true, true, 5, 1, 2, 5, 1, 128000, 8000, 12000, 0.8),
                classifier,
                graphRetriever,
                longTermRetriever,
                new ContextBudgetManager(new WechatContextProperties(true, true, true, 5, 1, 2, 5, 1, 128000, 8000, 12000, 0.8), new ConservativeTokenEstimator()),
                new ContextCompressor(new ConservativeTokenEstimator()),
                new WechatContextAssembler());

        WechatContextPackage context = orchestrator.build(new ContextBuildRequest(
                "session", "继续", memory, "资源", "", null));

        assertThat(context.relevance()).isEqualTo(RelevanceLevel.STRONG);
        assertThat(context.finalContextText())
                .contains("user-7")
                .contains("user-11")
                .doesNotContain("user-6")
                .contains("活摘内容")
                .contains("长期偏好");
    }

    @Test
    void weakRelevanceKeepsOneRecentTurnAndHistoricalTopics() {
        WechatConversationMemory memory = WechatConversationMemory.empty(20, "更早摘要");
        memory.record("旧主题", "旧回复");
        memory.record("上一轮", "上一轮回复");
        ConversationRelevanceClassifier classifier = mock(ConversationRelevanceClassifier.class);
        when(classifier.classify(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyList(), org.mockito.ArgumentMatchers.anyList()))
                .thenReturn(ConversationRelevanceDecision.weak("new topic"));
        MemoryGraphRetriever graphRetriever = mock(MemoryGraphRetriever.class);
        when(graphRetriever.recentTopics("session", 5))
                .thenReturn(List.of(node(MemoryNodeType.CONVERSATION_TOPIC, "topic-b", "历史主题内容")));

        WechatContextOrchestrator orchestrator = new WechatContextOrchestrator(
                new WechatContextProperties(true, true, true, 5, 1, 2, 5, 1, 128000, 8000, 12000, 0.8),
                classifier,
                graphRetriever,
                mock(LongTermMemoryRetriever.class),
                new ContextBudgetManager(new WechatContextProperties(true, true, true, 5, 1, 2, 5, 1, 128000, 8000, 12000, 0.8), new ConservativeTokenEstimator()),
                new ContextCompressor(new ConservativeTokenEstimator()),
                new WechatContextAssembler());

        WechatContextPackage context = orchestrator.build(new ContextBuildRequest(
                "session", "新话题", memory, "", "", null));

        assertThat(context.relevance()).isEqualTo(RelevanceLevel.WEAK);
        assertThat(context.finalContextText())
                .contains("上一轮")
                .doesNotContain("旧主题")
                .contains("历史主题内容");
    }

    private MemoryGraphNode node(MemoryNodeType type, String topic, String content) {
        return new MemoryGraphNode(1, "session", 1L, type, topic, topic, content, "",
                1, 1, 1, null, null, "", "", "", null, null, null, false);
    }
}
```

- [ ] **Step 3: Run tests to verify failure**

Run:

```powershell
mvn "-Dtest=WechatContextAssemblerTests,WechatContextOrchestratorTests" test
```

Expected: FAIL because assembler/orchestrator classes do not exist.

- [ ] **Step 4: Implement assembler**

Create `src/main/java/com/example/spring/wechat/context/WechatContextAssembler.java`:

```java
package com.example.spring.wechat.context;

import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class WechatContextAssembler {

    public String assemble(
            RelevanceLevel relevance,
            String policy,
            List<ContextSection> sections,
            ContextBudgetReport budgetReport) {
        StringBuilder text = new StringBuilder();
        text.append("【context_policy / 上下文策略】").append(System.lineSeparator())
                .append("相关性：").append(relevance == null ? RelevanceLevel.WEAK : relevance).append(System.lineSeparator())
                .append("策略：").append(policy == null ? "" : policy.strip()).append(System.lineSeparator());
        if (budgetReport != null) {
            text.append("预算：").append(budgetReport.contextBudgetTokens()).append(" tokens").append(System.lineSeparator())
                    .append("实际：").append(budgetReport.actualTokens()).append(" tokens").append(System.lineSeparator())
                    .append("压缩：").append(budgetReport.compressed() ? "是" : "否");
        }
        for (ContextSection section : sections == null ? List.<ContextSection>of() : sections) {
            String formatted = section.formatted();
            if (!formatted.isBlank()) {
                text.append(System.lineSeparator()).append(System.lineSeparator()).append(formatted);
            }
        }
        return text.toString().strip();
    }
}
```

- [ ] **Step 5: Implement orchestrator**

Create `src/main/java/com/example/spring/wechat/context/WechatContextOrchestrator.java`:

```java
package com.example.spring.wechat.context;

import com.example.spring.wechat.memory.model.ConversationTurn;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class WechatContextOrchestrator {

    private final WechatContextProperties properties;
    private final ConversationRelevanceClassifier relevanceClassifier;
    private final MemoryGraphRetriever graphRetriever;
    private final LongTermMemoryRetriever longTermMemoryRetriever;
    private final ContextBudgetManager budgetManager;
    private final ContextCompressor compressor;
    private final WechatContextAssembler assembler;

    public WechatContextOrchestrator(
            WechatContextProperties properties,
            ConversationRelevanceClassifier relevanceClassifier,
            MemoryGraphRetriever graphRetriever,
            LongTermMemoryRetriever longTermMemoryRetriever,
            ContextBudgetManager budgetManager,
            ContextCompressor compressor,
            WechatContextAssembler assembler) {
        this.properties = properties;
        this.relevanceClassifier = relevanceClassifier;
        this.graphRetriever = graphRetriever;
        this.longTermMemoryRetriever = longTermMemoryRetriever;
        this.budgetManager = budgetManager;
        this.compressor = compressor;
        this.assembler = assembler;
    }

    public WechatContextPackage build(ContextBuildRequest request) {
        if (request == null || request.memory() == null || properties == null || !properties.memoryGraphEnabled()) {
            return new WechatContextPackage(RelevanceLevel.WEAK, "", List.of(), null);
        }
        List<String> recentClassifierTurns = formatTurns(request.memory().recentTurns(properties.strongRecentTurns()));
        List<String> recentTopics = graphRetriever == null
                ? List.of()
                : graphRetriever.recentTopics(request.sessionKey(), 5).stream().map(MemoryGraphNode::content).toList();
        ConversationRelevanceDecision decision = relevanceClassifier == null
                ? ConversationRelevanceDecision.weak("相关性分类器不可用")
                : relevanceClassifier.classify(request.userText(), recentClassifierTurns, recentTopics);

        List<ContextSection> sections = decision.relevance() == RelevanceLevel.STRONG
                ? strongSections(request, decision)
                : weakSections(request, decision, recentTopics);
        int budget = budgetManager.contextBudgetTokens();
        List<ContextSection> compressed = compressor.compress(sections, budget);
        String draft = renderSections(compressed);
        boolean wasCompressed = !renderSections(sections).equals(draft);
        ContextBudgetReport report = budgetManager.report(draft, wasCompressed);
        String finalText = assembler.assemble(
                decision.relevance(),
                policyText(decision.relevance()),
                compressed,
                report);
        return new WechatContextPackage(decision.relevance(), finalText, selectedNodes(request, decision), report);
    }

    private List<ContextSection> strongSections(ContextBuildRequest request, ConversationRelevanceDecision decision) {
        List<ContextSection> sections = new ArrayList<>();
        sections.add(new ContextSection("recent_turns", "recent_turns / 最近完整对话",
                String.join(System.lineSeparator(), formatTurns(request.memory().recentTurns(properties.strongRecentTurns()))),
                10,
                true));
        sections.add(new ContextSection("active_extract", "active_extract / 当前主题活摘",
                nodeContents(graphRetriever == null ? List.of() : graphRetriever.activeExtracts(
                        request.sessionKey(),
                        decision.currentTopic(),
                        5)),
                60,
                true));
        sections.add(new ContextSection("long_term_memory", "long_term_memory / 长期记忆",
                String.join(System.lineSeparator(), longTermMemoryRetriever == null
                        ? List.of()
                        : longTermMemoryRetriever.longTermMemories(request.sessionKey(), request.userText(), 5)),
                80,
                true));
        sections.add(new ContextSection("resource_context", "resource_context / 可用资源",
                request.resourceContext(),
                70,
                true));
        sections.add(new ContextSection("conversation_summary", "conversation_summary / 会话摘要",
                request.memory().conversationSummary().orElse(""),
                50,
                true));
        return sections;
    }

    private List<ContextSection> weakSections(ContextBuildRequest request, ConversationRelevanceDecision decision, List<String> recentTopics) {
        List<ContextSection> sections = new ArrayList<>();
        sections.add(new ContextSection("recent_turns", "recent_turns / 最近完整对话",
                String.join(System.lineSeparator(), formatTurns(request.memory().recentTurns(properties.weakRecentTurns()))),
                10,
                true));
        sections.add(new ContextSection("conversation_topics", "conversation_topics / 历史主题",
                String.join(System.lineSeparator(), recentTopics),
                80,
                true));
        sections.add(new ContextSection("long_term_memory", "long_term_memory / 长期记忆",
                String.join(System.lineSeparator(), longTermMemoryRetriever == null
                        ? List.of()
                        : longTermMemoryRetriever.longTermMemories(request.sessionKey(), request.userText(), 5)),
                70,
                true));
        sections.add(new ContextSection("resource_context", "resource_context / 可用资源",
                request.resourceContext(),
                60,
                true));
        return sections;
    }

    private List<String> formatTurns(List<ConversationTurn> turns) {
        if (turns == null || turns.isEmpty()) {
            return List.of();
        }
        return turns.stream()
                .map(turn -> "用户：" + turn.userText() + System.lineSeparator() + "助手：" + turn.assistantText())
                .toList();
    }

    private String nodeContents(List<MemoryGraphNode> nodes) {
        return nodes == null ? "" : String.join(System.lineSeparator(), nodes.stream()
                .map(MemoryGraphNode::content)
                .filter(value -> value != null && !value.isBlank())
                .toList());
    }

    private List<MemoryGraphNode> selectedNodes(ContextBuildRequest request, ConversationRelevanceDecision decision) {
        if (graphRetriever == null || decision.relevance() != RelevanceLevel.STRONG) {
            return List.of();
        }
        return graphRetriever.activeExtracts(request.sessionKey(), decision.currentTopic(), 5);
    }

    private String renderSections(List<ContextSection> sections) {
        return String.join(System.lineSeparator(), sections.stream()
                .map(ContextSection::formatted)
                .filter(value -> !value.isBlank())
                .toList());
    }

    private String policyText(RelevanceLevel relevance) {
        if (relevance == RelevanceLevel.STRONG) {
            return "保留最近 " + properties.strongRecentTurns() + " 轮完整对话 + 当前主题活摘 + 长期记忆";
        }
        return "保留最近 " + properties.weakRecentTurns() + " 轮完整对话 + 历史主题 + 长期记忆";
    }
}
```

- [ ] **Step 6: Run orchestration tests**

Run:

```powershell
mvn "-Dtest=WechatContextAssemblerTests,WechatContextOrchestratorTests" test
```

Expected: PASS.

- [ ] **Step 7: Commit**

```powershell
git add src/main/java/com/example/spring/wechat/context src/test/java/com/example/spring/wechat/context
git commit -m "feat(context): assemble relevance-aware memory graph context"
```

---

## Task 7: Implement Sliding Summaries, Active Extracts, and Async Long-Term Ingestion

**Files:**
- Create: `src/main/java/com/example/spring/wechat/context/SlidingWindowSummaryService.java`
- Create: `src/main/java/com/example/spring/wechat/context/ActiveExtractService.java`
- Create: `src/main/java/com/example/spring/wechat/context/LongTermMemoryExtractor.java`
- Create: `src/main/java/com/example/spring/wechat/context/MemoryGraphMaintenanceService.java`
- Create: `src/main/java/com/example/spring/wechat/context/MemoryGraphMaintenanceScheduler.java`
- Test: `src/test/java/com/example/spring/wechat/context/SlidingWindowSummaryServiceTests.java`
- Test: `src/test/java/com/example/spring/wechat/context/ActiveExtractServiceTests.java`
- Test: `src/test/java/com/example/spring/wechat/context/LongTermMemoryExtractorTests.java`

- [ ] **Step 1: Write failing long-term extractor test**

Create `src/test/java/com/example/spring/wechat/context/LongTermMemoryExtractorTests.java`:

```java
package com.example.spring.wechat.context;

import com.example.spring.chat.ChatService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LongTermMemoryExtractorTests {

    @Test
    void extractsStableMemoriesAndFiltersTemporarySensitiveItems() {
        ChatService chatService = mock(ChatService.class);
        when(chatService.reply(contains("长期记忆抽取器"))).thenReturn("""
                [
                  {"type":"LONG_TERM_MEMORY","title":"用户偏好","content":"用户偏好先看完整设计再实现","confidence":0.92},
                  {"type":"LONG_TERM_MEMORY","title":"临时提醒","content":"明天提醒用户喝水","confidence":0.95},
                  {"type":"LONG_TERM_MEMORY","title":"密钥","content":"用户 API key 是 abc","confidence":0.95}
                ]
                """);

        LongTermMemoryExtractor extractor = new LongTermMemoryExtractor(chatService);

        List<MemoryGraphNodeDraft> drafts = extractor.extract("session", 1L, "用户说以后先写设计，再实现。明天提醒我喝水。API key 是 abc。");

        assertThat(drafts).hasSize(1);
        assertThat(drafts.get(0).content()).contains("先看完整设计");
    }
}
```

- [ ] **Step 2: Run extractor test to verify failure**

Run:

```powershell
mvn "-Dtest=LongTermMemoryExtractorTests" test
```

Expected: FAIL because `LongTermMemoryExtractor` does not exist.

- [ ] **Step 3: Implement long-term extractor with strict filter**

Create `src/main/java/com/example/spring/wechat/context/LongTermMemoryExtractor.java`:

```java
package com.example.spring.wechat.context;

import com.example.spring.chat.ChatService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class LongTermMemoryExtractor {

    private final ChatService chatService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public LongTermMemoryExtractor(ChatService chatService) {
        this.chatService = chatService;
    }

    public List<MemoryGraphNodeDraft> extract(String sessionKey, Long conversationId, String transcript) {
        if (chatService == null || transcript == null || transcript.isBlank()) {
            return List.of();
        }
        try {
            JsonNode root = objectMapper.readTree(cleanJson(chatService.reply(prompt(transcript))));
            if (!root.isArray()) {
                return List.of();
            }
            List<MemoryGraphNodeDraft> drafts = new ArrayList<>();
            for (JsonNode item : root) {
                String title = item.path("title").asText("");
                String content = item.path("content").asText("");
                double confidence = item.path("confidence").asDouble(0);
                if (allowed(content, confidence)) {
                    drafts.add(new MemoryGraphNodeDraft(
                            sessionKey,
                            conversationId,
                            MemoryNodeType.LONG_TERM_MEMORY,
                            topicKey(title),
                            title,
                            content,
                            content,
                            0.9,
                            0,
                            confidence,
                            null,
                            null,
                            "conversation",
                            conversationId == null ? "" : "conversation://" + conversationId,
                            "memory_type:long_term_memory",
                            null));
                }
            }
            return drafts;
        } catch (RuntimeException exception) {
            return List.of();
        } catch (java.io.IOException exception) {
            return List.of();
        }
    }

    private String prompt(String transcript) {
        return """
                你是微信 Agent 的长期记忆抽取器。
                只抽取稳定事实、长期偏好、持续项目背景。
                不要抽取临时任务、提醒、天气、价格、账号、密码、token、API key、支付信息、敏感医疗细节。
                只输出 JSON 数组，每项包含 type、title、content、confidence。

                对话内容：
                %s
                """.formatted(transcript);
    }

    private boolean allowed(String content, double confidence) {
        String text = content == null ? "" : content.strip().toLowerCase(java.util.Locale.ROOT);
        if (text.isBlank() || confidence < 0.75) {
            return false;
        }
        return !containsAny(text,
                "提醒", "明天", "今天", "天气", "价格", "api key", "token", "密码", "密钥", "支付", "银行卡", "医疗诊断");
    }

    private boolean containsAny(String text, String... markers) {
        for (String marker : markers) {
            if (text.contains(marker)) {
                return true;
            }
        }
        return false;
    }

    private String topicKey(String title) {
        return title == null ? "" : title.strip().replaceAll("\\s+", "-").toLowerCase(java.util.Locale.ROOT);
    }

    private String cleanJson(String value) {
        String text = value == null ? "" : value.strip();
        if (text.startsWith("```")) {
            text = text.replaceFirst("^```[a-zA-Z]*", "").replaceFirst("```$", "").strip();
        }
        return text;
    }
}
```

- [ ] **Step 4: Write failing summary and active extract tests**

Create `src/test/java/com/example/spring/wechat/context/SlidingWindowSummaryServiceTests.java`:

```java
package com.example.spring.wechat.context;

import com.example.spring.chat.ChatService;
import com.example.spring.wechat.memory.model.WechatConversationMemory;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SlidingWindowSummaryServiceTests {

    @Test
    void summarizesTurnsOutsideStrongWindow() {
        ChatService chatService = mock(ChatService.class);
        when(chatService.reply(contains("滑动窗口摘要器"))).thenReturn("第 1-6 轮摘要");
        WechatConversationMemory memory = WechatConversationMemory.empty(20);
        for (int index = 1; index <= 11; index++) {
            memory.record("user-" + index, "assistant-" + index);
        }

        SlidingWindowSummaryService service = new SlidingWindowSummaryService(chatService);

        String summary = service.summarizeOutsideRecentWindow(memory, 5);

        assertThat(summary).isEqualTo("第 1-6 轮摘要");
    }
}
```

Create `src/test/java/com/example/spring/wechat/context/ActiveExtractServiceTests.java`:

```java
package com.example.spring.wechat.context;

import com.example.spring.chat.ChatService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ActiveExtractServiceTests {

    @Test
    void extractsCurrentTopicOnly() {
        ChatService chatService = mock(ChatService.class);
        when(chatService.reply(contains("活摘抽取器"))).thenReturn("用户已确认 Memory Graph 使用方案 C。");
        ActiveExtractService service = new ActiveExtractService(chatService);

        String extract = service.extract("Memory Graph", "摘要里有 Memory Graph，也有杭州天气。");

        assertThat(extract).contains("Memory Graph").doesNotContain("杭州天气");
    }
}
```

- [ ] **Step 5: Implement summary and active extract services**

Create `src/main/java/com/example/spring/wechat/context/SlidingWindowSummaryService.java`:

```java
package com.example.spring.wechat.context;

import com.example.spring.chat.ChatService;
import com.example.spring.wechat.memory.model.ConversationTurn;
import com.example.spring.wechat.memory.model.WechatConversationMemory;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SlidingWindowSummaryService {

    private final ChatService chatService;

    public SlidingWindowSummaryService(ChatService chatService) {
        this.chatService = chatService;
    }

    public String summarizeOutsideRecentWindow(WechatConversationMemory memory, int recentTurns) {
        if (chatService == null || memory == null) {
            return "";
        }
        List<ConversationTurn> all = memory.snapshot();
        int keep = Math.max(1, recentTurns);
        int end = Math.max(0, all.size() - keep);
        if (end == 0) {
            return "";
        }
        String transcript = format(all.subList(0, end));
        if (transcript.isBlank()) {
            return "";
        }
        String reply = chatService.reply(prompt(transcript));
        return reply == null ? "" : reply.strip();
    }

    private String prompt(String transcript) {
        return """
                你是微信 Agent 的滑动窗口摘要器。
                只保留用户明确事实、偏好、当前目标、已完成事项、未完成事项、待确认问题、关键工具结果。
                删除寒暄、重复确认、临时噪声、系统提示词、敏感凭据。

                需要摘要的窗口外对话：
                %s
                """.formatted(transcript);
    }

    private String format(List<ConversationTurn> turns) {
        StringBuilder text = new StringBuilder();
        for (ConversationTurn turn : turns) {
            if (!text.isEmpty()) {
                text.append(System.lineSeparator());
            }
            text.append("用户：").append(turn.userText()).append(System.lineSeparator())
                    .append("助手：").append(turn.assistantText());
        }
        return text.toString();
    }
}
```

Create `src/main/java/com/example/spring/wechat/context/ActiveExtractService.java`:

```java
package com.example.spring.wechat.context;

import com.example.spring.chat.ChatService;
import org.springframework.stereotype.Service;

@Service
public class ActiveExtractService {

    private final ChatService chatService;

    public ActiveExtractService(ChatService chatService) {
        this.chatService = chatService;
    }

    public String extract(String currentTopic, String summaryOrTranscript) {
        if (chatService == null || currentTopic == null || currentTopic.isBlank()
                || summaryOrTranscript == null || summaryOrTranscript.isBlank()) {
            return "";
        }
        String reply = chatService.reply("""
                你是微信 Agent 的活摘抽取器。
                只抽取与当前主题强相关的信息。删除其他主题、寒暄、临时噪声和敏感凭据。

                当前主题：
                %s

                候选摘要或对话：
                %s
                """.formatted(currentTopic.strip(), summaryOrTranscript.strip()));
        return reply == null ? "" : reply.strip();
    }
}
```

- [ ] **Step 6: Implement maintenance service and scheduler**

Create `src/main/java/com/example/spring/wechat/context/MemoryGraphMaintenanceService.java`:

```java
package com.example.spring.wechat.context;

import com.example.spring.wechat.knowledge.service.KnowledgeIngestionService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class MemoryGraphMaintenanceService {

    private final WechatContextProperties properties;
    private final MemoryGraphRepository repository;
    private final KnowledgeIngestionService ingestionService;
    private final LongTermMemoryExtractor longTermMemoryExtractor;

    public MemoryGraphMaintenanceService(
            WechatContextProperties properties,
            MemoryGraphRepository repository,
            KnowledgeIngestionService ingestionService,
            LongTermMemoryExtractor longTermMemoryExtractor) {
        this.properties = properties;
        this.repository = repository;
        this.ingestionService = ingestionService;
        this.longTermMemoryExtractor = longTermMemoryExtractor;
    }

    public void ingestLongTermMemories(String sessionKey, Long conversationId, String transcript) {
        if (properties == null || !properties.longTermMemoryIngestionEnabled()) {
            return;
        }
        List<MemoryGraphNodeDraft> drafts = longTermMemoryExtractor.extract(sessionKey, conversationId, transcript);
        for (MemoryGraphNodeDraft draft : drafts) {
            MemoryGraphNode node = repository.createNode(draft);
            ingestionService.add(
                    sessionKey,
                    draft.title(),
                    draft.content(),
                    "memory_graph",
                    "memory://long_term_memory/" + node.id(),
                    "memory_type:long_term_memory");
        }
    }
}
```

Create `src/main/java/com/example/spring/wechat/context/MemoryGraphMaintenanceScheduler.java`:

```java
package com.example.spring.wechat.context;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class MemoryGraphMaintenanceScheduler {

    private static final Logger log = LoggerFactory.getLogger(MemoryGraphMaintenanceScheduler.class);

    private final WechatContextProperties properties;

    public MemoryGraphMaintenanceScheduler(WechatContextProperties properties) {
        this.properties = properties;
    }

    @Scheduled(initialDelayString = "${wechat.memory.summary-maintenance-initial-delay-ms:60000}",
            fixedDelayString = "${wechat.memory.summary-maintenance-delay-ms:300000}")
    public void run() {
        if (properties == null || !properties.memoryGraphEnabled() || !properties.longTermMemoryIngestionEnabled()) {
            return;
        }
        log.debug("Memory Graph maintenance tick");
    }
}
```

- [ ] **Step 7: Run maintenance tests**

Run:

```powershell
mvn "-Dtest=SlidingWindowSummaryServiceTests,ActiveExtractServiceTests,LongTermMemoryExtractorTests" test
```

Expected: PASS.

- [ ] **Step 8: Commit**

```powershell
git add src/main/java/com/example/spring/wechat/context src/test/java/com/example/spring/wechat/context
git commit -m "feat(context): maintain summaries and long-term memories"
```

---

## Task 8: Integrate Memory Graph Context into WeChat Conversation Service

**Files:**
- Modify: `src/main/java/com/example/spring/wechat/conversation/WechatConversationService.java`
- Test: extend `src/test/java/com/example/spring/wechat/conversation/WechatConversationServiceTests.java`
- Test: extend `src/test/java/com/example/spring/wechat/conversation/agent/FunctionCallingAgentLoopTests.java` only if existing assertions need new context text

- [ ] **Step 1: Write failing service fallback test**

Add this test to `WechatConversationServiceTests`:

```java
@Test
void usesMemoryGraphContextWhenAvailable() {
    ChatService chatService = prompt -> "ok";
    WeatherService weatherService = city -> new WeatherResult("浙江", city, "晴", "28", "60", "东风", "3", "now", List.of());
    WechatConversationService service = new WechatConversationService(chatService, weatherService);
    WechatContextOrchestrator orchestrator = mock(WechatContextOrchestrator.class);
    when(orchestrator.build(any())).thenReturn(new WechatContextPackage(
            RelevanceLevel.STRONG,
            "【context_policy / 上下文策略】\n相关性：STRONG\n【recent_turns / 最近完整对话】\n用户：前文\n助手：回复",
            List.of(),
            new ContextBudgetReport(128000, 8000, 12000, 108000, 86400, 100, false)));

    service.configureContextOrchestrator(() -> orchestrator);

    String context = service.conversationContextForTest("user-1");

    assertThat(context).contains("context_policy", "相关性：STRONG");
}
```

If `conversationContextForTest` does not exist, add a package-private method:

```java
String conversationContextForTest(String sessionKey) {
    return conversationContext(sessionKey);
}
```

- [ ] **Step 2: Run service test to verify failure**

Run:

```powershell
mvn "-Dtest=WechatConversationServiceTests#usesMemoryGraphContextWhenAvailable" test
```

Expected: FAIL because service does not expose/configure the orchestrator.

- [ ] **Step 3: Inject orchestrator with ObjectProvider**

Modify `WechatConversationService`:

Add field:

```java
private WechatContextOrchestrator contextOrchestrator;
```

Add import:

```java
import com.example.spring.wechat.context.ContextBuildRequest;
import com.example.spring.wechat.context.WechatContextOrchestrator;
```

Add Spring configuration method:

```java
@Autowired
void configureContextOrchestrator(ObjectProvider<WechatContextOrchestrator> provider) {
    this.contextOrchestrator = provider == null ? null : provider.getIfAvailable();
}
```

Add package-private test hook:

```java
void configureContextOrchestrator(java.util.function.Supplier<WechatContextOrchestrator> supplier) {
    this.contextOrchestrator = supplier == null ? null : supplier.get();
}
```

- [ ] **Step 4: Replace context building with fallback**

Modify `conversationContext(String sessionKey)`:

```java
private String conversationContext(String sessionKey) {
    if (contextOrchestrator != null) {
        try {
            String context = contextOrchestrator.build(new ContextBuildRequest(
                    sessionKey,
                    "",
                    memoryFor(sessionKey),
                    combinedResourceContext(sessionKey),
                    "",
                    WechatConversationMode.GENERAL)).finalContextText();
            if (hasStructuredContext(context)) {
                return context;
            }
        } catch (RuntimeException exception) {
            log.warn("Memory Graph 上下文构造失败，userId={}, error={}", sessionKey, rootMessage(exception));
        }
    }
    if (memoryContextBuilder != null) {
        String context = memoryContextBuilder.build(
                memoryFor(sessionKey),
                combinedResourceContext(sessionKey));
        if (hasStructuredContext(context)) {
            return context;
        }
    }
    return fallbackConversationContext(sessionKey);
}
```

Then add:

```java
String conversationContextForTest(String sessionKey) {
    return conversationContext(sessionKey);
}
```

- [ ] **Step 5: Pass current user text into orchestrator**

If the code needs current user text for relevance, add an overloaded method:

```java
private String conversationContext(String sessionKey, String userText, WechatConversationMode conversationMode) {
    if (contextOrchestrator != null) {
        try {
            String context = contextOrchestrator.build(new ContextBuildRequest(
                    sessionKey,
                    userText,
                    memoryFor(sessionKey),
                    combinedResourceContext(sessionKey),
                    ragContext(sessionKey, userText),
                    conversationMode)).finalContextText();
            if (hasStructuredContext(context)) {
                return context;
            }
        } catch (RuntimeException exception) {
            log.warn("Memory Graph 上下文构造失败，userId={}, error={}", sessionKey, rootMessage(exception));
        }
    }
    return conversationModeContext(conversationMode, conversationContext(sessionKey));
}
```

Update Function Calling request construction to use:

```java
conversationContext(sessionKey, text, conversationMode)
```

and avoid calling `ragContext(sessionKey, text)` twice by storing it in a local variable if needed.

- [ ] **Step 6: Run integration tests**

Run:

```powershell
mvn "-Dtest=WechatConversationServiceTests,FunctionCallingAgentLoopTests,WechatAgentMemoryContextBuilderTests" test
```

Expected: PASS.

- [ ] **Step 7: Commit**

```powershell
git add src/main/java/com/example/spring/wechat/conversation/WechatConversationService.java src/test/java/com/example/spring/wechat/conversation/WechatConversationServiceTests.java
git commit -m "feat(context): route wechat conversations through memory graph context"
```

---

## Task 9: Final Harness Verification

**Files:**
- No new source files.
- Check all files touched by Tasks 1-8.

- [ ] **Step 1: Run targeted context suite**

Run:

```powershell
mvn "-Dtest=WechatContextPropertiesTests,RuleBasedRelevanceFallbackTests,ModelConversationRelevanceClassifierTests,MySqlMemoryGraphRepositoryTests,MemoryGraphRetrieverTests,LongTermMemoryRetrieverTests,ContextBudgetManagerTests,ContextCompressorTests,WechatContextAssemblerTests,WechatContextOrchestratorTests,SlidingWindowSummaryServiceTests,ActiveExtractServiceTests,LongTermMemoryExtractorTests,WechatConversationServiceTests,WechatAgentMemoryContextBuilderTests,FunctionCallingAgentLoopTests" test
```

Expected: PASS with 0 failures.

- [ ] **Step 2: Run existing harness-focused regression suite**

Run:

```powershell
mvn "-Dtest=WechatConversationMemoryTests,MySqlWechatMemoryServiceTests,InMemoryWechatMemoryFallbackTests,WechatAgentMemoryContextBuilderTests,WechatToolRegistryTests,SkillToolMappingTests,SkillBackedToolMetadataTests,AgentLoopStateTests,FunctionCallingAgentLoopTests,AgentGoalServiceTests,AgentGoalTrackerTests,WechatConversationServiceTests,WechatMessageDispatcherTests,WechatBotServiceTests" test
```

Expected: PASS with 0 failures.

- [ ] **Step 3: Run diff hygiene**

Run:

```powershell
git diff --check
git status --short
```

Expected:

- `git diff --check` exits 0.
- `git status --short` shows no uncommitted files after the previous commits.

- [ ] **Step 4: Optionally run full suite**

Run:

```powershell
mvn test
```

Expected for current baseline: the suite may still show the known unrelated failures previously observed:

- `EncodingHealthTests.projectTextFilesDoNotContainReplacementCharactersOrCommonMojibakeMarkers`
- `CareTaskSchedulerTests.dueNotificationGoesOnlyToThePatientAndProvidesTextReplyActions`
- `CarePlanTimeParserTests.expandsExplicitHourlyRangeWithoutInventingTimesOutsideRange`
- `XhsConsoleWebConfigurationTests.consoleEntryContainsAuthorizationNavigationAndVersionedAssets`

If new failures appear, fix them before completing the branch.

- [ ] **Step 5: Final commit only if verification changes files**

If no files changed during verification, do not create a commit.

If test fixture or documentation updates were required:

```powershell
git add <changed-files>
git commit -m "test(context): verify memory graph context integration"
```

---

## Self-Review

### Spec coverage

- Strong/weak topic relevance: Task 2 and Task 6.
- Strong related recent 5 turns: Task 1 config and Task 6 orchestrator.
- Weak related recent 1 turn and historical topics: Task 1 config, Task 5 retriever, Task 6 orchestrator.
- Active extract: Task 5 retrieval, Task 6 assembly, Task 7 extraction service.
- Sliding window summary: Task 7.
- Long-term RAG memory: Task 5 retrieval and Task 7 asynchronous ingestion.
- 80% model input budget after reserves: Task 4.
- Compression priority: Task 4.
- System prompt and current user request not compressed: Task 4 preserves only context sections; Task 8 keeps system prompt and current user prompt in existing loop.
- Fallback to old builder: Task 8.
- Tests and verification: Task 9.

### Placeholder scan

This plan contains no unresolved markers, no incomplete code blocks, and no steps that ask for unspecified tests. Each task includes file paths, commands, expected results, and concrete implementation snippets.

### Type consistency

The plan consistently uses:

- `WechatContextProperties`
- `RelevanceLevel`
- `ConversationRelevanceDecision`
- `MemoryGraphNode`
- `MemoryGraphRepository`
- `ContextBuildRequest`
- `WechatContextPackage`
- `ContextSection`
- `ContextBudgetReport`
- `WechatContextOrchestrator`

The signatures introduced in earlier tasks are the same signatures used by later tasks.
