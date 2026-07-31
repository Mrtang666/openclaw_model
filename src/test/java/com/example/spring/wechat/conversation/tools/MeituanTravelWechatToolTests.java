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
        String official = "## Official Meituan result\n\n- Hotel: sample hotel\n- [Open](https://hotel.meituan.com/1)";
        when(client.query(any())).thenReturn(new MeituanTravelResult(official));

        var reply = tool.execute(request(Map.of(
                "query", "Shanghai three day trip from Hangzhou",
                "origin_query", "Plan a Shanghai three day trip",
                "city", "Shanghai")));

        assertThat(reply.text()).isEqualTo(official);
        verify(client).query(new TravelQuery(
                "Shanghai three day trip from Hangzhou",
                "Plan a Shanghai three day trip",
                "Shanghai"));
    }

    @Test
    void mapsAuthenticationFailureToWechatSafeMessage() {
        when(client.query(any())).thenThrow(new MeituanTravelClientException(
                MeituanTravelClientException.Kind.AUTHENTICATION,
                "secret detail"));

        var reply = tool.execute(request(Map.of(
                "query", "Beijing hotel",
                "origin_query", "Beijing hotel")));

        assertThat(reply.text()).isNotBlank().doesNotContain("secret detail");
    }

    @Test
    void exposesCompactMetadataBecauseWorkflowRulesLiveInSkill() {
        assertThat(tool.name()).isEqualTo("meituan_travel");
        assertThat(tool.parameters()).extracting(WechatToolParameter::name)
                .containsExactly("query", "origin_query", "city", "request_type");
        assertThat(tool.description().length()).isLessThanOrEqualTo(80);
        assertThat(tool.capability().summary()).isNotBlank();
        assertThat(tool.capability().boundaries()).isEmpty();
        assertThat(tool.capability().requiredInformation()).isEmpty();
        assertThat(tool.capability().outputs()).isEmpty();
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
