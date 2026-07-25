package com.example.spring.wechat.conversation.tools;

import com.example.spring.wechat.payment.service.WechatPayService;
import com.example.spring.wechat.taxi.model.*;
import com.example.spring.wechat.taxi.service.RideOrchestrationService;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class TaxiWechatToolTests {
    private final RideOrchestrationService rides=mock(RideOrchestrationService.class);
    private final TaxiWechatTool tool=new TaxiWechatTool(rides,mock(WechatPayService.class));

    @Test
    void quoteReplyKeepsMarkdownTableAndHidesInternalIds(){
        RideQuote quote=new RideQuote("internal-quote-id","s","阿里巴巴高桥云港园区","120.1,30.1","杭州西湖风景名胜区","120.2,30.2","trace",
                List.of(new RideQuoteOption("1","191","惊喜特价",new BigDecimal("30"),new BigDecimal("30"),null,"{}")),Instant.now().plusSeconds(300));
        when(rides.estimateConfirmed("s","")).thenReturn(quote);
        var reply=tool.execute(request("确认地点",Map.of("operation","confirm_locations")));
        assertThat(reply.text()).contains("| 编号 | 车型 | 预估费用 |","**直连下单：**","**App 下单：**")
                .doesNotContain("internal-quote-id","trace");
    }

    @Test
    void appOrderUsesCoordinatePrefilledAppLinkInsteadOfGenericBrowserLink(){
        String app="https://v.didi.cn/ride?slat=30.1&slng=120.1&dlat=30.2&dlng=120.2";
        String mini="https://v.didi.cn/p/ride?fromlat=30.1&fromlng=120.1&tolat=30.2&tolng=120.2";
        when(rides.generateRideAppLink("s","",1)).thenReturn(new RideAppLink(app,mini,"https://v.didi.cn/home"));
        var reply=tool.execute(request("打开滴滴 1",Map.of("operation","open_didi_app","option_index","1")));
        assertThat(reply.text()).contains(mini,"微信小程序").doesNotContain(app,"https://v.didi.cn/home");
    }

    private WechatToolRequest request(String text,Map<String,String> args){return new WechatToolRequest("s",text,args,"",null,null);}
}
