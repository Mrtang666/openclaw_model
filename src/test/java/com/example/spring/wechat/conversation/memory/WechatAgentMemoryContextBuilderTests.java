package com.example.spring.wechat.conversation.memory;

import com.example.spring.wechat.memory.model.WechatConversationMemory;
import org.junit.jupiter.api.Test;

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
                .contains("滚动摘要")
                .contains("用户正在准备杭州出行计划")
                .contains("最近对话")
                .contains("用户：你好")
                .contains("助手：你好，我是你的 AI 助手。")
                .contains("媒体记忆")
                .contains("橘猫坐在椅子上")
                .contains("plan.pdf")
                .contains("杭州两日游计划")
                .contains("工具状态")
                .contains("最近查询天气城市：杭州");
    }

    @Test
    void returnsNoneWhenMemoryIsEmpty() {
        String context = new WechatAgentMemoryContextBuilder().build(WechatConversationMemory.empty(10));

        assertThat(context).isEqualTo("无");
    }
}
