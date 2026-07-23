package com.example.spring.wechat.conversation.tools;

import com.example.spring.tool.protocol.function.FunctionCallingToolCall;
import com.example.spring.tool.protocol.function.FunctionCallingToolSchemaConverter;
import com.example.spring.tool.protocol.validation.ToolCallValidationResult;
import com.example.spring.tool.protocol.validation.ToolCallValidator;
import com.example.spring.wechat.bot.WechatReply;
import com.example.spring.wechat.map.model.MapLink;
import com.example.spring.wechat.map.model.MapOperation;
import com.example.spring.wechat.map.model.MapMultiRouteResult;
import com.example.spring.wechat.map.model.MapOrderPolicy;
import com.example.spring.wechat.map.model.MapPlace;
import com.example.spring.wechat.map.model.MapResult;
import com.example.spring.wechat.map.model.MapRouteLeg;
import com.example.spring.wechat.map.model.MapRouteOption;
import com.example.spring.wechat.map.model.MapTransportMode;
import com.example.spring.wechat.image.generation.model.ImageGenerationResult;
import com.example.spring.wechat.map.service.MapService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MapWechatToolTests {

    @Test
    void validatorRejectsMissingOperationAndIllegalEnum() {
        MapWechatTool tool = new MapWechatTool(mock(MapService.class));
        WechatToolDefinition definition = definition(tool);
        ToolCallValidator validator = new ToolCallValidator();

        ToolCallValidationResult missing = validator.validate(
                new FunctionCallingToolCall("call-1", "map_search", Map.of()),
                List.of(definition));
        ToolCallValidationResult illegal = validator.validate(
                new FunctionCallingToolCall("call-2", "map_search", Map.of("operation", "book_ticket")),
                List.of(definition));

        assertThat(missing.valid()).isFalse();
        assertThat(missing.message()).contains("operation");
        assertThat(illegal.valid()).isFalse();
        assertThat(illegal.message()).contains("允许值");
    }

    @Test
    void executesRouteQueryAndReturnsWechatReply() {
        MapService service = mock(MapService.class);
        MapWechatTool tool = new MapWechatTool(service);
        MapPlace origin = place("杭州东站", "杭州市上城区全福桥路2号", "120.212", "30.290");
        MapPlace destination = place("西湖断桥", "杭州市西湖区北山街", "120.149", "30.258");
        when(service.planRoute("杭州东站", "西湖断桥", "杭州", MapTransportMode.ALL))
                .thenReturn(new MapResult(
                        MapOperation.ROUTE,
                        "杭州东站 → 西湖断桥",
                        List.of(origin, destination),
                        List.of(
                                new MapRouteOption(
                                        MapTransportMode.DRIVING,
                                        12500,
                                        1800,
                                        "",
                                        "0",
                                        null,
                                        List.of(),
                                        "速度优先"),
                                new MapRouteOption(
                                        MapTransportMode.TRANSIT,
                                        14100,
                                        3000,
                                        "4",
                                        "",
                                        800,
                                        List.of("地铁1号线", "地铁2号线"),
                                        "地铁1号线 → 地铁2号线")),
                        List.of(new MapLink("打开高德公交路线", "https://uri.amap.com/navigation?...")),
                        List.of("路线时间是估算值。")));

        WechatReply reply = tool.execute(request(Map.of(
                "operation", "route",
                "origin", "杭州东站",
                "destination", "西湖断桥",
                "city", "杭州",
                "transport_mode", "all")));

        assertThat(reply).isInstanceOf(WechatReply.class);
        assertThat(reply.text())
                .contains("杭州东站 → 西湖断桥")
                .contains("驾车", "30 分钟", "12.5 公里")
                .contains("公共交通共返回 1 种方案")
                .contains("地铁1号线", "打开高德公交路线");
        verify(service).planRoute("杭州东站", "西湖断桥", "杭州", MapTransportMode.ALL);
    }

    @Test
    void asksForConditionalRouteArgumentsInsteadOfGuessing() {
        MapWechatTool tool = new MapWechatTool(mock(MapService.class));

        WechatReply reply = tool.execute(request(Map.of(
                "operation", "route",
                "origin", "杭州东站")));

        assertThat(reply.text()).contains("同时提供起点和终点");
    }

    @Test
    void executesMultiRouteAndReturnsTextBeforeMapImage() {
        MapService service = mock(MapService.class);
        MapWechatTool tool = new MapWechatTool(service);
        MapPlace station = place("杭州东站", "杭州市上城区全福桥路2号", "120.212", "30.290");
        MapPlace westLake = place("西湖断桥", "杭州市西湖区北山街", "120.149", "30.258");
        MapPlace temple = place("灵隐寺", "杭州市西湖区灵隐路", "120.102", "30.240");
        MapRouteLeg firstLeg = new MapRouteLeg(station, westLake, new MapRouteOption(
                MapTransportMode.DRIVING, 12500, 1800, "", "0", null, List.of(), "速度优先",
                List.of("经天城路", "进入环城北路"), List.of("120.212,30.290", "120.149,30.258")));
        MapRouteLeg secondLeg = new MapRouteLeg(westLake, temple, new MapRouteOption(
                MapTransportMode.DRIVING, 6800, 1440, "", "0", null, List.of(), "常规路线",
                List.of("经北山街"), List.of("120.149,30.258", "120.102,30.240")));
        ImageGenerationResult image = new ImageGenerationResult(
                "route-map", "", new byte[]{1, 2, 3}, "route-map.png", "image/png", 800, 700);
        when(service.planMultiRoute(
                List.of("杭州东站", "西湖断桥", "灵隐寺"),
                "杭州",
                MapTransportMode.DRIVING,
                MapOrderPolicy.OPTIMIZE,
                true,
                false,
                true))
                .thenReturn(new MapMultiRouteResult(
                        "杭州东站 → 西湖断桥 → 灵隐寺",
                        List.of(station, westLake, temple),
                        List.of(firstLeg, secondLeg),
                        19300,
                        3240,
                        "",
                        MapTransportMode.DRIVING,
                        MapOrderPolicy.OPTIMIZE,
                        image,
                        List.of("路线时间是估算值。")));

        WechatReply reply = tool.execute(request(Map.of(
                "operation", "multi_route",
                "locations", "[\"杭州东站\",\"西湖断桥\",\"灵隐寺\"]",
                "city", "杭州",
                "transport_mode", "driving",
                "order_policy", "optimize")));

        assertThat(reply.parts()).hasSize(2);
        assertThat(reply.parts().get(0).text())
                .contains("推荐路线：杭州东站 → 西湖断桥 → 灵隐寺")
                .contains("19.3 公里", "54 分钟", "主要经过");
        assertThat(reply.parts().get(1).hasImage()).isTrue();
        verify(service).planMultiRoute(
                List.of("杭州东站", "西湖断桥", "灵隐寺"),
                "杭州",
                MapTransportMode.DRIVING,
                MapOrderPolicy.OPTIMIZE,
                true,
                false,
                true);
    }

    @Test
    void functionCallingSchemaContainsMapEnumsAndCapabilityBoundaries() {
        MapWechatTool tool = new MapWechatTool(mock(MapService.class));
        FunctionCallingToolSchemaConverter converter = new FunctionCallingToolSchemaConverter();

        List<Map<String, Object>> schemas = converter.convert(List.of(definition(tool)));

        assertThat(schemas).singleElement().satisfies(schema -> {
            Map<?, ?> function = (Map<?, ?>) schema.get("function");
            assertThat(function.get("name")).isEqualTo("map_search");
            assertThat(function.get("description").toString())
                    .contains("实时有票")
                    .contains("缺少 operation")
                    .contains("origin 和 destination");
            Map<?, ?> parameters = (Map<?, ?>) function.get("parameters");
            assertThat(parameters.get("required")).isEqualTo(List.of("operation"));
            Map<?, ?> properties = (Map<?, ?>) parameters.get("properties");
            Map<?, ?> operation = (Map<?, ?>) properties.get("operation");
            assertThat(operation.get("enum")).isEqualTo(
                    List.of("place_search", "place_detail", "route", "multi_route", "nearby_search"));
            Map<?, ?> locations = (Map<?, ?>) properties.get("locations");
            assertThat(locations.get("type")).isEqualTo("array");
            assertThat(locations.get("items")).isEqualTo(Map.of("type", "string"));
            Map<?, ?> category = (Map<?, ?>) properties.get("category");
            assertThat(category.get("enum")).isEqualTo(List.of("all", "food", "attraction", "shopping"));
        });
    }

    private static WechatToolDefinition definition(MapWechatTool tool) {
        return new WechatToolDefinition(tool.name(), tool.description(), tool.parameters(), tool.capability());
    }

    private static WechatToolRequest request(Map<String, String> arguments) {
        return new WechatToolRequest("user-1", "", arguments, "", null, null);
    }

    private static MapPlace place(String name, String address, String longitude, String latitude) {
        return new MapPlace(
                "id-" + name,
                name,
                "交通设施服务",
                address,
                longitude + "," + latitude,
                "浙江省",
                "杭州市",
                "",
                "330100",
                "",
                null,
                "",
                "",
                "",
                List.of());
    }
}
