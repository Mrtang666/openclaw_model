package com.example.spring.wechat.commerce.advice.model;

import java.util.List;

public record ShoppingAdviceResult(
        String title,
        String budgetSummary,
        List<String> priorities,
        List<String> recommendations,
        List<String> pitfalls,
        List<String> checklist,
        List<String> notices) {

    public ShoppingAdviceResult {
        title = title == null ? "" : title.strip();
        budgetSummary = budgetSummary == null ? "" : budgetSummary.strip();
        priorities = priorities == null ? List.of() : List.copyOf(priorities);
        recommendations = recommendations == null ? List.of() : List.copyOf(recommendations);
        pitfalls = pitfalls == null ? List.of() : List.copyOf(pitfalls);
        checklist = checklist == null ? List.of() : List.copyOf(checklist);
        notices = notices == null ? List.of() : List.copyOf(notices);
    }
}
