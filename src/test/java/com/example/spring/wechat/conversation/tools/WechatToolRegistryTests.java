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
                    assertThat(definition.capability().summary()).contains("测试能力");
                    assertThat(definition.capability().boundaries()).contains("缺少 value 时需要追问");
                    assertThat(definition.capability().outputs()).contains("文本回复");
                    assertThat(definition.arguments()).containsExactly("value");
                    assertThat(definition.parameters()).singleElement()
                            .satisfies(parameter -> {
                                assertThat(parameter.name()).isEqualTo("value");
                                assertThat(parameter.type()).isEqualTo("string");
                                assertThat(parameter.required()).isTrue();
                                assertThat(parameter.description()).isEqualTo("测试输入值");
                                assertThat(parameter.allowedValues()).containsExactly("hello", "world");
                                assertThat(parameter.example()).isEqualTo("hello");
                            });
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
        public List<WechatToolParameter> parameters() {
            return List.of(new WechatToolParameter(
                    "value",
                    "string",
                    true,
                    "测试输入值",
                    List.of("hello", "world"),
                    "hello"));
        }

        @Override
        public WechatToolCapability capability() {
            return new WechatToolCapability(
                    "测试能力：根据 value 生成测试回复",
                    List.of("缺少 value 时需要追问"),
                    List.of("value"),
                    List.of("文本回复"));
        }

        @Override
        public WechatReply execute(WechatToolRequest request) {
            return WechatReply.text("fake: " + request.argument("value"));
        }
    }
}
