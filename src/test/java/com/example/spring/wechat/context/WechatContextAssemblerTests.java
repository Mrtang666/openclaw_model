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
                .startsWith("【context_policy / 上下文策略】")
                .contains("相关性：STRONG")
                .contains("recent_turns")
                .contains("active_extract");
    }
}
