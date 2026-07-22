package com.example.spring.wechat.conversation.memory;

import com.example.spring.wechat.memory.model.WechatConversationMemory;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class WechatAgentMemoryContextBuilderTests {

    @Test
    void buildsContextWithSummaryRecentTurnsAndMediaMemory() {
        WechatConversationMemory memory = WechatConversationMemory.empty(10, "用户正在准备杭州出行计划。");
        memory.record("你好", "你好，我是你的 AI 助手。");
        memory.recordUserImage("这张图是什么", "图片里是一只橘猫坐在椅子上。");
        memory.recordFile("plan.pdf", "PDF", "文件内容是杭州两日游计划。");
        memory.recordWeatherCity("杭州");

        String context = new WechatAgentMemoryContextBuilder().build(memory);

        assertThat(context)
                .contains("conversation_summary")
                .contains("recent_turns")
                .contains("media_memory")
                .contains("tool_state")
                .contains("杭州")
                .contains("plan.pdf")
                .contains("橘猫");
    }

    @Test
    void returnsNoneWhenMemoryIsEmpty() {
        String context = new WechatAgentMemoryContextBuilder().build(WechatConversationMemory.empty(10));

        assertThat(context).isEqualTo("无");
    }

    @Test
    void buildsLayeredContextWithResourceAndClarificationState() {
        WechatConversationMemory memory = WechatConversationMemory.empty(10, "long memory");
        memory.record("hello", "hi");
        memory.recordPendingClarification(
                "make a report",
                "Which format do you want?",
                "document_generation",
                List.of("format", "title"));

        String context = new WechatAgentMemoryContextBuilder().build(memory, "image resources");

        assertThat(context)
                .contains("conversation_summary")
                .contains("recent_turns")
                .contains("resource_context")
                .contains("pending_clarification")
                .contains("document_generation")
                .contains("format")
                .contains("image resources");
    }
}
