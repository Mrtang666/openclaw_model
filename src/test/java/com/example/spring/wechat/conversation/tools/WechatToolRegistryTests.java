package com.example.spring.wechat.conversation.tools;

import com.example.spring.wechat.bot.WechatReply;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class WechatToolRegistryTests {

    @Test
    void registersWechatToolsByNameAndExposesDefinitionsForPlanner() {
        WechatToolRegistry registry = new WechatToolRegistry(List.of(new FakeWechatTool()));

        assertThat(registry.names()).containsExactly("fake_tool");
        assertThat(registry.definitions()).singleElement()
                .satisfies(definition -> {
                    assertThat(definition.name()).isEqualTo("fake_tool");
                    assertThat(definition.description()).contains("测试工具");
                    assertThat(definition.arguments()).containsExactly("value");
                });
        assertThat(registry.execute("fake_tool", sampleRequest("hello")).text()).isEqualTo("fake: hello");
    }

    private WechatToolRequest sampleRequest(String value) {
        return new WechatToolRequest(
                "user-1",
                "原始用户消息",
                Map.of("value", value),
                "最近对话为空",
                (userText, prompt) -> {
                },
                (userText, prompt) -> {
                });
    }

    private static final class FakeWechatTool implements WechatTool {

        @Override
        public String name() {
            return "fake_tool";
        }

        @Override
        public String description() {
            return "测试工具";
        }

        @Override
        public List<String> arguments() {
            return List.of("value");
        }

        @Override
        public WechatReply execute(WechatToolRequest request) {
            return WechatReply.text("fake: " + request.argument("value"));
        }
    }
}
