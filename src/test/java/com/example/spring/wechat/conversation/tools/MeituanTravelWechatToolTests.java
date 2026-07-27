package com.example.spring.wechat.conversation.tools;

import com.example.spring.wechat.travel.client.MeituanTravelClient;
import com.example.spring.wechat.travel.client.MeituanTravelClientException;
import com.example.spring.wechat.travel.model.MeituanTravelResult;
import com.example.spring.wechat.travel.model.TravelQuery;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MeituanTravelWechatToolTests {

    private final MeituanTravelClient client = mock(MeituanTravelClient.class);
    private final MeituanTravelWechatTool tool = new MeituanTravelWechatTool(client);

    @Test
    void returnsOfficialCliContentWithoutRewriting() {
        String official = "## 上海三日游\n\n- 酒店：示例酒店\n- [查看详情](https://hotel.meituan.com/1)";
        when(client.query(any())).thenReturn(new MeituanTravelResult(official));

        var reply = tool.execute(request(Map.of(
                "query", "杭州出发的上海三日游",
                "origin_query", "帮我规划上海三日游",
                "city", "上海")));

        assertThat(reply.text()).isEqualTo(official);
        verify(client).query(new TravelQuery("杭州出发的上海三日游", "帮我规划上海三日游", "上海"));
    }

    @Test
    void mapsAuthenticationFailureToWechatSafeMessage() {
        when(client.query(any())).thenThrow(new MeituanTravelClientException(
                MeituanTravelClientException.Kind.AUTHENTICATION,
                "secret detail"));

        var reply = tool.execute(request(Map.of(
                "query", "北京酒店",
                "origin_query", "北京酒店")));

        assertThat(reply.text())
                .isEqualTo("美团酒旅服务鉴权失败，请检查访问凭证。")
                .doesNotContain("secret detail");
    }

    @Test
    void exposesOnlyTravelQueryCapabilities() {
        assertThat(tool.name()).isEqualTo("meituan_travel");
        assertThat(tool.parameters()).extracting(WechatToolParameter::name)
                .containsExactly("query", "origin_query", "city", "request_type");
        assertThat(tool.description()).contains("酒店", "机票", "火车票", "景点门票");
        assertThat(tool.capability().boundaries())
                .anyMatch(value -> value.contains("不创建真实订单"));
    }

    private WechatToolRequest request(Map<String, String> arguments) {
        return new WechatToolRequest(
                "clawbot:connection-1:user-1",
                arguments.getOrDefault("origin_query", ""),
                arguments,
                "",
                null,
                null);
    }
}
