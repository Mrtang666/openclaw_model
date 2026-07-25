package com.example.spring.wechat.conversation.tools;

import com.example.spring.wechat.bot.WechatReply;
import com.example.spring.wechat.payment.service.WechatPayService;
import com.example.spring.wechat.taxi.model.*;
import com.example.spring.wechat.taxi.service.RideOrchestrationService;
import org.springframework.stereotype.Component;
import java.util.List;
import java.util.Locale;

@Component
public class TaxiWechatTool implements WechatTool {
    private final RideOrchestrationService rides; private final WechatPayService payments;
    public TaxiWechatTool(RideOrchestrationService rides, WechatPayService payments){this.rides=rides;this.payments=payments;}
    public String name(){return "taxi_service";}
    public String description(){return "滴滴打车服务。必须先解析并确认起终点，再询价；用户确认车型后才能下单。";}
    public List<String> arguments(){return List.of("operation","origin","destination","city","confirmation_id","quote_id","option_index","order_id","reason","phone","openid");}
    public List<WechatToolParameter> parameters(){return List.of(
            WechatToolParameter.requiredString("operation","prepare_locations、confirm_locations、confirm_order、open_didi_app、query_order、prepare_cancel、confirm_cancel、create_payment","prepare_locations"),
            WechatToolParameter.optionalString("origin","用户原始起点",""), WechatToolParameter.optionalString("destination","用户原始终点",""),
            WechatToolParameter.optionalString("city","起终点所在城市",""), WechatToolParameter.optionalString("confirmation_id","地点确认编号",""),
            WechatToolParameter.optionalString("quote_id","报价编号",""), WechatToolParameter.optionalString("option_index","车型编号","1"),
            WechatToolParameter.optionalString("order_id","订单编号",""), WechatToolParameter.optionalString("reason","取消原因","用户取消"),
            WechatToolParameter.optionalString("phone","叫车手机号",""), WechatToolParameter.optionalString("openid","微信支付 openid",""));}
    public WechatToolCapability capability(){return new WechatToolCapability("地点追问和确认、滴滴询价、下单、查单、取消及微信支付",
            List.of("地点不完整时先追问","地点解析成功后必须让用户确认","未确认车型不得创建订单","最终费用以滴滴完单结果为准"),
            List.of("operation","origin","destination","city","confirmation_id","quote_id","option_index","order_id"),List.of("地点确认信息","报价方案","订单状态"));}

    public WechatReply execute(WechatToolRequest request){try{return switch(request.argument("operation").toLowerCase(Locale.ROOT)){
        case "estimate","prepare_locations" -> prepareLocations(request); case "confirm_locations" -> confirmLocations(request);
        case "confirm_order" -> confirmOrder(request); case "open_didi_app" -> openDidiApp(request); case "query_order" -> WechatReply.text(formatOrder(rides.query(request.sessionKey(),request.argument("order_id"))));
        case "prepare_cancel" -> prepareCancel(request); case "cancel_order","confirm_cancel" -> confirmCancel(request);
        case "create_payment" -> createPayment(request); case "reservation" -> WechatReply.text("当前滴滴 MCP 未提供预约下单工具，暂不能创建预约订单。");
        default -> WechatReply.text("暂不支持该打车操作。");};}catch(RuntimeException e){return WechatReply.text("打车服务暂时无法完成："+(e.getMessage()==null?"请稍后重试":e.getMessage()));}}

