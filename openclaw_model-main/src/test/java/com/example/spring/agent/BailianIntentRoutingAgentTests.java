package com.example.spring.agent;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.spring.bailian.BailianChatService;
import com.example.spring.bailian.BailianProperties;
import com.example.spring.document.DocumentAsset;
import com.example.spring.document.DocumentOutputFormat;
import com.example.spring.document.DocumentTaskIntent;
import com.example.spring.document.DocumentTaskSource;
import com.example.spring.memory.MemoryMessage;
import java.util.List;
import org.junit.jupiter.api.Test;

class BailianIntentRoutingAgentTests {
    @Test
    void routesCreativePdfRequestWithoutUsingRecentDocument() throws Exception {
        BailianIntentRoutingAgent router = new BailianIntentRoutingAgent(
            new FixedChatService("""
                ```json
                {
                  "agents":["DOCUMENT"],
                  "documentTask":{
                    "intent":"CREATE",
                    "source":"NONE",
                    "output":"PDF",
                    "task":"创作一个完整的小故事",
                    "title":"月光下的约定"
                  }
                }
                ```
                """));
        DocumentAsset recent = new DocumentAsset(
            "id", "赛前须知.docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            new byte[] {1}, "比赛规则", "赛前须知");
        AgentRequest request = new AgentRequest(
            "user", 1L, "生成一个小故事以pdf格式输出", List.of(), 0,
            List.of(), List.of(), List.of(), 0, List.of(recent));

        IntentRoutingDecision decision = router.route(request);

        assertThat(decision.plan().steps()).containsExactly(AgentType.DOCUMENT);
        assertThat(decision.documentTaskPlan().intent()).isEqualTo(DocumentTaskIntent.CREATE);
        assertThat(decision.documentTaskPlan().source()).isEqualTo(DocumentTaskSource.NONE);
        assertThat(decision.documentTaskPlan().output()).isEqualTo(DocumentOutputFormat.PDF);
    }

    @Test
    void extractsJsonFromCodeFence() {
        assertThat(BailianIntentRoutingAgent.extractJson(
            "```json\n{\"agents\":[\"CHAT\"]}\n```"))
            .isEqualTo("{\"agents\":[\"CHAT\"]}");
    }

    private static final class FixedChatService extends BailianChatService {
        private final String reply;

        private FixedChatService(String reply) {
            super(properties());
            this.reply = reply;
        }

        @Override
        public String chat(String userId, String userText, List<MemoryMessage> history) {
            return reply;
        }

        private static BailianProperties properties() {
            BailianProperties properties = new BailianProperties();
            properties.setApiKey("test-key");
            return properties;
        }
    }
}
