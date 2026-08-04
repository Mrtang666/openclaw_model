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
