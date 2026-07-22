package com.example.spring.tool.protocol.function;

import com.example.spring.wechat.conversation.tools.WechatToolDefinition;
import com.example.spring.wechat.conversation.tools.WechatToolCapability;
import com.example.spring.wechat.conversation.tools.WechatToolParameter;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class FunctionCallingToolSchemaConverterTests {

    @Test
    void convertsWechatToolDefinitionsToOpenAiCompatibleToolsSchema() {
        FunctionCallingToolSchemaConverter converter = new FunctionCallingToolSchemaConverter();

        List<Map<String, Object>> tools = converter.convert(List.of(new WechatToolDefinition(
                "weather",
                "查询城市天气",
                List.of(
                        WechatToolParameter.requiredString("city", "要查询天气的中国城市名", "杭州"),
                        WechatToolParameter.optionalEnum(
                                "unit",
                                "温度单位",
                                List.of("celsius", "fahrenheit"),
                                "celsius")))));

        assertThat(tools).singleElement()
                .satisfies(tool -> {
                    assertThat(tool.get("type")).isEqualTo("function");
                    Map<?, ?> function = (Map<?, ?>) tool.get("function");
                    assertThat(function.get("name")).isEqualTo("weather");
                    assertThat(function.get("description")).isEqualTo("查询城市天气");

                    Map<?, ?> parameters = (Map<?, ?>) function.get("parameters");
                    assertThat(parameters.get("type")).isEqualTo("object");
                    assertThat(parameters.get("additionalProperties")).isEqualTo(false);
                    assertThat(parameters.get("required")).isEqualTo(List.of("city"));

                    Map<?, ?> properties = (Map<?, ?>) parameters.get("properties");
                    Map<?, ?> city = (Map<?, ?>) properties.get("city");
                    assertThat(city.get("type")).isEqualTo("string");
                    assertThat(city.get("description")).isEqualTo("要查询天气的中国城市名");

                    Map<?, ?> unit = (Map<?, ?>) properties.get("unit");
                    assertThat(unit.get("enum")).isEqualTo(List.of("celsius", "fahrenheit"));
                });
    }

    @Test
    void includesToolCapabilityBoundaryInFunctionDescription() {
        FunctionCallingToolSchemaConverter converter = new FunctionCallingToolSchemaConverter();

        List<Map<String, Object>> tools = converter.convert(List.of(new WechatToolDefinition(
                "weather",
                "查询城市天气",
                List.of(WechatToolParameter.requiredString("city", "城市名", "杭州")),
                new WechatToolCapability(
                        "可以查询指定城市天气",
                        List.of("缺少城市名时必须追问"),
                        List.of("city"),
                        List.of("天气文本")))));

        assertThat(tools).singleElement()
                .satisfies(tool -> {
                    Map<?, ?> function = (Map<?, ?>) tool.get("function");
                    assertThat(function.get("description").toString())
                            .contains("查询城市天气")
                            .contains("可以查询指定城市天气")
                            .contains("缺少城市名时必须追问")
                            .contains("天气文本");
                });
    }
}
