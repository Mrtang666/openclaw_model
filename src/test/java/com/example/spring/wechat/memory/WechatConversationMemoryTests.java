package com.example.spring.wechat.memory;

import org.junit.jupiter.api.Test;

import com.example.spring.wechat.memory.model.WechatConversationMemory;
import java.lang.reflect.Method;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

class WechatConversationMemoryTests {

    @Test
    void retainsRecentTurnsAndConversationState() throws Exception {
        Class<?> memoryType;
        try {
            memoryType = Class.forName(
                    "com.example.spring.wechat.memory.model.WechatConversationMemory");
        } catch (ClassNotFoundException exception) {
            fail("微信会话记忆模型尚未实现");
            return;
        }

        Object memory = memoryType.getMethod("empty", int.class).invoke(null, 2);
        memoryType.getMethod("record", String.class, String.class)
                .invoke(memory, "第一句", "第一条回复");
        memoryType.getMethod("record", String.class, String.class)
                .invoke(memory, "第二句", "第二条回复");
        memoryType.getMethod("record", String.class, String.class)
                .invoke(memory, "第三句", "第三条回复");

        List<?> turns = (List<?>) memoryType.getMethod("snapshot").invoke(memory);
        Method userText = turns.get(0).getClass().getMethod("userText");

        assertThat(turns).hasSize(2);
        assertThat(userText.invoke(turns.get(0))).isEqualTo("第二句");

        memoryType.getMethod("recordWeatherCity", String.class).invoke(memory, "杭州");
        memoryType.getMethod("recordPendingImagePrompt", String.class, String.class)
                .invoke(memory, "画一只猫", "赛博朋克橘猫");

        assertThat(memoryType.getMethod("lastWeatherCity").invoke(memory))
                .hasToString("Optional[杭州]");
        assertThat(memoryType.getMethod("lastPendingImagePrompt").invoke(memory))
                .hasToString("Optional[赛博朋克橘猫]");
    }
    @Test
    void retainsStructuredClarificationState() {
        WechatConversationMemory memory = WechatConversationMemory.empty(3);
        memory.recordPendingClarification(
                "make a file",
                "Which format?",
                "document_generation",
                List.of("format", "title"));

        WechatConversationMemory restored = WechatConversationMemory.empty(3);
        restored.applyState(memory.state());

        assertThat(restored.pendingClarificationToolName()).hasValue("document_generation");
        assertThat(restored.pendingClarificationMissingFields()).containsExactly("format", "title");
        assertThat(restored.pendingClarificationQuestion()).hasValue("Which format?");
    }
}