    private WechatReply prepareLocations(WechatToolRequest r){RideLocationConfirmation c=rides.prepareLocationConfirmation(r.sessionKey(),r.argument("origin"),r.argument("destination"),r.argument("city"));return WechatReply.text("**打车路线确认**\n\n- **起点：** "+c.originName()+address(c.originAddress())+"\n- **终点：** "+c.destinationName()+address(c.destinationAddress())+"\n\n如果地点正确，请回复 **“确认地点”**；如果需要修改，请直接告诉我要修改的起点或终点。");}
    private WechatReply confirmLocations(WechatToolRequest r){if(!confirmed(r.userText()))return WechatReply.text("请明确回复 **“确认地点”**，确认后我再查询滴滴车型和价格。");RideQuote q=rides.estimateConfirmed(r.sessionKey(),r.argument("confirmation_id"));if(q.options().isEmpty())return WechatReply.text("地点已确认，但滴滴暂未返回可用车型，请稍后重试。");StringBuilder s=new StringBuilder("地点已确认！ ✅\n\n从 **").append(q.originName()).append("** 到 **").append(q.destinationName()).append("** 的打车方案如下：\n\n| 编号 | 车型 | 预估费用 |\n| --- | --- | --- |");for(int i=0;i<q.options().size();i++){RideQuoteOption o=q.options().get(i);s.append("\n| ").append(i+1).append(" | ").append(o.name()).append(" | ").append(price(o)).append(" |");}s.append("\n\n请选择你想要的车型：\n\n- **直连下单：** 回复“确认叫车 编号”，并提供与你滴滴 App 一致的手机号\n- **App 下单：** 回复“打开滴滴 编号”，在滴滴 App 内确认发单");return WechatReply.text(s.toString());}
    private WechatReply confirmOrder(WechatToolRequest r){if(!confirmed(r.userText())&&!r.userText().contains("叫车"))return WechatReply.text("请明确回复“确认叫车 1”，我才会创建滴滴订单。");RideOrder o=rides.confirm(r.sessionKey(),r.argument("quote_id"),Integer.parseInt(r.argument("option_index")),r.argument("phone"));return WechatReply.text("已提交叫车请求，正在为你寻找合适车辆。订单号："+o.orderId());}
    private WechatReply openDidiApp(WechatToolRequest r){if(r.userText()==null||(!r.userText().contains("滴滴")&&!r.userText().contains("App")&&!r.userText().contains("小程序")))return WechatReply.text("请明确回复 **“打开滴滴 车型编号”**，系统再生成微信小程序链接。");RideAppLink link=rides.generateRideAppLink(r.sessionKey(),r.argument("quote_id"),Integer.parseInt(r.argument("option_index")));String target=!link.miniProgramLink().isBlank()?link.miniProgramLink():!link.appLink().isBlank()?link.appLink():link.browserLink();String channel=!link.miniProgramLink().isBlank()?"微信小程序":"滴滴 App";return WechatReply.text("**滴滴"+channel+"下单**\n\n已带入确认过的起点、终点和车型，请点击下面的链接，在滴滴内核对后发单：\n\n[打开滴滴并确认行程]("+target+")\n\n如果微信提示需要授权，请使用当前手机号登录滴滴。请勿同时使用直连下单，避免重复订单。");}
    private WechatReply createPayment(WechatToolRequest r){RideOrder o=rides.query(r.sessionKey(),r.argument("order_id"));if(o.finalFare()==null)return WechatReply.text("订单尚未返回最终车费，暂不能发起微信支付。");var p=payments.create(o,r.argument("openid"));return WechatReply.text("微信支付订单已创建："+p.paymentId());}
    private WechatReply prepareCancel(WechatToolRequest r){RideOrder o=rides.prepareCancellation(r.sessionKey(),r.argument("order_id"));String warning=o.status()==RideOrderStatus.DRIVER_ASSIGNED||o.status()==RideOrderStatus.DRIVER_ARRIVING?"司机已经接单，取消可能产生费用，具体以滴滴返回结果为准。":"当前仍在寻找司机，通常可以取消。";return WechatReply.text("订单 "+o.orderId()+" 当前状态："+o.status()+"。\n"+warning+"\n如果仍要取消，请明确回复“确认取消订单”。");}
    private WechatReply confirmCancel(WechatToolRequest r){if(r.userText()==null||!r.userText().contains("确认取消"))return prepareCancel(r);RideOrder o=rides.cancel(r.sessionKey(),r.argument("order_id"),r.argument("reason"));return WechatReply.text("订单已取消："+o.orderId()+"。如滴滴产生取消费用，请以滴滴订单结算结果为准。");}
    private boolean confirmed(String text){return text!=null&&(text.contains("确认")||text.contains("正确")||text.contains("是的")||text.contains("没错"));}
    private String address(String v){return v==null||v.isBlank()?"":"（"+v+"）";}
    private String price(RideQuoteOption o){if(o.minPrice()==null)return "以滴滴显示为准";if(o.maxPrice()!=null&&o.maxPrice().compareTo(o.minPrice())!=0)return "约 "+o.minPrice()+"~"+o.maxPrice()+" 元";return "约 "+o.minPrice()+" 元";}
    private String formatOrder(RideOrder o){StringBuilder s=new StringBuilder("订单状态：").append(o.status());if(!o.driverName().isBlank())s.append("\n司机：").append(o.driverName());if(!o.vehiclePlate().isBlank())s.append("\n车牌：").append(o.vehiclePlate());if(o.etaSeconds()!=null)s.append("\n预计到达：").append(Math.max(1,o.etaSeconds()/60)).append(" 分钟");if(o.finalFare()!=null)s.append("\n最终费用：").append(o.finalFare()).append(" 元");return s.toString();}
}
