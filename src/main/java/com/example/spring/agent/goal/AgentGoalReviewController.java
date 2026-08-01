package com.example.spring.agent.goal;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/agent-goals/review-actions")
public class AgentGoalReviewController {

    private final AgentGoalReviewService reviewService;

    public AgentGoalReviewController(AgentGoalReviewService reviewService) {
        this.reviewService = reviewService;
    }

    @GetMapping("/pending")
    public List<AgentGoalReviewAction> pendingActions(
            @RequestParam(name = "limit", defaultValue = "20") int limit) {
        return reviewService.pendingActions(limit);
    }

    @PostMapping("/{actionId}/applied")
    public ResponseEntity<Void> markApplied(@PathVariable long actionId) {
        reviewService.markApplied(actionId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{actionId}/dismissed")
    public ResponseEntity<Void> markDismissed(@PathVariable long actionId) {
        reviewService.markDismissed(actionId);
        return ResponseEntity.noContent().build();
    }
}
