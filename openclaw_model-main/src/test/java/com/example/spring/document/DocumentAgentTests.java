package com.example.spring.document;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.spring.agent.AgentRequest;
import com.example.spring.agent.AgentResponse;
import com.example.spring.bailian.BailianChatService;
import com.example.spring.bailian.BailianProperties;
import com.example.spring.memory.ConversationMemoryService;
import com.example.spring.memory.MemoryMessage;
import com.example.spring.memory.MemoryProperties;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DocumentAgentTests {
    @TempDir
    Path tempDirectory;
    private static final DocumentAsset HISTORY_DOCUMENT = new DocumentAsset(
        "id", "source.docx",
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        new byte[] {1}, "原始文件内容", "原始文件");

    @Test
    void explicitReferenceToPreviousReplyBeatsActiveHistoricalDocument() {
        assertThat(DocumentAgent.determineSourceKind(
            List.of(), List.of(HISTORY_DOCUMENT), "把上面的内容输出一个pdf文件"))
            .isEqualTo(DocumentAgent.SourceKind.PRIOR_ASSISTANT_REPLY);
        assertThat(DocumentAgent.determineSourceKind(
            List.of(), List.of(HISTORY_DOCUMENT), "将上述内容保存为Word"))
            .isEqualTo(DocumentAgent.SourceKind.PRIOR_ASSISTANT_REPLY);
        assertThat(DocumentAgent.determineSourceKind(
            List.of(), List.of(HISTORY_DOCUMENT), "把刚才的重点导出PDF"))
            .isEqualTo(DocumentAgent.SourceKind.PRIOR_ASSISTANT_REPLY);
        assertThat(DocumentAgent.determineSourceKind(
            List.of(), List.of(HISTORY_DOCUMENT), "再输出一个Word"))
            .isEqualTo(DocumentAgent.SourceKind.PRIOR_ASSISTANT_REPLY);
    }

    @Test
    void pureExportDoesNotCallTheModelAgain() {
        assertThat(DocumentAgent.isPureExportInstruction("把上面的内容输出一个pdf文件"))
            .isTrue();
        assertThat(DocumentAgent.isPureExportInstruction("总结上面的内容并输出PDF"))
            .isFalse();
        assertThat(DocumentAgent.isPureExportInstruction("翻译成英文后保存为Word"))
            .isFalse();
    }

    @Test
    void derivesFileNameFromStructuredPreviousReply() {
        String source = "根据文档提取重点如下：\n- 产品名称：魔域文化超级傲龙AI 19周年纪念版\n- 核心升级：智能球轴";

        assertThat(DocumentAgent.deriveSourceName(source, "对话内容"))
            .isEqualTo("魔域文化超级傲龙AI 19周年纪念版");
    }

    @Test
    void createsNewStoryPdfWithoutReadingUnrelatedRecentDocument() throws Exception {
        MemoryProperties memoryProperties = new MemoryProperties();
        memoryProperties.setDataDirectory(tempDirectory);
        ConversationMemoryService memoryService = new ConversationMemoryService(memoryProperties);
        memoryService.afterPropertiesSet();
        DocumentProperties documentProperties = new DocumentProperties();
        documentProperties.setDataDirectory(tempDirectory);
        Path font = Path.of("C:/Windows/Fonts/simhei.ttf");
        if (Files.isRegularFile(font)) documentProperties.setPdfFontPath(font);
        DocumentTaskPlanner unusedPlanner = (request, instruction) -> {
            throw new AssertionError("顶层路由已经提供计划，不应再次规划");
        };
        DocumentAgent agent = new DocumentAgent(
            new StoryChatService(),
            unusedPlanner,
            new DocumentTextChunker(documentProperties),
            new DocumentGenerationService(documentProperties),
            memoryService);
        AgentRequest request = new AgentRequest(
            "user", 9L, "生成一个小故事以pdf格式输出", List.of(), 0,
            List.of(), List.of(), List.of(), 0, List.of(HISTORY_DOCUMENT))
            .withDocumentTaskPlan(new DocumentTaskPlan(
                DocumentTaskIntent.CREATE,
                DocumentTaskSource.NONE,
                DocumentOutputFormat.PDF,
                "创作一个温暖、完整的小故事",
                "月光下的约定"));

        AgentResponse response = agent.execute(request);

        assertThat(response.files()).singleElement().satisfies(file -> {
            assertThat(file.fileName()).isEqualTo("月光下的约定.pdf");
            try (PDDocument pdf = PDDocument.load(new ByteArrayInputStream(file.data()))) {
                String text = new PDFTextStripper().getText(pdf);
                assertThat(text).contains("月光下的约定", "小女孩在月光下帮助迷路的老人")
                    .doesNotContain("赛前须知", "比赛规则");
            } catch (Exception exception) {
                throw new AssertionError(exception);
            }
        });
    }

    @Test
    void fallbackAlsoRecognizesCreativeFileRequestSemantically() {
        AgentRequest request = new AgentRequest(
            "user", 10L, "生成一个小故事以pdf格式输出", List.of(), 0,
            List.of(), List.of(), List.of(), 0, List.of(HISTORY_DOCUMENT));

        DocumentTaskPlan plan = DocumentAgent.fallbackPlan(request, request.text());

        assertThat(plan.intent()).isEqualTo(DocumentTaskIntent.CREATE);
        assertThat(plan.source()).isEqualTo(DocumentTaskSource.NONE);
        assertThat(plan.output()).isEqualTo(DocumentOutputFormat.PDF);
    }

    private static final class StoryChatService extends BailianChatService {
        private StoryChatService() {
            super(properties());
        }

        @Override
        public String chat(String userId, String userText, List<MemoryMessage> history) {
            return "# 月光下的约定\n小女孩在月光下帮助迷路的老人，最后平安回到了村庄。";
        }

        private static BailianProperties properties() {
            BailianProperties properties = new BailianProperties();
            properties.setApiKey("test-key");
            return properties;
        }
    }
}
