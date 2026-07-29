package com.example.spring.wechat.conversation.tools;

import com.example.spring.wechat.bot.WechatReply;
import com.example.spring.xhs.analysis.XhsOpinionQueryService;
import com.example.spring.xhs.analysis.XhsOpinionView;
import com.example.spring.xhs.analysis.XhsSentiment;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class XhsOpinionSearchWechatToolTests {

    @Test
    void returnsRiskAndTraceableSource() {
        XhsOpinionQueryService queryService = mock(XhsOpinionQueryService.class);
        when(queryService.search("brand-a", "过敏", "NEGATIVE", 40, 5)).thenReturn(List.of(
                new XhsOpinionView("brand-a", "使用体验", "使用后脸部发红", XhsSentiment.NEGATIVE,
                        "CONSUMER_SAFETY", 80, "CRITICAL",
                        "https://www.xiaohongshu.com/explore/note-1", Instant.now(), Instant.now())));
        XhsOpinionSearchWechatTool tool = new XhsOpinionSearchWechatTool(queryService);

        WechatReply reply = tool.execute(new WechatToolRequest(
                "user-1", "查询过敏负面舆情",
                Map.of("project_key", "brand-a", "keyword", "过敏", "sentiment", "NEGATIVE",
                        "minimum_risk_score", "40", "limit", "5"),
                "", List.of(), List.of(), List.of(), List.of(), null, null));

        assertThat(reply.text()).contains("CRITICAL", "80分", "使用后脸部发红",
                "https://www.xiaohongshu.com/explore/note-1");
    }
}
