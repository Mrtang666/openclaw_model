package com.example.spring.agent.goal;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AgentGoalReviewController.class)
class AgentGoalReviewControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AgentGoalReviewService reviewService;

    @Test
    void pendingActionsReturnsReviewQueue() throws Exception {
        when(reviewService.pendingActions(5)).thenReturn(List.of(new AgentGoalReviewAction(
                9L,
                42L,
                AgentGoalReviewActionType.IMPROVE_PROMPT,
                AgentGoalReviewActionStatus.PENDING,
                "no reply",
                Instant.parse("2026-07-30T04:00:00Z"),
                Instant.parse("2026-07-30T04:00:00Z"))));

        mockMvc.perform(get("/api/agent-goals/review-actions/pending").param("limit", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(9))
                .andExpect(jsonPath("$[0].goalId").value(42))
                .andExpect(jsonPath("$[0].actionType").value("IMPROVE_PROMPT"))
                .andExpect(jsonPath("$[0].status").value("PENDING"))
                .andExpect(jsonPath("$[0].reason").value("no reply"));

        verify(reviewService).pendingActions(5);
    }

    @Test
    void marksActionApplied() throws Exception {
        mockMvc.perform(post("/api/agent-goals/review-actions/9/applied"))
                .andExpect(status().isNoContent());

        verify(reviewService).markApplied(9L);
    }

    @Test
    void marksActionDismissed() throws Exception {
        mockMvc.perform(post("/api/agent-goals/review-actions/9/dismissed"))
                .andExpect(status().isNoContent());

        verify(reviewService).markDismissed(9L);
    }
}
